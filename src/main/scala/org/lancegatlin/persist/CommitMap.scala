package org.lancegatlin.persist

import org.joda.time.Instant
import s_mach.datadiff.DataDiff

import scala.concurrent.Future

// Note: B/PB must be invariant here b/c of DataDiff type-class
trait CommitMap[A,B,PB] extends PersistentMap[A,B] {

  implicit def dataDiff:DataDiff[B,PB]

  def base: Future[OldMoment]
  def zomCommit: Future[List[(Commit[A,B,PB], Metadata)]]

  trait OldMoment extends super.OldMoment {
    def checkout(
      filter: (A,Boolean) => Boolean
    ) : Future[CommitMap[A,B,PB]]
  }

  def isNoOldMoment(o: OldMoment) : Boolean

//  trait NoOldMoment extends OldMoment
  case object NoOldMoment {
    def unapply(o:OldMoment) : Option[OldMoment] = {
      if(isNoOldMoment(o)) {
        Some(o)
      } else {
        None
      }
    }
  }

  trait NowMoment extends super.NowMoment {
    def commit(
      checkout: Checkout[A],
      commit: Commit[A,B,PB]
    )(implicit
      metadata: Metadata
    ) : Future[Boolean]

    def commitFold[X](
      f: OldMoment => (Checkout[A],Commit[A,B,PB],X),
      g: Exception => X
    )(implicit metadata: Metadata) : Future[X]

    def merge(
      other: CommitMap[A,B,PB]
    )(implicit metadata: Metadata) : Future[Boolean]

    def mergeFold[X](
      f: OldMoment => (CommitMap[A,B,PB],X),
      g: Exception => X
    )(implicit metadata: Metadata) : Future[X]
  }

  override def old(when: Instant) : OldMoment
  override def now : NowMoment
}
