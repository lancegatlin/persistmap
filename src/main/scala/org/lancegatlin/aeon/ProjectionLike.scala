package org.lancegatlin.aeon

import scala.concurrent.Future

trait Projection[A,+B] {
  def size: Future[Int]
  def keys: Future[Iterable[A]]
  def find(key: A) : Future[Option[B]]
  def filterKeys(f: A => Boolean) : Projection[A,B]
  // def mapValues[C,BB >: B](f: BB => C) : Aspect[A,C]

  // def aggregate[C,BB >: B](z: => C)(seqop: (C, (A,BB)) => C, combop: (C,C) => C) : Future[C]
  // def reduce[BB >: B](f: (BB,BB) => BB) : Future[BB]
  def toMap: Future[Map[A,B]]
}

trait ProjectionLike[
  A,
  +B,
  +CRTP <: ProjectionLike[A,B,CRTP]
] extends Projection[A,B] { self: CRTP =>
  override def filterKeys(f: A => Boolean) : CRTP
}

trait DelegatedProjection[A,+B] extends Projection[A,B] {
  def delegate: Projection[A,B]

  override def size = delegate.size
  override def keys = delegate.keys
  override def find(key: A) = delegate.find(key)
  override def toMap = delegate.toMap
}

trait DelegatedProjectionLike[
  A,
  +B,
  +CRTP <: DelegatedProjectionLike[A,B,CRTP,CRTP2],
  +CRTP2 <: ProjectionLike[A,B,CRTP2]
] extends DelegatedProjection[A,B] with ProjectionLike[A,B,CRTP] { self:CRTP =>
}

//object DelegatedProjectionLike {
//  case class DelegatedProjectionImpl[A,B,CRTP <: ProjectionLike[A,B,CRTP]](
//    delegate: ProjectionLike[A,B,CRTP]
//  ) extends
//    DelegatedProjectionLike[A,B,DelegatedProjectionImpl[A,B,CRTP],CRTP] {
//    override def filterKeys(f: (A) => Boolean) =
//      DelegatedProjectionImpl(delegate.filterKeys(f))
//  }
//  def apply[A,B,CRTP <: ProjectionLike[A,B,CRTP]](
//    delegate: ProjectionLike[A,B,CRTP]
//  ) : DelegatedProjectionLike[A,B,DelegatedProjectionImpl[A,B,CRTP],CRTP] =
//    DelegatedProjectionImpl(delegate)
//}
