package org.lancegatlin.aeon.impl

import java.util.concurrent.{ConcurrentLinkedQueue, ConcurrentSkipListMap}
import scala.collection.mutable
import scala.concurrent.{ExecutionContext, Future}
import s_mach.concurrent._
import s_mach.datadiff._
import org.joda.time.Instant
import org.lancegatlin.aeon._
import org.lancegatlin.aeon.diffmap.{DiffMap, Commit}

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
  import DiffMap._

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
    sb.append(s"base.aeon=${base.aeon}\n")
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
    override def filterKeys(f: (A) => Boolean): OldMoment

    def oomCommit: List[(Commit[A,B,PB],Metadata)]
    def local: LocalMoment[A,B]

    override def checkout(): Future[LocalDiffMap[A,B,PB]] = {
      val now = Instant.now()
      val materializedMoment = local.materialize
      Future.successful {
        new LocalDiffMap(
          _baseAeon = aeon,
          _baseState = materializedMoment,
          zomBaseCommit = Nil
        )
      }
    }
  }

  case class BaseMoment(
    local: MaterializedMoment[A,B]
  ) extends OldMoment with LiftedLocalMoment[A,B,MaterializedMoment[A,B]] {
    override def aeon = _baseAeon

    override def oomCommit = Nil

    override def filterKeys(f: A => Boolean) =
      BaseMoment(local.filterKeys(f))
  }


  case class LazyOldMoment(
    aeon: Aeon,
    oomCommit: List[(Commit[A,B,PB],Metadata)],
    optFilterKeys: Option[A => Boolean] = None
  ) extends OldMoment with LiftedLocalMoment[A,B,LazyLocalMoment[A,B]] {

    override def filterKeys(f: (A) => Boolean): OldMoment =
      copy(optFilterKeys = Some(f))

    // Note: prev is not saved to prevent holding long references to previous
    // old moments - don't close over prev as a val!
    def calcPrev = old(aeon.start.minus(1))

    val local = {
      def maybeFilterKeys[C](m:Map[A,C]) : Map[A,C] = {
        optFilterKeys match {
          case Some(f) => m.filterKeys(f)
          case None => m
        }
      }
      LazyLocalMoment(
        calcActive = {
          // Note: not closing over builder so that it can be discarded
          val builder = mutable.Map[A,Record.Active[B]](
            maybeFilterKeys(calcPrev.local.active).toSeq:_*
          )
          oomCommit.foreach { case (rawCommit,_) =>
            val commit = {
              optFilterKeys match {
                case Some(f) => rawCommit.filterKeys(f)
                case None => rawCommit
              }
            }
            commit.put.foreach { case (key,value) =>
              builder.put(key,Record(value))
            }
            commit.replace.foreach { case (key,patch) =>
              lazy val record = calcPrev.local.active(key)
              lazy val calcValue = record.value applyPatch patch
              lazy val calcVersion = record.version + 1
              builder.put(key, Record.lazyApply(
                calcValue = calcValue,
                calcVersion = calcVersion
              ))
            }
            commit.deactivate.foreach(builder.remove)
            commit.reactivate.foreach { case (key,value) =>
              lazy val record = calcPrev.local.inactive(key)
              lazy val calcVersion = record.version + 1
              builder.put(key, Record.lazyApply(
                calcValue = value,
                calcVersion = calcVersion
              ))
            }
          }
          builder.toMap
        },
        calcInactive = {
          // Note: not closing over builder so that it can be discarded
          val builder = mutable.Map[A,Record.Inactive](
            maybeFilterKeys(calcPrev.local.inactive).toSeq:_*
          )
          oomCommit.foreach { case (commit,_) =>
            commit.reactivate.foreach { case (k,_) => builder.remove(k) }
            commit.deactivate.foreach { key =>
              val record = calcPrev.local.active(key)
              builder.put(key, Record.Inactive(record.version + 1))
            }
          }
          builder.toMap
        }
      )
    }
  }

  case class EmptyOldMoment(
    // Note: using case class args to prevent init order NPE
    local:LocalMoment[A,B] = LocalMoment.empty[A,B],
    oomCommit: List[(Commit[A,B,PB],Metadata)] = Nil
  ) extends OldMoment with LiftedLocalMoment[A,B,LocalMoment[A,B]] {
    val aeon = Aeon(
      beginOfTime,
      _baseAeon.start
    )
    override def filterKeys(f: A => Boolean) = this
  }

  val NoOldMoment = EmptyOldMoment()

  override def old(when: Instant): OldMoment = {
    val key = whenToOldState.floorKey(when.getMillis)
    whenToOldState.get(key) match {
      case null => NoOldMoment
      case v => v
    }
  }

  def mostRecentOldMoment = whenToOldState.lastEntry.getValue

  case class NowMoment(
    oldMoment: OldMoment
  ) extends SuperNowMoment with LiftedLocalMoment[A,B,LocalMoment[A,B]] {
    def aeon = oldMoment.aeon
    def local = oldMoment.local

    override def filterKeys(f: (A) => Boolean): NowMoment =
      NowMoment(oldMoment.filterKeys(f))

    // Note: need setState for f: OldMoment => (commitFold is Moment[A,B] => ...)
    def _commitFold[X](
      f: OldMoment => Future[(Checkout[A],List[(Commit[A,B,PB],Metadata)],X)],
      g: Exception => X
    ) : Future[X] = {
      def loop() : Future[X] = {
        val lastEntry = whenToOldState.lastEntry
        val nextAeon = Instant.now()
        val currentMoment = lastEntry.getValue
        for {
          (checkout,oomCommit, x) <- f(currentMoment)
          result <- {
            val missingCheckout = calcMissingCheckout(checkout,currentMoment)
            if(missingCheckout.isEmpty) {
              if(oomCommit.nonEmpty) {
                val nextMoment = LazyOldMoment(
                  aeon = Aeon(
                    start = currentMoment.aeon.end.plus(1),
                    end = Instant.now()
                  ),
                  oomCommit = oomCommit
                )
                def innerLoop(lastEntry: java.util.Map.Entry[Long,OldMoment]) : Future[X] = {
                  // Note: need synchronized here to ensure no new states are inserted
                  // during check-execute below -- lock time has been minimized
                  if(compareAndSetNowMoment(lastEntry,nextMoment)) {
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
              } else {
                g(new RuntimeException("Empty commit")).future
              }
            } else {
              g(new RuntimeException(s"Missing keys: ${missingCheckout.mkString(",")}")).future
            }
          }
        } yield result
      }
      loop()
    }

    def calcMissingCheckout(
      checkout: Checkout[A],
      currentMoment: OldMoment
    ) : Iterable[A] = {
      checkout
        .filterNot { case (key,version) =>
          currentMoment.local.active.contains(key) ||
          currentMoment.local.inactive.contains(key)
        }
        .keys
    }

    def canCommit(checkout: Checkout[A], moment: OldMoment) : Boolean = {
      // Check if any of commit's checkout versions changed
      checkout.forall { case (key,version) =>
        val record =
          moment.local.active.get(key) match {
            case Some(_record) => _record
            case None => moment.local.inactive(key)
          }
        record.version == version
      }
    }

    def calcCheckoutVersionMismatch(
      checkout: Checkout[A],
      currentMoment: OldMoment
    ) : Iterable[(A,Long,Long)] = {
      // Note: assumed here that all checkout keys exist
      checkout.flatMap { case (key,expectedVersion) =>
        val record =
          currentMoment.local.active.get(key) match {
            case Some(_record) => _record
            case None =>
              // Note: if record isn't active is has to be inactive since
              // checkouts are confirmed to exist
              currentMoment.local.inactive(key)
          }

        if(record.version == expectedVersion) {
          None
        } else {
          Some((key,expectedVersion,record.version))
        }
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
      _commitFold({ nowMoment =>
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
      },g)
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
      _commitFold({ nowMoment =>
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
      },g)
    }

    override def deactivate(
      key: A
    )(implicit metadata: Metadata) : Future[Boolean] = {
      _commitFold({ nowMoment =>
        nowMoment.local.active.get(key) match {
          case Some(record) =>
            val (checkout,commit) = CommitBuilder[A,B,PB]()
              .deactivate(key,record.version)
              .result()
            (checkout, (commit,metadata) :: Nil, true).future
          case None =>
            (Checkout.empty[A,Long],Nil,false).future
        }
      },{ _ => false })
    }

    override def reactivate(
      key: A,
      value: B
    )(implicit metadata: Metadata) : Future[Boolean] = {
      _commitFold({ nowMoment =>
        nowMoment.local.inactive.get(key) match {
          case Some(record) =>
            val (checkout,commit) = CommitBuilder[A,B,PB]()
              .reactivate(key,value,record.version)
              .result()
            (checkout, (commit,metadata) :: Nil, true).future
          case None =>
            (Checkout.empty[A,Long],Nil, false).future
        }
      }, { _ => false })
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
      _commitFold(f,g)
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
        val currentMoment = whenToOldState.lastEntry.getValue
        for {
          (other, x) <- f(currentMoment)
          otherBase = other.base
          otherActive <- otherBase.active.toMap
          otherInactive <- otherBase.inactive.toMap
          zomCommit <- other.zomCommit
          result <- {
            if(zomCommit.nonEmpty) {
              val checkout =
                (otherActive.map { case (key,record) => (key,record.version)}) ++
                (otherInactive.map { case (key,record) => (key,record.version)})
              val missingCheckout = calcMissingCheckout(checkout,currentMoment)
              if(missingCheckout.isEmpty) {
                val checkoutVersionMismatch = calcCheckoutVersionMismatch(checkout, currentMoment)
                if (checkoutVersionMismatch.isEmpty) {
                  val nextMoment = LazyOldMoment(
                    aeon = Aeon(
                      start = currentMoment.aeon.end,
                      end = Instant.now()
                    ),
                    oomCommit = zomCommit
                  )
                  if (compareAndSetNowMoment(lastEntry, nextMoment)) {
                    x.future
                  } else {
                    loop()
                  }
                } else {
                  g(new RuntimeException(s"Merge conflict: ${
                    checkoutVersionMismatch.map { case (key,expectedVersion,version) =>
                        s"(key=$key expectedVersion=$expectedVersion foundVersion=$version)"
                    }.mkString(",")
                  }")).future
                }
              } else {
                g(new RuntimeException(s"Missing keys: ${missingCheckout.mkString(",")}")).future
              }
            } else {
              x.future
            }
          }
        } yield result
      }
      loop()
    }

    override def checkout(): Future[LocalDiffMap[A,B,PB]] = oldMoment.checkout()
  }

  def publishEvents() : Unit = {
    def loop() : Unit ={
      eventQueue.poll() match {
        case e@OnCommit(_) =>
          onEvent(e)
          loop()
        case _ =>
      }
    }
    loop()
  }
  
  def compareAndSetNowMoment(
    lastEntry: java.util.Map.Entry[Long,OldMoment],
    nextMoment: LazyOldMoment
  ): Boolean = {
    val isSuccess =
      whenToOldState.synchronized {
        if (lastEntry.getKey == whenToOldState.lastEntry.getKey) {
          whenToOldState.put(nextMoment.aeon.end.getMillis, nextMoment)
          if(emitEvents) {
            eventQueue.offer(OnCommit(nextMoment.oomCommit))
          }
          true
        } else {
          false
        }
      }
    if (isSuccess) {
      if(emitEvents) {
        Future { publishEvents() }.background
      }
      true
    } else {
      false
    }
  }
  
  override def now = NowMoment(mostRecentOldMoment)

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
      key: A,
      value: B
    ): FutureMoment = {
      val record = base.local.inactive(key)
      builder.reactivate(key,value,record.version)
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

  lazy val eventQueue = new ConcurrentLinkedQueue[Event[A,B,PB]]()
}
