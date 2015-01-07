package org.lancegatlin.persist

case class Commit[A,+B,+PB](
  put: Map[A,B] = Map.empty[A,B],
  replace: Map[A,PB] = Map.empty[A,PB],
  deactivate: Set[A] = Set.empty[A],
  reactivate: Set[A] = Set.empty[A]
) {
  def isNoChange : Boolean =
    put.size == 0 &&
    replace.size == 0 &&
    deactivate.size == 0 &&
    reactivate.size == 0
}

object Commit {
  def newBuilder[A,B,PB] = new CommitBuilder[A,B,PB]
  private[this] val _noChange = Commit[Any,Nothing,Nothing]()
  def noChange[A,B,PB] : Commit[A,B,PB] = _noChange.asInstanceOf[Commit[A,B,PB]]
}
