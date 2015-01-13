package org.lancegatlin.aeon.impl

import org.lancegatlin.aeon.LocalProjection

object EmptyLocalProjection extends LocalProjection[Any,Nothing] {
  override val size = 0
  override val keys = Iterable.empty
  override def find(key: Any) = None
  override def filterKeys(f: (Any) => Boolean) = this
  override val toMap = Map.empty[Any,Nothing]
}