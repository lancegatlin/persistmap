package org.lancegatlin.aeon

import org.lancegatlin.aeon.impl.EmptyMoment

import scala.concurrent.Future

trait Moment[A,+B] {
 // Removed this b/c it makes structurally sharing moments impossible
//  def when: (Instant,Instant)

  def count : Future[(Int,Int)]

  def findActiveIds: Future[Iterable[A]]
  def findInactiveIds: Future[Iterable[A]]

  def find(key: A) : Future[Option[B]]
  def findVersion(key: A) : Future[Option[Long]]

  def findRecord(key: A) : Future[Option[Record[B]]]

  def filter(f: (A,Boolean) => Boolean) : Future[Moment[A,B]]

  def active : Future[Iterable[(A,Record[B])]]
  def inactive : Future[Iterable[(A,Record[B])]]

  def toMap : Future[Map[A, B]]

  def materialize : Future[MaterializedMoment[A,B]]
}

object Moment {
  private[this] val _empty = new EmptyMoment { }
  def empty[A,B] = _empty.asInstanceOf[Moment[A,B]]
}