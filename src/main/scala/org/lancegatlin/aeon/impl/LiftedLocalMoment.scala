package org.lancegatlin.aeon.impl

import org.lancegatlin.aeon.{LocalMoment, Moment}
import s_mach.concurrent._

import scala.language.higherKinds

trait LiftedLocalMoment[A,+B,+LM[AA,+BB] <: LocalMoment[AA,BB]] extends Moment[A,B] {
  def local:LM[A,B]

  override def count = local.count.future
  override def findActiveIds = local.findActiveIds.future
  override def findInactiveIds = local.findInactiveIds.future
  override def find(key: A) = local.find(key).future
  override def findVersion(key: A) = local.findVersion(key).future
  override def findRecord(key: A) =
    local.findRecord(key).future

  override def filterKeys(f: A => Boolean) =
    local.filterKeys(f).lift.future


  override def active = local.active.future
  override def inactive = local.inactive.future

  override def toMap = local.toMap.future
  override def materialize = local.materialize.future
}

object LiftedLocalMoment {
  case class LiftedLocalMomentImpl[A,B,+LM[AA,+BB] <: LocalMoment[AA,BB]](
    local:LM[A,B]
  ) extends LiftedLocalMoment[A,B,LM]

  def apply[A,B,LM[AA,+BB] <: LocalMoment[AA,BB]](self:LM[A,B]) : Moment[A,B] =
    LiftedLocalMomentImpl[A,B,LM](self)
}

