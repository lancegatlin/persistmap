package org.lancegatlin.aeon

import org.lancegatlin.aeon.impl.EmptyLocalProjection

trait LocalProjection[A,+B] {
  def size: Int
  def keys: Iterable[A]
  def find(key: A) : Option[B]
  def filterKeys(f: A => Boolean) : LocalProjection[A,B]
  def toMap: Map[A,B]
}

object LocalProjection {
  private[this] val _empty = EmptyLocalProjection
  def empty[A,B] = _empty.asInstanceOf[LocalProjection[Any,Nothing]]
}