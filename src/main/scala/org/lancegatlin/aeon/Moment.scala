package org.lancegatlin.aeon

import org.lancegatlin.aeon.impl.EmptyMoment

import scala.concurrent.Future

trait Moment[A,+B] extends Projection[A,B] {
  override def filterKeys(f: (A) => Boolean): Moment[A,B]

  def active : Projection[A,Record.Active[B]]
  def inactive : Projection[A,Record.Inactive]
  def all : Projection[A,Record[B]]

  def materialize() : Future[MaterializedMoment[A,B]]
}

object Moment {
  private[this] val _empty = EmptyMoment
  def empty[A,B] = _empty.asInstanceOf[Moment[A,B]]
}