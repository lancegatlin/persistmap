package org.lancegatlin.aeon.impl

import java.util.concurrent.ConcurrentSkipListMap

import org.joda.time.Instant
import org.lancegatlin.aeon._
import org.lancegatlin.aeon.diffmap.{DiffMap, Commit}
import s_mach.concurrent._
import s_mach.datadiff._

import scala.collection.mutable
import scala.concurrent.{ExecutionContext, Future}

object LocalDiffMap {
  def apply[A,B,PB](kv:(A,B)*)(implicit
    ec: ExecutionContext,
    dataDiff:DataDiff[B,PB]
    ) : LocalDiffMap[A,B,PB] = {
    val now = Instant.now()
    new LocalDiffMap[A,B,PB](
      _baseAeon = Aeon(now,now),
      _baseState = MaterializedMoment(kv:_*),
      zomBaseCommit = Nil
    )
  }

}

class LocalDiffMap[A,B,PB](
  _baseAeon: Aeon,
  _baseState: MaterializedMoment[A,B] = MaterializedMoment.empty[A,B],
  zomBaseCommit: List[(List[Commit[A,B,PB]],Metadata)]
)(implicit
  val executionContext: ExecutionContext,
  val dataDiff:DataDiff[B,PB]
) extends DiffMap[A,B,PB] { self =>

  type SuperOldMoment = super.OldMoment
  type SuperNowMoment = super.NowMoment
  type SuperFutureMoment = super.FutureMoment

  override val base = BaseMoment(_baseState)

  val whenToOldState : ConcurrentSkipListMap[Long,OldMoment] = {
    val m = new ConcurrentSkipListMap[Long,OldMoment]()

    m.put(_baseAeon.end.getMillis, base)
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

  trait OldMoment extends SuperOldMoment {
    def oomCommit: List[(Commit[A,B,PB],Metadata)]
    def local: LocalMoment[A,B]

    override def checkout(
      filter: (A,Boolean) => Boolean
    ): Future[LocalDiffMap[A,B,PB]] = {
      for {
        materializedMoment <- materialize
      } yield {
        new LocalDiffMap(
          // TODO: last is super inefficient
          _baseAeon = Aeon(oomCommit.last._2.when,oomCommit.head._2.when),
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
            // Note: not closing over builder here so that it can be discarded
            lazy val record = prev.local.findRecord(key).get
            lazy val calcValue = record.value applyPatch patch
            lazy val calcVersion = record.version + 1
            builder.put(key, Record.lazyApply(
              calcValue = calcValue,
              calcVersion = calcVersion
            ))
          }
          commit.deactivate.foreach(builder.remove)
          commit.reactivate.foreach { key =>
            lazy val record = prev.local.findRecord(key).get
            lazy val calcValue = record.value
            lazy val calcVersion = record.version + 1
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
            lazy val record = prev.local.findRecord(key).get
            lazy val calcValue = record.value
            lazy val calcVersion = record.version + 1
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
            val materializedRecord = record.materialize
            builder.put(
              key,
              materializedRecord.copy(
                version = materializedRecord.version + 1
              )
            )
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
              val materializedRecord = record.materialize
              builder.put(
                key,
                materializedRecord.copy(
                  version = materializedRecord.version + 1
                )
              )
            }
          }
        builder.toMap
      }
    )
  }

  val NoOldMoment = new OldMoment with LiftedLocalMoment[A,B,LocalMoment] {
    val local = LocalMoment.empty[A,B]
    val oomCommit = Nil
  }

  override def old(when: Instant): OldMoment = {
    val key = whenToOldState.floorKey(when.getMillis)
    whenToOldState.get(key) match {
      case null => NoOldMoment
      case v => v
    }
  }

  def mostRecentOldMoment = whenToOldState.lastEntry.getValue

  object NowMoment extends SuperNowMoment with LiftedLocalMoment[A,B,LocalMoment] {
    def local = mostRecentOldMoment.local

    def setState[X](
      f: OldMoment => Future[(Checkout[A],List[(Commit[A,B,PB],Metadata)],X)]
    ) : Future[X] = {
      def loop() : Future[X] = {
        val lastEntry = whenToOldState.lastEntry
        val nextAeon = Instant.now()
        val currentMoment = lastEntry.getValue
        for {
          (checkout,oomCommit, x) <- f(currentMoment)
          result <- {
//            val updatedMetadata = metadata.copy(when = nextAeon)
            if(oomCommit.isEmpty) {
              x.future
            } else {
              val newMoment = LazyOldMoment(oomCommit, currentMoment)
              def innerLoop(lastEntry: java.util.Map.Entry[Long,OldMoment]) : Future[X] = {
                // Note: need synchronized here to ensure no new states are inserted
                // during check-execute below -- lock time has been minimized
                val isSuccess =
                  whenToOldState.synchronized {
                    if (lastEntry.getKey == whenToOldState.lastEntry.getKey) {
                      whenToOldState.put(nextAeon.getMillis, newMoment)
                      true
                    } else {
                      false
                    }
                  }
                if(isSuccess) {
                  x.future
                } else {
                  // State changed during computation
                  val updatedLastEntry = whenToOldState.lastEntry
                  val updatedState = updatedLastEntry.getValue
                  if(canCommit(checkout, updatedState)) {
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
        } yield result
      }
      loop()
    }

    def canCommit(checkout: Checkout[A], moment: OldMoment) : Boolean = {
      // Check if any of commit's checkout verions changed
      checkout.forall { case (key,version) =>
        moment.local.findRecord(key).exists(_.version == version)
      }
    }

    override def put(
      key: A,
      value: B
    )(implicit metadata:Metadata) : Future[Boolean] = {
      putFold(key)(
        f = { _ => (value,true).future },
        g = { _ => false }
      )
    }

    override def putFold[X](key: A)(
      f: Moment[A,B] => Future[(B,X)],
      g: Exception => X
    )(implicit metadata:Metadata) : Future[X] = {
      setState { nowMoment =>
        if(
          nowMoment.local.active.contains(key) == false &&
          nowMoment.local.inactive.contains(key) == false
        ) {
          for {
            (value,x) <- f(nowMoment)
          } yield {
            val (checkout,commit) = CommitBuilder[A,B,PB]()
              .put(key,value)
              .result()
            (checkout,(commit,metadata) :: Nil,x)
          }
        } else {
          (Checkout.empty[A,Long],Nil,
            g(new IllegalArgumentException(s"Key $key already exists!"))
          ).future
        }
      }
    }


    override def replace(
      key: A,
      value: B
    )(implicit metadata:Metadata) : Future[Boolean] = {
      replaceFold(key)(
        f = { _ => (value,true).future },
        g = { _ => false }
      )
    }

    override def replaceFold[X](key: A)(
      f: Moment[A,B] => Future[(B,X)],
      g: Exception => X
    )(implicit metadata:Metadata) : Future[X] = {
      setState { nowMoment =>
        if(
          nowMoment.local.active.contains(key) ||
          nowMoment.local.inactive.contains(key)
        ) {
          val record = nowMoment.local.active(key)
          val oldValue = record.value
          for {
            (newValue, x) <- f(nowMoment)
          } yield {
            val patch = oldValue calcDiff newValue
            val (checkout,commit) = CommitBuilder[A,B,PB]()
              .replace(key,record.version,patch)
              .result()

            (checkout, (commit,metadata) :: Nil, x)
          }
        } else {
          (Checkout.empty[A,Long],Nil,
            g(new IllegalArgumentException(s"Key $key does not exist!"))
          ).future
        }
      }
    }

    override def deactivate(
      key: A
    )(implicit metadata: Metadata) : Future[Boolean] = {
      setState { nowMoment =>
        nowMoment.local.active.get(key) match {
          case Some(record) =>
            val (checkout,commit) = CommitBuilder[A,B,PB]()
              .deactivate(key,record.version)
              .result()
            (checkout, (commit,metadata) :: Nil, true).future
          case None =>
            (Checkout.empty[A,Long],Nil,false).future
        }
      }
    }

    override def reactivate(
      key: A
    )(implicit metadata: Metadata) : Future[Boolean] = {
      setState { nowMoment =>
        nowMoment.local.inactive.get(key) match {
          case Some(record) =>
            val (checkout,commit) = CommitBuilder[A,B,PB]()
              .reactivate(key,record.version)
              .result()
            (checkout, (commit,metadata) :: Nil, true).future
          case None =>
            (Checkout.empty[A,Long],Nil, false).future
        }
      }
    }

    override def commit(
      checkout: Checkout[A],
      oomCommit: List[(Commit[A,B,PB],Metadata)]
    ) : Future[Boolean] = {
      commitFold(
        f = { _ => (checkout,oomCommit,true).future },
        g = { _ => false }
      )
    }

    override def commitFold[X](
      f: Moment[A,B] => Future[(Checkout[A],List[(Commit[A,B,PB],Metadata)],X)],
      g: Exception => X
    ) : Future[X] = {
      setState(f)
    }


    override def merge(
      other: DiffMap[A,B,PB]
    )(implicit metadata: Metadata) : Future[Boolean] = {
      mergeFold(
        f = { _ => (other,true).future },
        g = { _ => false }
      )
    }

    override def mergeFold[X](
      f: Moment[A,B] => Future[(DiffMap[A,B,PB],X)],
      g: Exception => X
    )(implicit metadata: Metadata) : Future[X] = {
      def loop() : Future[X] = {
        val lastEntry = whenToOldState.lastEntry
        val nowMoment = whenToOldState.lastEntry.getValue
        for {
          (other, x) <- f(nowMoment)
          otherBase = other.base
          otherActive <- otherBase.active
          otherInactive <- otherBase.inactive
          zomCommit <- other.zomCommit
          result <- {
            val checkout =
              (otherActive.map { case (key,record) => (key,record.version)}) ++
              (otherInactive.map { case (key,record) => (key,record.version)})
            if (
              zomCommit.nonEmpty &&
              canCommit(checkout.toMap, nowMoment)
            ) {
              val nextMoment = LazyOldMoment(zomCommit,lastEntry.getValue)
              val nextAeon = Instant.now.getMillis
              val isSuccess =
                whenToOldState.synchronized {
                  if (lastEntry.getKey == whenToOldState.lastEntry.getKey) {
                    whenToOldState.put(nextAeon, nextMoment)
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

    override def checkout(
      filter: (A,Boolean) => Boolean
    ): Future[LocalDiffMap[A,B,PB]] = {
      mostRecentOldMoment.checkout(filter)
    }
  }

  override def now = NowMoment

  case class FutureMomentEx(
    base: OldMoment
  ) extends SuperFutureMoment {
    val builder = CommitBuilder[A,B,PB]()

    override def put(
      key: A,
      value: B
    ): FutureMoment = {
      builder.put(key,value)
      this
    }

    override def replace(
      key: A,
      value: B
    ): FutureMoment = {
      val record = base.local.active(key)
      val patch = record.value calcDiff value
      builder.replace(key,record.version,patch)
      this
    }

    override def reactivate(
      key: A
    ): FutureMoment = {
      val record = base.local.inactive(key)
      builder.reactivate(key,record.version)
      this
    }

    override def deactivate(
      key: A
    ): FutureMoment = {
      builder.deactivate(key, base.local.active(key).version)
      this
    }

    override def find(key: A) = {
      val record = base.local.active(key)
      builder.checkout(key,record.version)
      base.find(key)
    }
  }

  override def future(f: FutureMoment => Future[FutureMoment])(implicit metadata: Metadata): Future[Boolean] = {
    for {
      futureMoment <- f(FutureMomentEx(mostRecentOldMoment))
      result <- {
        val (checkout,commit) = futureMoment.asInstanceOf[FutureMomentEx].builder.result()
        now.commit(checkout, (commit,metadata) :: Nil)
      }
    } yield result
  }
}