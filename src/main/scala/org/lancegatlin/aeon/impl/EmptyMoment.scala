package org.lancegatlin.aeon.impl

import org.lancegatlin.aeon._
import s_mach.concurrent._

import scala.concurrent.Future

object EmptyProjection extends Projection[Any,Nothing] {
  override val size = 0.future
  override val keys = Iterable.empty.future
  override def find(key: Any) = None.future
  override def filterKeys(f: (Any) => Boolean) : Projection[Any,Nothing] = this
  override val toMap = Map.empty[Any,Nothing].future
}

object EmptyMoment extends Moment[Any,Nothing] with DelegatedProjection[Any,Nothing] {
  val delegate = EmptyProjection
  val active = EmptyProjection
  val inactive = EmptyProjection
  val all = EmptyProjection

  override def filterKeys(f: Any => Boolean) : Moment[Any,Nothing] = this

  override val materialize: Future[MaterializedMoment[Any, Nothing]] =
    MaterializedMoment.empty[Any,Nothing].future
}
