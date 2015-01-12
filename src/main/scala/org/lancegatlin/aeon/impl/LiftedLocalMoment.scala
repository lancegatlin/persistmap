package org.lancegatlin.aeon.impl

import scala.language.higherKinds
import org.lancegatlin.aeon._
import s_mach.concurrent._

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

trait LiftedLocalMoment[A,+B,+LM <: LocalMoment[A,B]] extends Moment[A,B] with LiftedLocalProjection[A,B] { self =>
  def local:LM
  // Note: these have to be lazy to avoid init order NPE
  override lazy val active = LiftedMapProjection(self.local.active)
  override lazy val inactive = LiftedMapProjection(self.local.inactive)
  override lazy val all = LiftedMapProjection(self.local.all)

  override def materialize() = local.materialize.future
}

object LiftedLocalMoment {
  case class LiftedLocalMomentImpl[A,B,+LM <: LocalMoment[A,B]](
    local:LM
  ) extends LiftedLocalMoment[A,B,LM] {
    override def filterKeys(f: (A) => Boolean) =
      LiftedLocalMomentImpl[A,B,LocalMoment[A,B]](local.filterKeys(f))
  }

  def apply[A,B,LM <: LocalMoment[A,B]](self:LM) : Moment[A,B] =
    LiftedLocalMomentImpl[A,B,LM](self)
}

