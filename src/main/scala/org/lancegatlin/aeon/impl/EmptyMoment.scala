package org.lancegatlin.aeon.impl

import org.lancegatlin.aeon.{Record, MaterializedMoment, Moment}
import s_mach.concurrent._

import scala.concurrent.Future

trait EmptyMoment extends Moment[Any,Nothing] {
  override val count = (0,0).future
  override val findActiveIds = Iterable.empty[Any].future
  override val findInactiveIds = Iterable.empty[Any].future
  override def find(key: Any) = None.future
  override def findVersion(key: Any) = None.future
  override def findRecord(key: Any) = None.future
  override def filter(f: (Any, Boolean) => Boolean) = this.future
  override val active = Iterable.empty[(Any, Record[Nothing])].future
  override val inactive = Iterable.empty[(Any, Record[Nothing])].future
  override val toMap = Map.empty[Any,Nothing].future
  override val materialize: Future[MaterializedMoment[Any, Nothing]] =
    MaterializedMoment.empty[Any,Nothing].future
}
