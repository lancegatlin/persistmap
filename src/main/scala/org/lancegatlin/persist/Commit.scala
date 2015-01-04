package org.lancegatlin.persist

case class Commit[ID,+A,+P](
  checkout: Map[ID,Long] = Map.empty[ID,Long],
  put: Map[ID,A] = Map.empty[ID,A],
  replace: Map[ID,P] = Map.empty[ID,P],
  deactivate: Set[ID] = Set.empty[ID],
  reactivate: Set[ID] = Set.empty[ID]
) {
  require(replace.keySet.forall(checkout.contains),"All changed ids must be checked out")
  require(deactivate.forall(checkout.contains),"All deactivated ids must be checked out")
  require(reactivate.forall(checkout.contains),"All reactivated ids must be checked out")
  
  def isNoChange : Boolean =
    put.size == 0 &&
    replace.size == 0 &&
    deactivate.size == 0 &&
    reactivate.size == 0 
}

class CommitBuilder[ID,A,P] {
  private[this] val _checkout = Map.newBuilder[ID,Long]
  private[this] val _put = Map.newBuilder[ID,A]
  private[this] val _replace = Map.newBuilder[ID,P]
  private[this] val _deactivate = Set.newBuilder[ID]
  private[this] val _reactivate= Set.newBuilder[ID]

  def checkout(id: ID, version: Long) = {
    _checkout.+=((id,version))
    this
  }

  def put(
    id: ID,
    value: A
  ) = {
    _put.+=((id,value))
    this
  }
  
  def replace(
    id: ID,
    version: Long,
    patch: P
  ) = {
    _checkout.+=((id,version))
    _replace.+=((id,patch))
    this
  }
  
  def deactivate(id:ID, version:Long) = {
    _checkout.+=((id,version))
    _deactivate += id
    this
  }
  
  def reactivate(id:ID, version:Long) = {
    _checkout.+=((id,version))
    _deactivate += id
    this
  }

  def result() = Commit(
    checkout = _checkout.result(),
    put = _put.result(),
    replace = _replace.result(),
    deactivate = _deactivate.result(),
    reactivate = _reactivate.result()
  )
}

object Commit {
  def newBuilder[ID,A,P] = new CommitBuilder[ID,A,P]
  private[this] val _noChange = Commit[Any,Nothing,Nothing]()
  def noChange[ID,A,P] : Commit[ID,A,P] = _noChange.asInstanceOf[Commit[ID,A,P]]
}


