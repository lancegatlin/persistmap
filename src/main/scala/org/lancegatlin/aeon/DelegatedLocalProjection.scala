package org.lancegatlin.aeon

trait DelegatedLocalProjection[A,+B] extends LocalProjection[A,B] { self =>
  def delegate: Map[A,B]

  override def size = delegate.size
  override def keys = delegate.keys
  override def find(key: A) = delegate.get(key)
  override def toMap = delegate
}

object DelegatedLocalProjection {
  case class DelegatedLocalProjectionImpl[A,B](
    delegate: Map[A,B]
  ) extends DelegatedLocalProjection[A,B] {
    override def filterKeys(f: (A) => Boolean) =
      DelegatedLocalProjection(delegate.filterKeys(f))
  }
  def apply[A,B](delegate: Map[A,B]) : DelegatedLocalProjection[A,B] =
    DelegatedLocalProjectionImpl(delegate)
}

