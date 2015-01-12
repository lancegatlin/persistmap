package org.lancegatlin.aeon.diffmap

case class Commit[A,+B,+PB](
  put: Map[A,B] = Map.empty[A,B],
  replace: Map[A,PB] = Map.empty[A,PB],
  deactivate: Set[A] = Set.empty[A],
  reactivate: Map[A,B] = Map.empty[A,B]
) {
  def isNoChange : Boolean =
    put.size == 0 &&
    replace.size == 0 &&
    deactivate.size == 0 &&
    reactivate.size == 0

  def filterKeys(f: A => Boolean) : Commit[A,B,PB] = {
    copy(
      put = put.filterKeys(f),
      replace = replace.filterKeys(f),
      deactivate = deactivate.filter(f),
      reactivate = reactivate.filterKeys(f)
    )
  }
}

object Commit {
  private[this] val _noChange = Commit[Any,Nothing,Nothing]()
  def noChange[A,B,PB] : Commit[A,B,PB] = _noChange.asInstanceOf[Commit[A,B,PB]]
}
