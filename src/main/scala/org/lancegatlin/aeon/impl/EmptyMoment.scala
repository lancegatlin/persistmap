package org.lancegatlin.aeon.impl

import org.lancegatlin.aeon._
import s_mach.concurrent._

import scala.concurrent.Future

object EmptyMoment extends Moment[Any,Nothing] with DelegatedProjection[Any,Nothing] {
  val delegate = EmptyProjection
  val active = EmptyProjection
  val inactive = EmptyProjection
  val all = EmptyProjection

  override def filterKeys(f: Any => Boolean) = this

  override val materialize: Future[MaterializedMoment[Any, Nothing]] =
    MaterializedMoment.empty[Any,Nothing].future
}
