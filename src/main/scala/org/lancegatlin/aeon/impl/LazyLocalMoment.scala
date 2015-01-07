package org.lancegatlin.aeon.impl

import org.lancegatlin.aeon._

trait LazyLocalMoment[A,+B] extends
  LocalMoment[A,B] { self =>
  def active: Map[A,Record[B]]
  def inactive: Map[A,Record[B]]

  override lazy val count = (active.size,inactive.size)

  override lazy val findActiveIds = active.keys
  override lazy val findInactiveIds = inactive.keys

  override def find(key: A) = active.get(key).map(_.value)
  override def findVersion(key: A) = active.get(key).map(_.version)

  override def findRecord(key: A) = active.get(key) orElse inactive.get(key)

  override def filter(f: (A,Boolean) => Boolean): LazyLocalMoment[A,B] =
    LazyLocalMoment(
      calcActive = self.active.filterKeys({ k => f(k,true)}),
      calcInactive = self.active.filterKeys({ k => f(k,true)})
    )

  override lazy val toMap = active.map { case (key,record) =>
    (key,record.value)
  }.toMap


  override lazy val materialize = MaterializedMoment(
    active = active.map { case (key,record) => (key,record.materialize) }.toMap,
    inactive = inactive.map { case (key,record) => (key,record.materialize) }.toMap
  )
  lazy val lift : Moment[A,B] = LiftedLocalMoment[A,B,LazyLocalMoment](this)
}

object LazyLocalMoment {
  private[this] val _empty = LazyLocalMomentImpl[Any,Nothing]()(Map.empty)
  def empty[A,B] = _empty.asInstanceOf[LazyLocalMoment[A,B]]

  case class LazyLocalMomentImpl[A,B]()(
    calcActive: Map[A,Record[B]],
    calcInactive: Map[A,Record[B]] = Map.empty[A,Record[B]]
  ) extends LazyLocalMoment[A,B] {
    lazy val active = calcActive
    lazy val inactive = calcInactive
  }

  def apply[A,B](kv: (A,B)*) : LazyLocalMoment[A,B] =
    LazyLocalMomentImpl[A,B]()(
      calcActive = kv.map { case (key,value) => (key, Record.lazyApply(value))}.toMap
    )

  def apply[A,B](
    calcActive: => Map[A,Record[B]],
    calcInactive: => Map[A,Record[B]]
  ) : LazyLocalMoment[A,B] =
    LazyLocalMomentImpl[A,B]()(
      calcActive = calcActive,
      calcInactive = calcInactive
    )
}
