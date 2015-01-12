package org.lancegatlin.aeon.impl

import scala.language.higherKinds
import org.lancegatlin.aeon._
import s_mach.concurrent._

trait LiftedLocalProjection[A,+B] extends Projection[A,B] {
  def local: Map[A,B]

  override def size = local.size.future
  override def find(key: A) = local.get(key).future
  override def keys = local.keys.future
  override def filterKeys(f: (A) => Boolean) : Projection[A,B] =
    new LiftedLocalProjection[A,B] {
      def local = local.filterKeys(f)
    }
  override def toMap = local.toMap.future
}

trait LiftedLocalMoment[A,+B,+LM[AA,+BB] <: LocalMoment[AA,BB]] extends Moment[A,B] with LiftedLocalProjection[A,B] { self =>
  def local:LM[A,B]
  override val active = new LiftedLocalProjection[A,Record.Active[B]] {
    def local = self.local.active
  }
  override val inactive = new LiftedLocalProjection[A,Record.Inactive] {
    def local = self.local.inactive
  }
  override val all = new LiftedLocalProjection[A,Record[B]] {
    def local = self.local.all
  }

  override def filterKeys(f: A => Boolean) : Moment[A,B] ={
    val l:LocalMoment[A,B] = local
    val newL:LocalMoment[A,B] = l.filterKeys(f).asInstanceOf[LocalMoment[A,B]]
    LiftedLocalMoment[A,B,LocalMoment](newL)
  }
//    LiftedLocalMoment[A,B,LocalMoment](local.filterKeys(f))

  override def materialize = local.materialize.future
}

object LiftedLocalMoment {
  case class LiftedLocalMomentImpl[A,B,+LM[AA,+BB] <: LocalMoment[AA,BB]](
    local:LM[A,B]
  ) extends LiftedLocalMoment[A,B,LM]

  def apply[A,B,LM[AA,+BB] <: LocalMoment[AA,BB]](self:LM[A,B]) : Moment[A,B] =
    LiftedLocalMomentImpl[A,B,LM](self)
}

