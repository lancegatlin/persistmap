package org.lancegatlin.aeon

trait DelegatedProjection[A,+B] extends Projection[A,B] {
  def delegate: Projection[A,B]

  override def size = delegate.size
  override def keys = delegate.keys
  override def find(key: A) = delegate.find(key)
  override def toMap = delegate.toMap
}