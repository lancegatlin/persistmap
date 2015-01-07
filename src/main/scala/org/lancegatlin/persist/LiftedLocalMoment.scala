package org.lancegatlin.persist

import scala.language.higherKinds
import s_mach.concurrent._

trait LiftedLocalMoment[A,+B,+LM[AA,+BB] <: LocalMoment[AA,BB]] extends Moment[A,B] {
  def local:LM[A,B]

  override lazy val count = local.count.future
  override lazy val findActiveIds = local.findActiveIds.future
  override lazy val findInactiveIds = local.findInactiveIds.future
  override def find(key: A) = local.find(key).future
  override def findVersion(key: A) = local.findVersion(key).future
  override def findRecord(key: A) =
    local.findRecord(key).future

  override def filter(f: (A, Boolean) => Boolean) =
    local.filter(f).lift.future


  override def active = local.active.future
  override def inactive = local.inactive.future

  override lazy val toMap = local.toMap.future
  override lazy val materialize = local.materialize.future
}

object LiftedLocalMoment {
  case class LiftedLocalMomentImpl[A,B,+LM[AA,+BB] <: LocalMoment[AA,BB]](
    local:LM[A,B]
  ) extends LiftedLocalMoment[A,B,LM]

  def apply[A,B,LM[AA,+BB] <: LocalMoment[AA,BB]](self:LM[A,B]) : Moment[A,B] =
    LiftedLocalMomentImpl[A,B,LM](self)
}

