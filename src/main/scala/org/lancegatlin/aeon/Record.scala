package org.lancegatlin.aeon

sealed trait Record[+A] {
  def isActive: Boolean
  def version: Long
}

object Record {
  case class Inactive(
    version: Long
  ) extends Record[Nothing] {
    override def isActive = false
  }

  sealed trait Active[+A] extends Record[A] {
    def value: A

    def materialize : Record.Materialized[A]
  }

  case class Materialized[+A](
    value: A,
    version: Long = 1
  ) extends Active[A] {
    override def isActive = true
    override def materialize = this
  }

  case class Lazy[+A]()(
    calcValue: => A,
    calcVersion: => Long
  ) extends Active[A] {
    lazy val value = calcValue
    lazy val version = calcVersion

    override def isActive = true

    def materialize = Materialized(
      value = value,
      version = version
    )
  }

  def apply[A](
    value: A,
    version: Long = 1,
    zomPrev: List[Record[A]] = Nil
  ) : Materialized[A] = Materialized(
    value = value,
    version = version
  )

  def lazyApply[A](
    calcValue: => A,
    calcVersion: => Long = 1
  ) : Lazy[A] = {
    Lazy()(
      calcValue = calcValue,
      calcVersion = calcVersion
    )
  }
}

