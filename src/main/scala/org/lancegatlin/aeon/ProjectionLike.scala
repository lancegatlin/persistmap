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

trait DelegatedProjection[A,+B] extends Projection[A,B] {
  def delegate: Projection[A,B]

  override def size = delegate.size
  override def keys = delegate.keys
  override def find(key: A) = delegate.find(key)
  override def toMap = delegate.toMap
}