package org.lancegatlin.aeon.diffmap

import org.joda.time.Instant
import org.lancegatlin.aeon._
import s_mach.datadiff.DataDiff

import scala.concurrent.Future

object DiffMap {
  sealed trait Event[A,B,PB]
  case class OnCommit[A,B,PB](
    oomCommit: List[(Commit[A,B,PB],Metadata)]
  ) extends Event[A,B,PB]
}

// Note: B/PB must be invariant here b/c of DataDiff type-class
trait DiffMap[A,B,PB] extends AeonMap[A,B] {

  implicit def dataDiff:DataDiff[B,PB]

  trait OldMoment extends super.OldMoment {
    override def filterKeys(f: (A) => Boolean): OldMoment

    def checkout() : Future[DiffMap[A,B,PB]]
  }

  trait NowMoment extends super.NowMoment with OldMoment {
    override def filterKeys(f: (A) => Boolean): NowMoment

    def commit(
      checkout: Checkout[A],
      oomCommit: List[(Commit[A,B,PB],Metadata)]
    ) : Future[Boolean]

    def commitFold[X](
      f: Moment[A,B] => Future[(Checkout[A],List[(Commit[A,B,PB],Metadata)],X)],
      g: Exception => X
    ) : Future[X]

    def merge(
      other: DiffMap[A,B,PB]
    )(implicit metadata: Metadata) : Future[Boolean]

    def mergeFold[X](
      f: Moment[A,B] => Future[(DiffMap[A,B,PB],X)],
      g: Exception => X
    )(implicit metadata: Metadata) : Future[X]
  }

  override def base: OldMoment
  override def old(when: Instant) : OldMoment
  override def now : NowMoment

  def zomCommit: Future[List[(Commit[A,B,PB], Metadata)]]

  protected def emitEvents : Boolean = false
  protected def onEvent(e: DiffMap.Event[A,B,PB]) : Unit = { }
}
