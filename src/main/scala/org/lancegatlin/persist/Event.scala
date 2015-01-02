package org.lancegatlin.persist

sealed trait Event[A,P] extends Product {
  def metadata: Metadata
}

object Event {
  case class Put[A,P](
    value: A,
    metadata: Metadata 
  ) extends Event[A,P]
  object Put {
    val symbol = 'Put
  }
  case class Replace[A,P](
    patch: P,
    metadata: Metadata
  ) extends Event[A,P]
  object Replace {
    val symbol = 'Replace
  }
  case class Touch[A,P](
    metadata: Metadata
  ) extends Event[A,P]
  object Touch {
    val symbol = 'Touch
  }
  case class Deactivate[A,P](
    metadata: Metadata
  ) extends Event[A,P]
  object Deactivate {
    val symbol = 'Deactivate
  }
  case class Reactivate[A,P](
    metadata: Metadata
  ) extends Event[A,P]
  object Reactivate {
    val symbol = 'Reactivate
  }

  def apply[A,P](
    _type: Symbol,
    optIDValue: Option[A],
    optPatch: Option[P],
    metadata: Metadata
  ) : Event[A,P] = {
    (_type,optIDValue,optPatch) match {
      case (Put.symbol, Some(value), None) => Put(value,metadata)
      case (Replace.symbol, None, Some(patch)) => Replace(patch,metadata)
      case (Deactivate.symbol, None, None) => Deactivate(metadata)
      case (Reactivate.symbol, None, None) => Reactivate(metadata)
      case (Touch.symbol, None, None) => Touch(metadata)
      case invalid => throw new UnsupportedOperationException(
        s"Can't create Event from $invalid!"
      )
    }
  }

  def unapply[A,P](
    e: Event[A,P]
  ) : Option[(Symbol,Option[A],Option[P],Metadata)] = Some { e match {
    case Put(value,metadata) => (Put.symbol,Some(value),None,metadata)
    case Replace(patch,metadata) => (Replace.symbol, None, Some(patch), metadata)
    case Deactivate(metadata) => (Deactivate.symbol,None,None,metadata)
    case Reactivate(metadata) => (Reactivate.symbol,None,None,metadata)
    case Touch(metadata) => (Touch.symbol,None,None,metadata)
  }}
}