package org.lancegatlin.aeon

import org.joda.time.Instant

case class Metadata(
  who: String,
  why: Option[String] = None,
  when: Instant = Instant.now(),
  extension: Map[String,String] = Map.empty
)
