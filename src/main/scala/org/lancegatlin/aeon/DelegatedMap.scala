package org.lancegatlin.aeon

import scala.collection.immutable.{DefaultMap, AbstractMap}

abstract class DelegatedMap[A,+B] extends AbstractMap[A,B] with DefaultMap[A,B] { self =>
  def delegate: Map[A,B]

  override def foreach[C](f: ((A, B)) => C) = delegate.foreach(f)
  def iterator = delegate.iterator
  override def size = delegate.size
  override def contains(key: A) = delegate.contains(key)
  def get(key: A) = delegate.get(key)
}

abstract class DelegatedUnionMap2[A,+B] extends AbstractMap[A,B] with DefaultMap[A,B] { self =>
  def delegate1: Map[A,B]
  def delegate2: Map[A,B]

  override def foreach[C](f: ((A, B)) => C) = {
    delegate1.foreach(f)
    delegate2.foreach(f)
  }
  def iterator = delegate1.iterator ++ delegate2.iterator
  override def size = delegate1.size + delegate2.size
  override def contains(key: A) = delegate1.contains(key) || delegate2.contains(key)
  def get(key: A) = delegate1.get(key) orElse delegate2.get(key)
  override lazy val keys = delegate1.keys ++ delegate2.keys
}

