package org.lancegatlin.aeon.impl

import s_mach.concurrent._
import org.lancegatlin.aeon.{LocalProjection, Projection}

trait LiftedLocalProjection[A,+B] extends Projection[A,B] {
  def local: LocalProjection[A,B]

  override def size = local.size.future
  override def find(key: A) = local.find(key).future
  override def keys = local.keys.future
  override def toMap = local.toMap.future
}

trait LiftedMapProjection[A,+B] extends Projection[A,B] {
  def local: Map[A,B]

  override def size = local.size.future
  override def find(key: A) = local.get(key).future
  override def keys = local.keys.future
  override def toMap = local.toMap.future
}

object LiftedMapProjection {
  case class LiftedMapProjectionImpl[A,+B](
    local: Map[A,B]
  ) extends LiftedMapProjection[A,B] {
    override def filterKeys(f: (A) => Boolean) =
      LiftedMapProjectionImpl(local.filterKeys(f))
  }
  def apply[A,B](local: Map[A,B]) : LiftedMapProjection[A,B] =
    LiftedMapProjectionImpl(local)
}