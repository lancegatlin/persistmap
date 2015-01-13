package org.lancegatlin.aeon

import org.joda.time.{Interval, Instant}

// Note: using this since org.joda.time.Interval discards the Instant
// instances. Round trip from (Instant,Instant) to Interval to
// (Instant,Instant) means throwing away original instances, creating temp
// Interval and creating two new Instant instances
case class Aeon(
  start: Instant,
  end: Instant
) {
  def contains(other: (Instant, Instant)) : Boolean = {
    val lhsBegin = start.getMillis
    val lhsEnd = end.getMillis
    val rhsBegin = other._2.getMillis
    val rhsEnd = other._2.getMillis

    lhsBegin <= rhsBegin &&
    rhsBegin < lhsEnd &&
    rhsEnd < lhsEnd
  }
  def contains(other: Instant) : Boolean = {
    val when = other.getMillis
    start.getMillis <= when && when <= end.getMillis
  }
  def toInterval : Interval = new Interval(start,end)
}


