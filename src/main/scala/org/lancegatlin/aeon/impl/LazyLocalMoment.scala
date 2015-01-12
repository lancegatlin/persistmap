package org.lancegatlin.aeon.impl

import org.lancegatlin.aeon._

trait LazyLocalMoment[A,+B] extends
  LocalMoment[A,B] { self =>
  def active: Map[A,Record.Active[B]]
  def inactive: Map[A,Record.Inactive]

  override lazy val count = (active.size,inactive.size)

  override lazy val findActiveIds = active.keys
  override lazy val findInactiveIds = inactive.keys

  override def find(key: A) = active.get(key).map(_.value)
  override def findVersion(key: A) = active.get(key).map(_.version)

  override def findRecord(key: A) = active.get(key) orElse inactive.get(key)

  override def filterKeys(f: A => Boolean): LazyLocalMoment[A,B] =
    LazyLocalMoment(
      calcActive = self.active.filterKeys(f),
      calcInactive = self.inactive.filterKeys(f)
    )

  override lazy val toMap = active.map { case (key,record) =>
    (key,record.value)
  }.toMap


  override lazy val materialize = MaterializedMoment(
    active = active.map { case (key,record) => (key,record.materialize) }.toMap,
    inactive = inactive
  )
  lazy val lift : Moment[A,B] = LiftedLocalMoment[A,B,LazyLocalMoment](this)
}

object LazyLocalMoment {
  private[this] val _empty = LazyLocalMomentImpl[Any,Nothing]()(Map.empty)
  def empty[A,B] = _empty.asInstanceOf[LazyLocalMoment[A,B]]

  case class LazyLocalMomentImpl[A,B]()(
    calcActive: Map[A,Record.Active[B]],
    calcInactive: Map[A,Record.Inactive] = Map.empty[A,Record.Inactive]
  ) extends LazyLocalMoment[A,B] {
    lazy val active = calcActive
    lazy val inactive = calcInactive
  }

  def apply[A,B](kv: (A,B)*) : LazyLocalMoment[A,B] =
    LazyLocalMomentImpl[A,B]()(
      calcActive = kv.map { case (key,value) => (key, Record.lazyApply(value))}.toMap
    )

  def apply[A,B](
    calcActive: => Map[A,Record.Active[B]],
    calcInactive: => Map[A,Record.Inactive]
  ) : LazyLocalMoment[A,B] =
    LazyLocalMomentImpl[A,B]()(
      calcActive = calcActive,
      calcInactive = calcInactive
    )
}
