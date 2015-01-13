package org.lancegatlin.aeon.impl

import s_mach.concurrent._
import org.lancegatlin.aeon.Projection

object EmptyProjection extends Projection[Any,Nothing] {
  override val size = 0.future
  override val keys = Iterable.empty.future
  override def find(key: Any) = None.future
  override def filterKeys(f: (Any) => Boolean) : Projection[Any,Nothing] = this
  override val toMap = Map.empty[Any,Nothing].future
}