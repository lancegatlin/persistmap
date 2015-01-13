package org.lancegatlin.aeon

trait LocalMoment[A,+B] extends LocalProjection[A,B] {
  override def filterKeys(f: (A) => Boolean): LocalMoment[A,B]

  def active : Map[A,Record.Active[B]]
  def inactive : Map[A,Record.Inactive]
  def all: Map[A,Record[B]]

  def materialize : MaterializedMoment[A,B]
  def asMoment: Moment[A,B]
}

object LocalMoment {
  private[this] val _empty = MaterializedMoment.empty[Any,Nothing]
  def empty[A,B] = _empty.asInstanceOf[LocalMoment[A,B]]
}