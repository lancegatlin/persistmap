package org.lancegatlin.persist

import java.util.concurrent.ConcurrentSkipListMap

import org.joda.time.Instant
import s_mach.concurrent._
import s_mach.datadiff._

import scala.collection.mutable
import scala.concurrent.{ExecutionContext, Future}

object LocalCommitMap {
  def apply[A,B,PB](kv:(A,B)*)(implicit
    ec: ExecutionContext,
    dataDiff:DataDiff[B,PB]
    ) : LocalCommitMap[A,B,PB] = {
    val now = Instant.now()
    new LocalCommitMap[A,B,PB](
      _baseWhen = When(now,now),
      _baseState = MaterializedMoment(kv:_*),
      zomBaseCommit = Nil
    )
  }

}

class LocalCommitMap[A,B,PB](
  _baseWhen: When,
  _baseState: MaterializedMoment[A,B] = MaterializedMoment.empty[A,B],
  zomBaseCommit: List[(List[Commit[A,B,PB]],Metadata)]
)(implicit
  val executionContext: ExecutionContext,
  val dataDiff:DataDiff[B,PB]
) extends CommitMap[A,B,PB] { self =>

  val baseState = BaseMoment(_baseState)

  val whenToOldState : ConcurrentSkipListMap[Long,OldMoment] = {
    val m = new ConcurrentSkipListMap[Long,OldMoment]()

    m.put(_baseWhen.end.getMillis, baseState)
    m
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


  override def base = {
    baseState.future
  }

  override def zomCommit: Future[List[(Commit[A,B,PB], Metadata)]] = {
    import scala.collection.JavaConverters._
    whenToOldState
      .descendingMap()
      .entrySet.asScala
      .iterator
      .flatMap { entry =>
        entry.getValue.oomCommit
      }
      .toList

  }.future

  trait OldMoment extends super.OldMoment {
    def oomCommit: List[(Commit[A,B,PB],Metadata)]
    def local: LocalMoment[A,B]

    override def checkout(
      filter: (A,Boolean) => Boolean
    ): Future[LocalCommitMap[A,B,PB]] = {
      for {
        materializedMoment <- materialize
      } yield {
        new LocalCommitMap(
          // TODO: last is super inefficient
          _baseWhen = When(oomCommit.last._2.when,oomCommit.head._2.when),
          _baseState = LocalMoment(
            active = materializedMoment.active.filterKeys({ k => filter(k,true) }),
            inactive = materializedMoment.inactive.filterKeys({ k => filter(k,false)})
          ),
          zomBaseCommit = Nil
        )
      }
    }
  }

  case class BaseMoment(
    local: MaterializedMoment[A,B]
  ) extends OldMoment with LiftedLocalMoment[A,B,MaterializedMoment] {
    override def oomCommit = Nil
  }

  case class LazyOldMoment(
    oomCommit: List[(Commit[A,B,PB],Metadata)],
    prev: OldMoment
  ) extends OldMoment with LiftedLocalMoment[A,B,LazyLocalMoment] {
    val local = LazyLocalMoment(
      calcActive = {
        val builder = mutable.Map[A,Record[B]](prev.local.active.toSeq:_*)
        oomCommit.foreach { case (commit,_) =>
          commit.put.foreach { case (key,value) =>
            builder.put(key,Record(value))
          }
          commit.replace.foreach { case (key,patch) =>
            lazy val (calcValue,calcVersion) = {
              // Note: not closing over builder here so that it can be discarded
              val record = prev.local.findRecord(key).get
              (record.value applyPatch patch, record.version + 1)
            }
            builder.put(key, Record.lazyApply(
              calcValue = calcValue,
              calcVersion = calcVersion
            ))
          }
          commit.deactivate.foreach(builder.remove)
          commit.reactivate.foreach { key =>
            lazy val (calcValue,calcVersion) = {
              // Note: not closing over builder here so that it can be discarded
              val record = prev.local.findRecord(key).get
              (record.value,record.version)
            }
            builder.put(key, Record.lazyApply(
              calcValue = calcValue,
              calcVersion = calcVersion
            ))
          }
        }
        builder.toMap
      },
      calcInactive = {
        val builder = mutable.Map[A,Record[B]](prev.local.inactive.toSeq:_*)
        oomCommit.foreach { case (commit,_) =>
          commit.reactivate.foreach(builder.remove)
            commit.deactivate.foreach { key =>
              lazy val (calcValue,calcVersion) = {
                // Note: not closing over builder here so that it can be
                // discarded
                val record = prev.local.findRecord(key).get
                (record.value,record.version)
              }
              builder.put(key, Record.lazyApply(
                calcValue = calcValue,
                calcVersion = calcVersion
              ))
            }
          }
        builder.toMap
      }
    )
  }

  case class MaterializedOldMoment(
    oomCommit: List[(Commit[A,B,PB],Metadata)],
    prev: OldMoment
  ) extends OldMoment with LiftedLocalMoment[A,B,MaterializedMoment] {
    val local = MaterializedMoment(
      active = {
        val builder = mutable.Map[A,Record.Materialized[B]](
          prev.local.active
            .iterator
            .map { case (key,record) => (key,record.materialize) }
            .toSeq:_*
        )
        oomCommit.foreach { case (commit,_) =>
          commit.put.foreach { case (key,value) =>
            builder.put(key,Record(value))
          }
          commit.replace.foreach { case (key,patch) =>
            val record = prev.local.findRecord(key).get
            val value = record.value applyPatch patch
            val version = record.version + 1
            builder.put(key, Record(
              value = value,
              version = version
            ))
          }
          commit.deactivate.foreach(builder.remove)
          commit.reactivate.foreach { key =>
            val record = prev.local.findRecord(key).get
            builder.put(key, record.materialize)
          }
        }
        builder.toMap
      },
      inactive = {
        val builder = mutable.Map[A,Record.Materialized[B]](
          prev.local.inactive
            .iterator
            .map { case (key,record) => (key,record.materialize) }
            .toSeq:_*
        )
        oomCommit.foreach { case (commit,_) =>
          commit.reactivate.foreach(builder.remove)
            commit.deactivate.foreach { key =>
              val record = prev.local.findRecord(key).get
              builder.put(key, record.materialize)
            }
          }
        builder.toMap
      }
    )
  }


//  object NowStateEx extends NowState {
//    def setState[X](
//      f: OldStateEx => (Commit[A,B,PB],Metadata,X)
//    ) : Future[X] = {
//      def loop() : X = {
//        val lastEntry = whenToOldState.lastEntry
//        val nextWhen = Instant.now()
//        val currentState = lastEntry.getValue
//        val (commit, metadata, x) = f(currentState)
//        val updatedMetadata = metadata.copy(when = nextWhen)
//        if(commit.isNoChange) {
//          x
//        } else {
//          val newState = LazyOldState(currentState,commit,updatedMetadata)
//          def innerLoop(lastEntry: java.util.Map.Entry[Long,OldStateEx]) : X = {
//            // Note: need synchronized here to ensure no new states are inserted
//            // during check-execute below -- lock time has been minimized
//            val isSuccess =
//              whenToOldState.synchronized {
//                if (lastEntry.getKey == whenToOldState.lastEntry.getKey) {
//                  whenToOldState.put(nextWhen.getMillis, newState)
//                  true
//                } else {
//                  false
//                }
//              }
//            if(isSuccess) {
//              x
//            } else {
//              // State during computation
//              val updatedLastEntry = whenToOldState.lastEntry
//              val updatedState = updatedLastEntry.getValue
//              if(canCommit(commit, updatedState)) {
//                // If checkouts have not changed then just retry without
//                // recomputing commit
//                innerLoop(updatedLastEntry)
//              } else {
//                // At least one checkout changed, have to recompute commit
//                loop()
//              }
//            }
//
//          }
//          innerLoop(lastEntry)
//        }
//      }
//      Future { loop() }
//    }
//
//    def canCommit(commit: Commit[A,B,PB], state: OldStateEx) : Boolean = {
//      // Check if any of commit's checkout verions changed
//      commit.checkout.forall { case (key,version) =>
//        state._findRecord(key).exists(_.version == version)
//      }
//    }
//
//    override def putFold[X](key: A)(
//      f: OldState => (A, X),
//      g: Exception => X
//    )(implicit metadata: Metadata): Future[X] = {
//      setState { currentState =>
//        if(
//          currentState.active.contains(key) == false &&
//          currentState.inactive.contains(key) == false
//        ) {
//          val (value,x) = f(currentState)
//          val commit = Commit.newBuilder
//            .put(key,value)
//            .result()
//          (commit,metadata,x)
//        } else {
//          (Commit.noChange[A,B,PB],metadata,
//            g(new IllegalArgumentException(s"Key $id already exists!"))
//          )
//        }
//      }
//    }
//
//
//    override def replaceFold[X](key: A)(
//      f: OldState => (A,X),
//      g: Exception => X
//    )(implicit metadata: Metadata): Future[X] = {
//      setState { currentState =>
//        if(
//          currentState.active.contains(key) ||
//          currentState.inactive.contains(key)
//        ) {
//          val record = currentState.active(key)
//          val oldValue = record.value
//          val (newValue,x) = f(currentState)
//          val patch = oldValue calcDiff newValue
//          val commit = Commit.newBuilder
//            .replace(key,record.version,patch)
//            .result()
//
//          (commit, metadata, x)
//
//        } else {
//          (Commit.noChange, metadata,
//            g(new IllegalArgumentException(s"Key $id does not exist!"))
//          )
//        }
//      }
//    }
//
//    override def reactivate(key: A)(implicit
//      metadata: Metadata
//    ): Future[Boolean] = {
//      setState { currentState =>
//        currentState.inactive.get(key) match {
//          case Some(record) =>
//            val commit = Commit.newBuilder
//              .reactivate(key,record.version)
//              .result()
//            (commit, metadata, true)
//          case None =>
//            (Commit.noChange, metadata, false)
//        }
//      }
//    }
//
//    override def deactivate(key: A)(implicit
//      metadata: Metadata
//    ): Future[Boolean] = {
//      setState { currentState =>
//        currentState.active.get(key) match {
//          case Some(record) =>
//            val commit = Commit.newBuilder
//              .deactivate(key,record.version)
//              .result()
//            (commit, metadata, true)
//          case None =>
//            (Commit.noChange, metadata, false)
//        }
//      }
//    }
//
//
//    override def commitFold[X](
//      f: OldState => (Commit[A,B,PB],X),
//      g: Exception => X
//    )(implicit metadata: Metadata): Future[X] =
//      setState { currentState =>
//        val (commit,x) = f(currentState)
//        (commit, metadata, x)
//      }
//
//    override def mergeFold[X](
//      f: OldState => (PersistentMap[A,B,PB],X),
//      g: Exception => X
//    )(implicit metadata: Metadata): Future[X] = {
//      def loop() : Future[X] = {
//        val lastEntry = whenToOldState.lastEntry
//        val currentState = whenToOldState.lastEntry.getValue
//        val (other, x) = f(currentState)
//        for {
//          zomCommit <- other.zomCommit
//          result <- {
//            if (
//              zomCommit.nonEmpty &&
//              canCommit(zomCommit.head._1, currentState)
//            ) {
//              val isSuccess =
//                whenToOldState.synchronized {
//                  if (lastEntry.getKey == whenToOldState.lastEntry.getKey) {
//                    _appendCommits(
//                      whenToOldState,
//                      zomCommit,
//                      currentState
//                    )
//                    true
//                  } else {
//                    false
//                  }
//                }
//              if (isSuccess) {
//                x.future
//              } else {
//                loop()
//              }
//            } else {
//              g(new RuntimeException("Merge conflict")).future
//            }
//
//          }
//        } yield result
//      }
//      loop()
//    }
//
//    def currentState = whenToOldState.lastEntry.getValue
//    override def count = currentState.count
//    override def toMap = currentState.toMap
//    override def findActiveIds = currentState.findActiveIds
//    override def find(key: A) = currentState.find(key)
//    override def findInactiveIds = currentState.findInactiveIds
//    override def checkout(filter: A => Boolean) =  currentState.checkout(filter)
//  }
//
//  override def now = NowStateEx
//
//  case class FutureStateEx(
//    base: OldStateEx
//  ) extends FutureState {
//    val builder = Commit.newBuilder[A,B,PB]
//
//    override def put(
//      key: A,
//      value: A
//    ): FutureState = {
//      builder.put(key,value)
//      this
//    }
//
//    override def replace(
//      key: A,
//      value: A
//    ): FutureState = {
//      val record = base.active(key)
//      val patch = record.value calcDiff value
//      builder.replace(key,record.version,patch)
//      this
//    }
//
//    override def reactivate(
//      key: A
//    ): FutureState = {
//      val record = base.inactive(key)
//      builder.reactivate(key,record.version)
//      this
//    }
//
//    override def deactivate(
//      key: A
//    ): FutureState = {
//      builder.deactivate(key, base.active(key).version)
//      this
//    }
//
//    override def find(key: A) = {
//      val record = base.active(key)
//      builder.checkout(key,record.version)
//      base.find(key)
//    }
//
//    override def commit()(implicit metadata: Metadata): Future[Boolean] = {
//      now.commit(builder.result())
//    }
//  }
//
//  override def future = FutureStateEx(whenToOldState.lastEntry().getValue)
//
////  override def atomically(f: (OldState, FutureState) => FutureState): Future[Try[OldState]] = ???
//
////  override def asMap: Map[A, A] = ???


  case object _NoOldMoment extends OldMoment with LiftedLocalMoment[A,B,LocalMoment] {
    val local = LocalMoment.empty[A,B]
    val oomCommit = Nil
  }

  override def isNoOldMoment(o: super.OldMoment) = o eq _NoOldMoment


  override def old(when: Instant): OldMoment = {
    val key = whenToOldState.floorKey(when.getMillis)
    whenToOldState.get(key) match {
      case null => _NoOldMoment
      case v => v
    }
  }

  override def now: NowMoment = ???

  override def future(f: (FutureMoment) => FutureMoment): Future[Boolean] = ???

  override def findOld(when: Instant, n: Int): Future[List[OldMoment]] = ???
}
