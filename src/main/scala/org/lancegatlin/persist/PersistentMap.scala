package org.lancegatlin.persist

import org.joda.time.Instant

import scala.concurrent.Future

case class When(
  start: Instant,
  end: Instant
)

trait PersistentMap[A,+B] {
  trait OldMoment extends Moment[A,B]

  trait NowMoment extends Moment[A,B] {
    def deactivate(key: A)(implicit metadata: Metadata) : Future[Boolean]
    def reactivate(key: A)(implicit metadata: Metadata) : Future[Boolean]

    def put(key: A, value: A)(implicit metadata:Metadata) : Future[Boolean]

    def putFold[X](key: A)(
      f:  => (A,X),
      g: Exception => X
    )(implicit metadata:Metadata) : Future[X]

    def replace(
      key: A,
      value: A
    )(implicit metadata:Metadata) : Future[Boolean]

    def replaceFold[BB >: B,X](key: A)(
      f: Moment[A,BB] => (A,X),
      g: Exception => X
    )(implicit metadata:Metadata) : Future[X]
  }

  trait FutureMoment {
    def find(key: A) : Future[Option[A]]

    def deactivate(key: A) : FutureMoment
    def reactivate(key: A) : FutureMoment

    def put(key: A, value: A) : FutureMoment
    def replace(key: A, value: A) : FutureMoment
  }

  def old(when: Instant) : OldMoment
  def findOld(when: Instant, n: Int) : Future[List[OldMoment]]
  def now : NowMoment
  def future(
    f: FutureMoment => FutureMoment
  ) : Future[Boolean]
}
