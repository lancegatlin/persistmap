//package org.lancegatlin.persist
//
//import PersistentMap.Record
//import s_mach.concurrent._
//
//abstract class LazyPersistentMap[A,B](
//  zomId: Set[A],
//  active: Map[A,Record.Lazy[B]],
//  inactive: Map[A,Record.Lazy[B]] = Map.empty[A,Record.Lazy[B]]
//) extends PersistentMap[A,B] {
//  override def count = (active.size,inactive.size).future
//  override def findIds =
//  override def findActiveIds = active.keys.future
//  override def findInactiveIds = inactive.keys.future
//  override def find(key: A) = active.get(key).map(_.value).future
//  override def findVersion(key: A) = active.get(key).map(_.version).future
//  override def findRecord(key: A) =
//    (active.get(key) orElse inactive.get(key)).future
//  override def toSubMap(f: A => Boolean) =
//    active.filterKeys(f).mapValues(_.value).future
//  override def toMap = active.mapValues(_.value).future
//}
