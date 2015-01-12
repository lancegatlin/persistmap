package org.lancegatlin.aeon

trait LocalMoment[A,+B] extends Map[A,B] {
  def active : Map[A,Record.Active[B]]
  def inactive : Map[A,Record.Inactive]
  def all: Map[A,Record[B]]

  abstract override def filterKeys(f: A => Boolean) : LocalMoment[A,B]

  def materialize : MaterializedMoment[A,B]
  def asMoment: Moment[A,B]
}

object LocalMoment {
  private[this] val _empty = MaterializedMoment.empty[Any,Nothing]
  def empty[A,B] = _empty.asInstanceOf[LocalMoment[A,B]]

  def apply[A,B](
    kv: (A,B)*
  ) : LocalMoment[A,B] =
    MaterializedMoment(kv:_*)

  def apply[A,B](
    active: Map[A,Record.Materialized[B]],
    inactive: Map[A,Record.Inactive]
  ) : MaterializedMoment[A,B] =
    MaterializedMoment[A,B](
      active = active,
      inactive = inactive
    )
}