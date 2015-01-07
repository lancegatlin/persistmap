package org.lancegatlin

import org.joda.time.Instant

package object aeon {

  val beginOfTime = new Instant(0)
  val endOfTime = new Instant(Long.MaxValue)
  val allOfTime = Aeon(beginOfTime,endOfTime)

  type Checkout[A] = Map[A, Long]
  val Checkout = Map

//  // Note: using this since Interval creating an interval discards the Instant
//  // instances. Round trip from (Instant,Instant) to Interval to
//  // (Instant,Instant) means throwing away original instances, creating temp
//  // Interval and creating two new Instant instances
//  implicit class PimpMyInstantPair(val self:(Instant,Instant)) extends AnyVal {
//    def contains(other: (Instant, Instant)) : Boolean = {
//      val lhsBegin = self._1.getMillis
//      val lhsEnd = self._2.getMillis
//      val rhsBegin = other._2.getMillis
//      val rhsEnd = other._2.getMillis
//
//      lhsBegin <= rhsBegin &&
//      rhsBegin < lhsEnd &&
//      rhsEnd < lhsEnd
//    }
//    def contains(other: Instant) : Boolean = {
//      val when = other.getMillis
//      self._1.getMillis <= when && when <= self._2.getMillis
//    }
//    def toInterval : Interval = new Interval(self._1,self._2)
//  }
}
