package org.lancegatlin.persist

class CommitBuilder[A,B,PB] {
  private[this] val _checkout = Map.newBuilder[A,Long]
  private[this] val _put = Map.newBuilder[A,B]
  private[this] val _replace = Map.newBuilder[A,PB]
  private[this] val _deactivate = Set.newBuilder[A]
  private[this] val _reactivate= Set.newBuilder[A]

  def checkout(key: A, version: Long) = {
    _checkout.+=((key,version))
    this
  }

  def put(
    key: A,
    value: B
  ) = {
    _put.+=((key,value))
    this
  }

  def replace(
    key: A,
    version: Long,
    patch: PB
  ) = {
    _checkout.+=((key,version))
    _replace.+=((key,patch))
    this
  }

  def deactivate(key:A, version:Long) = {
    _checkout.+=((key,version))
    _deactivate += key
    this
  }

  def reactivate(key:A, version:Long) = {
    _checkout.+=((key,version))
    _deactivate += key
    this
  }

  def result() : (Checkout[A], Commit[A,B,PB]) = {
    val checkout = _checkout.result()
    val put = _put.result()
    val replace = _replace.result()
    val deactivate = _deactivate.result()
    val reactivate = _reactivate.result()
    require(replace.keySet.forall(checkout.contains),"All changed ids must be checked out")
    require(deactivate.forall(checkout.contains),"All deactivated ids must be checked out")
    require(reactivate.forall(checkout.contains),"All reactivated ids must be checked out")

    (
      checkout,
      Commit(
        put = put,
        replace = replace,
        deactivate = deactivate,
        reactivate = reactivate
      )
    )
  }
}

