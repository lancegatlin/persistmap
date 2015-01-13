package org.lancegatlin.aeon.impl

import org.lancegatlin.aeon._

trait LazyLocalMoment[A,+B] extends LocalMoment[A,B] { self =>
  override lazy val materialize = {
    MaterializedMoment(
      active = active.map { case (key,record) => (key, record.materialize) }.toMap,
      inactive = inactive
    )
  }
  lazy val asMoment : Moment[A,B] = LiftedLocalMoment[A,B,LazyLocalMoment[A,B]](this)
}

object LazyLocalMoment {
  private[this] val _empty = LazyLocalMomentImpl[Any,Nothing]()(Map.empty)
  def empty[A,B] = _empty.asInstanceOf[LazyLocalMoment[A,B]]

  case class LazyLocalMomentImpl[A,B]()(
    calcActive: Map[A,Record.Active[B]],
    calcInactive: Map[A,Record.Inactive] = Map.empty[A,Record.Inactive]
  ) extends DelegatedLocalProjection[A,B] with LazyLocalMoment[A,B] {
    lazy val active = calcActive
    lazy val inactive = calcInactive
    lazy val delegate = active.mapValues(_.value)
    lazy val all = active ++ inactive

    override def filterKeys(f: (A) => Boolean) =
      LazyLocalMomentImpl()(
        calcActive = active.filterKeys(f),
        calcInactive = inactive.filterKeys(f)
      )
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
