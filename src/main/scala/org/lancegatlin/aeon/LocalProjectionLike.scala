package org.lancegatlin.aeon

trait LocalProjection[A,+B] {
  def size: Int
  def keys: Iterable[A]
  def find(key: A) : Option[B]
  def filterKeys(f: A => Boolean) : LocalProjection[A,B]
  // def mapValues[C,BB >: B](f: BB => C) : Aspect[A,C]

  // def aggregate[C,BB >: B](z: => C)(seqop: (C, (A,BB)) => C, combop: (C,C) => C) : Future[C]
  // def reduce[BB >: B](f: (BB,BB) => BB) : Future[BB]
  def toMap: Map[A,B]
}

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

trait DelegatedUnionLocalProjection[A,+B] extends LocalProjection[A,B] { self =>
  def delegate1: Map[A,B]
  def delegate2: Map[A,B]

  override def size = delegate1.size + delegate2.size
  override lazy val keys = delegate1.keys ++ delegate2.keys
  override def find(key: A) = delegate1.get(key) orElse delegate2.get(key)
  override def toMap = delegate1 ++ delegate2
}

object DelegatedUnionLocalProjection {
  case class DelegatedUnionLocalProjectionImpl[A,B](
    delegate1: Map[A,B],
    delegate2: Map[A,B]
  ) extends DelegatedUnionLocalProjection[A,B] {
    override def filterKeys(f: (A) => Boolean) =
      DelegatedUnionLocalProjectionImpl(
        delegate1 = delegate1.filterKeys(f),
        delegate2 = delegate2.filterKeys(f)
      )
  }
  def apply[A,B](
    delegate1: Map[A,B],
    delegate2: Map[A,B]
  ) : DelegatedUnionLocalProjection[A,B] =
    DelegatedUnionLocalProjectionImpl(delegate1, delegate2)
}
