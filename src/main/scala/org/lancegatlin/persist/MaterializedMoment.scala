package org.lancegatlin.persist

trait MaterializedMoment[A,+B] extends
  LocalMoment[A,B] {
  def active: Map[A,Record.Materialized[B]]
  def inactive: Map[A,Record.Materialized[B]]

  override def count = (active.size,inactive.size)

  override def findActiveIds = active.keys
  override def findInactiveIds = inactive.keys

  override def find(key: A) = active.get(key).map(_.value)
  override def findRecord(key: A) = active.get(key) orElse inactive.get(key)
  override def findVersion(key: A) = active.get(key).map(_.version)


  override def filter(f: (A,Boolean) => Boolean): MaterializedMoment[A, B] = {
    MaterializedMoment(
      active = active.filterKeys({ k => f(k,true) }),
      inactive = inactive.filterKeys({ k => f(k,false) })
    )
  }

  override def toMap = active.map { case (key,record) =>
    (key,record.value)
  }.toMap


  override def materialize = this
  lazy val lift : Moment[A,B] = LiftedLocalMoment[A,B,MaterializedMoment](this)
}

object MaterializedMoment {
  private[this] val _empty = MaterializedMomentImpl[Any,Nothing](Map.empty)
  def empty[A,B] = _empty.asInstanceOf[MaterializedMoment[A,B]]

  case class MaterializedMomentImpl[A,B](
    active: Map[A,Record.Materialized[B]],
    inactive: Map[A,Record.Materialized[B]] = Map.empty[A,Record.Materialized[B]]
  ) extends MaterializedMoment[A,B]
  def apply[A,B](kv: (A,B)*) : MaterializedMoment[A,B] =
    MaterializedMomentImpl[A,B](
      active = kv.map { case (key,value) => (key, Record(value))}.toMap
    )

  def apply[A,B](
    active: Map[A,Record.Materialized[B]],
    inactive: Map[A,Record.Materialized[B]]
  ) : MaterializedMoment[A,B] =
    MaterializedMomentImpl[A,B](
      active = active,
      inactive = inactive
    )
}
