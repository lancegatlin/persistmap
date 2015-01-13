package org.lancegatlin.aeon

import org.lancegatlin.aeon.impl.LiftedLocalMoment

trait MaterializedMoment[A,+B] extends LocalMoment[A,B] {
  override def filterKeys(f: (A) => Boolean): MaterializedMoment[A,B]

  override def active : Map[A,Record.Materialized[B]]

  override def materialize = this
  lazy val asMoment : Moment[A,B] = LiftedLocalMoment[A,B,MaterializedMoment[A,B]](this)
}

object MaterializedMoment {
  private[this] val _empty = MaterializedMomentImpl[Any,Nothing](Map.empty)
  def empty[A,B] = _empty.asInstanceOf[MaterializedMoment[A,B]]

  case class MaterializedMomentImpl[A,B](
    active: Map[A,Record.Materialized[B]],
    inactive: Map[A,Record.Inactive] = Map.empty[A,Record.Inactive]
  ) extends DelegatedLocalProjection[A,B] with
    MaterializedMoment[A,B] {
    val delegate = active.mapValues(_.value)
    val all = new DelegatedUnionMap2[A,Record[B]] {
      def delegate1 = active
      def delegate2 = inactive
    }
    override def filterKeys(f: A => Boolean) =
      MaterializedMomentImpl(
        active = active.filterKeys(f),
        inactive = inactive.filterKeys(f)
      )
  }

  def apply[A,B](kv: (A,B)*) : MaterializedMoment[A,B] =
    MaterializedMomentImpl[A,B](
      active = kv.map { case (key,value) => (key, Record(value))}.toMap
    )

  def apply[A,B](
    active: Map[A,Record.Materialized[B]],
    inactive: Map[A,Record.Inactive]
  ) : MaterializedMoment[A,B] =
    MaterializedMomentImpl[A,B](
      active = active,
      inactive = inactive
    )
}
