package org.lancegatlin.aeon

trait LocalMoment[A,+B] extends LocalProjection[A,B] {
  def active : Map[A,Record.Active[B]]
  def inactive : Map[A,Record.Inactive]
  def all: Map[A,Record[B]]

  def materialize : MaterializedMoment[A,B]
  def asMoment: Moment[A,B]
}

trait LocalMomentLike[
  A,
  +B,
  +CRTP <: LocalMomentLike[A,B,CRTP]
] extends LocalMoment[A,B] with LocalProjectionLike[A,B,CRTP] { self: CRTP =>
}

object LocalMoment {
  private[this] val _empty = MaterializedMoment.empty[Any,Nothing]
  def empty[A,B] = _empty.asInstanceOf[LocalMoment[A,B]]
}