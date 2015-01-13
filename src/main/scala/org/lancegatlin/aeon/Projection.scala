package org.lancegatlin.aeon

import org.lancegatlin.aeon.impl.EmptyProjection

import scala.concurrent.Future

trait Projection[A,+B] {
  // Operations
  def size: Future[Int]
  def find(key: A) : Future[Option[B]]
  //def find(key: A*) : Future[Option[B]]
  // def findOrElse

  // * Operations
  def keys: Future[Iterable[A]]
  // def keySet
  // def keysIterator

  def toMap: Future[Map[A,B]]
  // def iterator
  // def aggregate
  // def reduce
  // def reduceOption
  // def scan
  // def collect
  // def collectFirst
  // def copyToArray
  // def copyToBuffer
  // def count
  // def exists
  // def equals
  // def sameElements
  // def map
  // def flatMap
  // def fold
  // def forall
  // def foreach *can't cluster compute side effects
  // def groupBy
  // def isEmpty
  // def nonEmpty
  // def max/min
  // def maxBy/minBy
  // def sum
  // def values
  // def valuesIterator
  // def unzip
  // def transform

  // def toArray
  // def toBuffer
  // toIndexedSeq
  // toIterable
  // toIterator
  // toList
  // toMap
  // toSeq
  // toSet
  // toStream
  // toTraversable
  // toVector

  // Query modifiers
  def filterKeys(f: A => Boolean) : Projection[A,B]
  // def filter
  // def filterNot
  // def mapValues

}

object Projection {
  private[this] val _empty = EmptyProjection
  def empty[A,B] = _empty.asInstanceOf[Projection[A,B]]
}