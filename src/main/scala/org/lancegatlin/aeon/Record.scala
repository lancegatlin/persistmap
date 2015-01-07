package org.lancegatlin.aeon

sealed trait Record[+A] {
  def value: A
  def isActive: Boolean
  def version: Long

  def materialize : Record.Materialized[A]
}

object Record {
  case class Materialized[+A](
    value: A,
    isActive: Boolean = true,
    version: Long = 1
  ) extends Record[A] {
    override def materialize = this
  }

  case class Lazy[+A](
    isActive: Boolean = true
  )(
    calcValue: => A,
    calcVersion: => Long
  ) extends Record[A] {
    lazy val value = calcValue
    lazy val version = calcVersion

    def materialize = Materialized(
      value = value,
      isActive = isActive,
      version = version
    )
  }

  def apply[A](
    value: A,
    isActive: Boolean = true,
    version: Long = 1,
    zomPrev: List[Record[A]] = Nil
  ) : Materialized[A] = Materialized(
    value = value,
    isActive = isActive,
    version = version
  )

  def lazyApply[A](
    calcValue: => A,
    isActive: Boolean = true,
    calcVersion: => Long = 1
  ) : Lazy[A] = {
    Lazy(
      isActive = isActive
    )(
      calcValue = calcValue,
      calcVersion = calcVersion
    )
  }
}

