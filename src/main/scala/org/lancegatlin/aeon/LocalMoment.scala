package org.lancegatlin.aeon

trait LocalMoment[A,+B] {

  def count : (Int,Int)

  def findActiveIds: Iterable[A]
  def findInactiveIds: Iterable[A]

  def find(key: A) : Option[B]
  def findVersion(key: A) : Option[Long]
  def findRecord(key: A) : Option[Record[B]]

  def filterKeys(f: A => Boolean) : LocalMoment[A,B]

  def active : Map[A,Record.Active[B]]
  def inactive : Map[A,Record.Inactive]

  def toMap : Map[A, B]

  def materialize : MaterializedMoment[A,B]
  def lift : Moment[A,B]
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