package org.lancegatlin

import org.joda.time.Instant

package object aeon {

  val beginOfTime = new Instant(0)
  val endOfTime = new Instant(Long.MaxValue)
  val allOfTime = Aeon(beginOfTime,endOfTime)

  type Checkout[A] = Map[A, Long]
  val Checkout = Map
}
