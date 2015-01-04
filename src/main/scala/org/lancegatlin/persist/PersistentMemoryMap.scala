package org.lancegatlin.persist

import java.util.concurrent.ConcurrentSkipListMap

import org.joda.time.Instant
import s_mach.concurrent._
import s_mach.datadiff._

import scala.collection.mutable
import scala.concurrent.{ExecutionContext, Future}
import scala.util.{Failure, Success, Try}

object PersistentMemoryMap {
  import PersistentMap._
  def apply[ID,A,P](kv:(ID,A)*)(implicit
    ec: ExecutionContext,
    dataDiff:DataDiff[A,P],
    metadata:Metadata
    ) : PersistentMemoryMap[ID,A,P] = {
    val active = kv.map { case (id,value) =>
      (id,Record(
        value = value,
        version = 1
      ))
    }.toMap
    new PersistentMemoryMap[ID,A,P](
      baseState = BaseState(
        active = active,
        inactive = Map.empty
      ),
      zomBaseCommit = Nil,
      baseMetadata = metadata
    )
  }

}

class PersistentMemoryMap[ID,A,P](
  baseState: PersistentMap.BaseState[ID,A] = PersistentMap.BaseState.empty,
  zomBaseCommit: List[(Commit[ID,A,P],Metadata)],
  baseMetadata: Metadata
)(implicit
  val executionContext: ExecutionContext,
  val dataDiff:DataDiff[A,P]
) extends
  PersistentMap[ID,A,P] {
  import PersistentMemoryMap._
  import PersistentMap._

  val whenToOldState = {
    val m = new ConcurrentSkipListMap[Long,OldStateEx]()
    val lastState = {
      import baseState._
      BaseOldState(
        active = active,
        inactive = inactive,
        metadata = baseMetadata
      )
    }
    m.put(baseMetadata.when.getMillis, lastState)
    _appendCommits(m,zomBaseCommit,lastState)
    m
  }

  def _appendCommits(
    m:ConcurrentSkipListMap[Long,OldStateEx],
    zomCommit: List[(Commit[ID,A,P],Metadata)],
    _lastState: OldStateEx) : Unit = {
    var lastState = _lastState
    zomCommit.foreach { case (commit,metadata) =>
      val nextState = LazyOldState(lastState,commit,metadata)
      m.put(metadata.when.getMillis,nextState)
      lastState = nextState
    }
  }

  def print : String = {
    import scala.collection.JavaConverters._
    val sb = new StringBuilder
    sb.append("whenToOldState\n")
    whenToOldState.descendingMap().entrySet().asScala.foreach { entry =>
      sb.append(s"${new Instant(entry.getKey)} => ${entry.getValue}\n")
    }
    sb.result()
  }


  override def base: Future[(BaseState[ID,A], Metadata)] = {
    (baseState,baseMetadata).future
  }

  override def zomCommit: Future[List[(Commit[ID,A,P], Metadata)]] = {
    import scala.collection.JavaConverters._
    whenToOldState
      .descendingMap()
      .entrySet.asScala
      .iterator
      .flatMap { entry =>
        entry.getValue match {
          case l:LazyOldState => Some((l.commit,l.metadata))
          case _ => None
        }
      }
      .toList

  }.future

  trait OldStateEx extends OldState {
    def metadata: Metadata
    def active: Map[ID,Record[A]]
    def inactive: Map[ID,Record[A]]

    def when = metadata.when

    override def find(id: ID): Future[Option[A]] =
      active.get(id).map(_.value).future

    override def count: Future[Count] =
      Count(
        activeRecordCount = active.size,
        inactiveRecordCount = inactive.size
      ).future

    override def findActiveIds: Future[Set[ID]] =
      active.keys.toSet.future

    override def findInactiveIds: Future[Set[ID]] =
      inactive.keys.toSet.future

    override def toMap: Future[Map[ID, A]] = {
      active.mapValues(_.value).future
    }

    def _findRecord(id: ID) : Option[Record[A]] =
      active.get(id) orElse inactive.get(id)

    override def checkout(filter: ID => Boolean): Future[PersistentMap[ID, A, P]] = {
      Future.successful {
        new PersistentMemoryMap[ID,A,P](
          baseState = BaseState(
            active = active.filter(t => filter(t._1)),
            inactive = inactive.filter(t => filter(t._1))
          ),
          zomBaseCommit = Nil,
          baseMetadata = metadata
        )
      }
    }
  }

  case class LazyOldState(
    prev: OldStateEx,
    commit: Commit[ID,A,P],
    metadata: Metadata
  ) extends OldStateEx {
    lazy val active: Map[ID,Record[A]] = {
      val newActive = mutable.Map[ID,Record[A]](prev.active.toSeq:_*)
      commit.put.foreach { case (id,value) =>
        newActive.put(id,Record(
          value = value,
          version = 1
        ))
      }
      commit.replace.foreach { case (id,patch) =>
        val record = newActive(id)
        val newValue = record.value applyPatch patch
        newActive.put(id, Record(
          value = newValue,
          version = record.version + 1
        ))
      }
      commit.deactivate.foreach(newActive.remove)
      commit.reactivate.foreach { id =>
        val record = prev.inactive(id)
        newActive.put(id, record)
      }
      newActive.toMap
    }
    lazy val inactive: Map[ID,Record[A]] = {
      val newInactive = mutable.Map[ID,Record[A]](prev.inactive.toSeq:_*)
      commit.reactivate.foreach(newInactive.remove)
      commit.deactivate.foreach { id =>
        val record = prev.inactive(id)
        newInactive.put(id, record)
      }
      newInactive.toMap
    }
  }

  case class BaseOldState(
    active: Map[ID,Record[A]],
    inactive: Map[ID,Record[A]],
    metadata: Metadata
  ) extends OldStateEx

  override def old(when: Instant) = {
    val k = whenToOldState.floorKey(when.getMillis)
    whenToOldState.get(k) match {
      case null => NoOldState
      case oldState => oldState
    }
  }

  object NowStateEx extends NowState {
    def setState[X](
      f: OldStateEx => (Commit[ID,A,P],Metadata,X)
    ) : Future[X] = {
      def loop() : X = {
        val lastEntry = whenToOldState.lastEntry
        val nextWhen = Instant.now()
        val currentState = lastEntry.getValue
        val (commit, metadata, x) = f(currentState)
        val updatedMetadata = metadata.copy(when = nextWhen)
        if(commit.isNoChange) {
          x
        } else {
          val newState = LazyOldState(currentState,commit,updatedMetadata)
          def innerLoop(lastEntry: java.util.Map.Entry[Long,OldStateEx]) : X = {
            // Note: need synchronized here to ensure no new states are inserted
            // during check-execute below -- lock time has been minimized
            val isSuccess =
              whenToOldState.synchronized {
                if (lastEntry.getKey == whenToOldState.lastEntry.getKey) {
                  whenToOldState.put(nextWhen.getMillis, newState)
                  true
                } else {
                  false
                }
              }
            if(isSuccess) {
              x
            } else {
              // State during computation
              val updatedLastEntry = whenToOldState.lastEntry
              val updatedState = updatedLastEntry.getValue
              if(canCommit(commit, updatedState)) {
                // If checkouts have not changed then just retry without
                // recomputing commit
                innerLoop(updatedLastEntry)
              } else {
                // At least one checkout changed, have to recompute commit
                loop()
              }
            }

          }
          innerLoop(lastEntry)
        }
      }
      Future { loop() }
    }

    def canCommit(commit: Commit[ID,A,P], state: OldStateEx) : Boolean = {
      // Check if any of commit's checkout verions changed
      commit.checkout.forall { case (id,version) =>
        state._findRecord(id).exists(_.version == version)
      }
    }

    override def putFold[X](id: ID)(
      f: OldState => (A, X),
      g: Exception => X
    )(implicit metadata: Metadata): Future[X] = {
      setState { currentState =>
        if(
          currentState.active.contains(id) == false &&
          currentState.inactive.contains(id) == false
        ) {
          val (value,x) = f(currentState)
          val commit = Commit.newBuilder
            .put(id,value)
            .result()
          (commit,metadata,x)
        } else {
          (Commit.noChange[ID,A,P],metadata,
            g(new IllegalArgumentException(s"Key $id already exists!"))
          )
        }
      }
    }


    override def replaceFold[X](id: ID)(
      f: OldState => (A,X),
      g: Exception => X
    )(implicit metadata: Metadata): Future[X] = {
      setState { currentState =>
        if(
          currentState.active.contains(id) ||
          currentState.inactive.contains(id)
        ) {
          val record = currentState.active(id)
          val oldValue = record.value
          val (newValue,x) = f(currentState)
          val patch = oldValue calcDiff newValue
          val commit = Commit.newBuilder
            .replace(id,record.version,patch)
            .result()

          (commit, metadata, x)

        } else {
          (Commit.noChange, metadata,
            g(new IllegalArgumentException(s"Key $id does not exist!"))
          )
        }
      }
    }

    override def reactivate(id: ID)(implicit
      metadata: Metadata
    ): Future[Boolean] = {
      setState { currentState =>
        currentState.inactive.get(id) match {
          case Some(record) =>
            val commit = Commit.newBuilder
              .reactivate(id,record.version)
              .result()
            (commit, metadata, true)
          case None =>
            (Commit.noChange, metadata, false)
        }
      }
    }

    override def deactivate(id: ID)(implicit
      metadata: Metadata
    ): Future[Boolean] = {
      setState { currentState =>
        currentState.active.get(id) match {
          case Some(record) =>
            val commit = Commit.newBuilder
              .deactivate(id,record.version)
              .result()
            (commit, metadata, true)
          case None =>
            (Commit.noChange, metadata, false)
        }
      }
    }


    override def commitFold[X](
      f: OldState => (Commit[ID,A,P],X),
      g: Exception => X
    )(implicit metadata: Metadata): Future[X] =
      setState { currentState =>
        val (commit,x) = f(currentState)
        (commit, metadata, x)
      }

    override def mergeFold[X](
      f: OldState => (PersistentMap[ID,A,P],X),
      g: Exception => X
    )(implicit metadata: Metadata): Future[X] = {
      def loop() : Future[X] = {
        val lastEntry = whenToOldState.lastEntry
        val currentState = whenToOldState.lastEntry.getValue
        val (other, x) = f(currentState)
        for {
          zomCommit <- other.zomCommit
          result <- {
            if (
              zomCommit.nonEmpty &&
              canCommit(zomCommit.head._1, currentState)
            ) {
              val isSuccess =
                whenToOldState.synchronized {
                  if (lastEntry.getKey == whenToOldState.lastEntry.getKey) {
                    println(s"Appending $zomCommit")
                    _appendCommits(
                      whenToOldState,
                      zomCommit,
                      currentState
                    )
                    true
                  } else {
                    false
                  }
                }
              if (isSuccess) {
                x.future
              } else {
                loop()
              }
            } else {
              g(new RuntimeException("Merge conflict")).future
            }

          }
        } yield result
      }
      loop()
    }

    def currentState = whenToOldState.lastEntry.getValue
    override def count = currentState.count
    override def toMap = currentState.toMap
    override def findActiveIds = currentState.findActiveIds
    override def find(id: ID) = currentState.find(id)
    override def findInactiveIds = currentState.findInactiveIds
    override def checkout(filter: ID => Boolean) =  currentState.checkout(filter)
  }
  
  override def now = NowStateEx

  case class FutureStateEx(
    base: OldStateEx
  ) extends FutureState {
    val builder = Commit.newBuilder[ID,A,P]

    override def put(
      id: ID,
      value: A
    ): FutureState = {
      builder.put(id,value)
      this
    }

    override def replace(
      id: ID,
      value: A
    ): FutureState = {
      val record = base.active(id)
      val patch = record.value calcDiff value
      builder.replace(id,record.version,patch)
      this
    }

    override def reactivate(
      id: ID
    ): FutureState = {
      val record = base.inactive(id)
      builder.reactivate(id,record.version)
      this
    }

    override def deactivate(
      id: ID
    ): FutureState = {
      builder.deactivate(id, base.active(id).version)
      this
    }

    override def find(id: ID) = {
      val record = base.active(id)
      builder.checkout(id,record.version)
      base.find(id)
    }

    override def commit()(implicit metadata: Metadata): Future[Boolean] = {
      now.commit(builder.result())
    }
  }
  
  override def future = FutureStateEx(whenToOldState.lastEntry().getValue)

//  override def atomically(f: (OldState, FutureState) => FutureState): Future[Try[OldState]] = ???

//  override def asMap: Map[ID, A] = ???
}
