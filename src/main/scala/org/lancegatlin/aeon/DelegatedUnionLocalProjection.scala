package org.lancegatlin.aeon

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