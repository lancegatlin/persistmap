package org.lancegatlin.persist

import org.joda.time.Instant

case class Metadata(
  who: String,
  why: Option[String] = None,
  when: Instant = Instant.now()
)
