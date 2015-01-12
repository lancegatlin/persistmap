//package org.lancegatlin.aeon.impl
//
//import scala.language.higherKinds
//import org.lancegatlin.aeon._
//import s_mach.concurrent._
//
//trait LiftedLocalProjection[A,+B] extends ProjectionLike[A,B] {
//  def local: LocalProjection[A,B]
//
//  override def size = local.size.future
//  override def find(key: A) = local.find(key).future
//  override def keys = local.keys.future
//  override def filterKeys(f: (A) => Boolean) : ProjectionLike[A,B] =
//    new LiftedLocalProjection[A,B] {
//      def local = local.filterKeys(f)
//    }
//  override def toMap = local.toMap.future
//}
//
//trait LiftedMapProjection[A,+B] extends ProjectionLike[A,B] {
//  def local: Map[A,B]
//
//  override def size = local.size.future
//  override def find(key: A) = local.get(key).future
//  override def keys = local.keys.future
////  override def filterKeys(f: (A) => Boolean) : Projection[A,B] =
////    new LiftedMapProjection[A,B] {
////      def local = local.filterKeys(f)
////    }
//  override def toMap = local.toMap.future
//}
//
//trait LiftedLocalMoment[A,+B,+LM[AA,+BB] <: LocalMomentLike[AA,BB]] extends MomentLike[A,B] with LiftedLocalProjection[A,B] { self =>
//  def local:LM[A,B]
//  override val active = new LiftedMapProjection[A,Record.Active[B]] {
//    def local = self.local.active
//  }
//  override val inactive = new LiftedMapProjection[A,Record.Inactive] {
//    def local = self.local.inactive
//  }
//  override val all = new LiftedMapProjection[A,Record[B]] {
//    def local = self.local.all
//  }
//
////  abstract override def filterKeys(f: A => Boolean) : Moment[A,B] ={
////    val l:LocalMoment[A,B] = local
////    val newL:LocalMoment[A,B] = l.filterKeys(f).asInstanceOf[LocalMoment[A,B]]
////    LiftedLocalMoment[A,B,LocalMoment](newL)
////  }
////    LiftedLocalMoment[A,B,LocalMoment](local.filterKeys(f))
//
//  override def materialize = local.materialize.future
//}
//
//object LiftedLocalMoment {
//  case class LiftedLocalMomentImpl[A,B,+LM[AA,+BB] <: LocalMomentLike[AA,BB]](
//    local:LM[A,B]
//  ) extends LiftedLocalMoment[A,B,LM]
//
//  def apply[A,B,LM[AA,+BB] <: LocalMomentLike[AA,BB]](self:LM[A,B]) : MomentLike[A,B] =
//    LiftedLocalMomentImpl[A,B,LM](self)
//}
//
