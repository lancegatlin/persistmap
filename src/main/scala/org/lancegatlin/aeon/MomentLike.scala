package org.lancegatlin.aeon

//import org.lancegatlin.aeon.impl.EmptyMoment

import scala.concurrent.Future

trait Moment[A,+B] {
  def active : Projection[A,Record.Active[B]]
  def inactive : Projection[A,Record.Inactive]
  def all : Projection[A,Record[B]]

  def materialize : Future[MaterializedMoment[A,B]]
}

trait MomentLike[
  A,
  +B,
  +CRTP <: MomentLike[A,B,CRTP]
] extends Moment[A,B] with ProjectionLike[A,B,CRTP] { self: CRTP =>
}


//object MomentLike {
//  private[this] val _empty = EmptyMoment
//  def empty[A,B] = _empty.asInstanceOf[MomentLike[A,B,_]]
//}