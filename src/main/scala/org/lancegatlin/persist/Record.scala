package org.lancegatlin.persist

import org.joda.time.{Instant, Interval}
import s_mach.datadiff._

object Record {
  def apply[A,P](
    value: A,
    interval: (Instant,Instant),
    metadata: Metadata
  )(implicit
    dataDiff:DataDiff[A,P]
  ) : Record[A,P] =
    Record(
      base = Base(
        value = value,
        isActive = true,
        version = 1,
        metadata = metadata
      ),
      oomEvent = Event.Put[A,P](value,metadata) :: Nil,
      interval = interval
    )

  case class Base[A](
    value : A, 
    isActive: Boolean,
    version: Long,
    metadata: Metadata
  )
}

case class Record[A,P](
  base: Record.Base[A],
  oomEvent: List[Event[A,P]],
  interval: (Instant,Instant)
)(implicit dataDiff: DataDiff[A,P]) {
  import org.lancegatlin.persist.Event._

  def calcNewerBase(when: Instant) : Record.Base[A] = {
    // Is requested when more recent than start of record interval?
    if(when.getMillis >= interval._1.getMillis) {
      var active = base.isActive
      var newValue = base.value
      var lastMetadata = base.metadata

      // Note: this loop is a fold right and uses the stack to avoid reversing the list
      def loop(oomEvent: List[Event[A,P]]) : Unit = {
        // TODO: filtering events here can be done more eff by using the halving method
        oomEvent match {
          case Nil =>
          case head :: tail =>
            loop(tail)
            if(
              head.metadata.when.isBefore(when) ||
              head.metadata.when.getMillis == when.getMillis
            ) {
              head match {
                case Put(_,_) =>
                case Replace(patch,metadata) =>
                  newValue = newValue applyPatch patch
                  lastMetadata = metadata
                case Deactivate(metadata) =>
                  active = false
                  lastMetadata = metadata
                case Reactivate(metadata) =>
                  active = true
                  lastMetadata = metadata
                case Touch(metadata) =>
                  lastMetadata = metadata
              }
            }
        }
      }
      loop(oomEvent)
      val newVersion = base.version + oomEvent.size
      Record.Base(
          value = newValue,
          isActive = active,
          version = newVersion,
          metadata = lastMetadata
      )
    } else {
      // If not, there are no events earlier than record start so no newer base is possible
      base
    }
  }

  lazy val mostRecentBase = calcNewerBase(interval._2)
  def value : A = mostRecentBase.value
  def isActive: Boolean = mostRecentBase.isActive
  def metadata: Metadata = mostRecentBase.metadata
  def version : Long = mostRecentBase.version

  def isFullRecord : Boolean = interval == PersistentMap.allInterval

  def mkSubRecord(subInterval: (Instant,Instant)) : Record[A,P] = {
    def contains(lhs: (Instant,Instant), rhs: (Instant, Instant)) : Boolean = {
      val lhsStart = lhs._1.getMillis
      val lhsEnd = lhs._2.getMillis
      val rhsStart = rhs._2.getMillis
      val rhsEnd = rhs._2.getMillis

      lhsStart <= rhsStart &&
      rhsStart < lhsEnd &&
      rhsEnd < lhsEnd
    }
    if (interval != subInterval && contains(interval,subInterval)) {
      val _interval = new Interval(interval)
      val newBase = calcNewerBase(interval._1)
      Record(
        base = newBase,
        oomEvent = oomEvent.filter(e => _interval.contains(e.metadata.when)),
        interval = interval
      )
    } else {
      this
    }
  }

  def append(event: Event[A,P]) : Record[A,P] = {
    val now = Instant.now()
    copy(
      oomEvent = event :: oomEvent,
      interval = if(interval._2.getMillis < now.getMillis) (interval._1,now) else interval
    )
  }
  
  def appendReplaceOrTouch(
    latest: A,
    touchIfNoChange: Boolean,
    metadata: Metadata
  ) : Record[A,P] = {
    value calcDiff latest match {
      case dataDiff.noChange =>
        if(touchIfNoChange) {
          appendTouch(metadata)
        } else {
          this
        }
      case patch =>
        appendReplace(patch,metadata)
    }
  }

  def appendReplace(
    patch: P,
    metadata: Metadata
  ) : Record[A,P] = {
    require(patch != dataDiff.noChange)
    append(Replace(patch,metadata))
  }
    
  def appendDeactivate(metadata: Metadata) : Record[A,P] = {
    if(isActive) {
      append(Deactivate(metadata))
    } else {
      this
    }
  }

  def appendReactivate(metadata: Metadata) : Record[A,P] = {
    if(isActive) {
      this
    } else {
      append(Reactivate(metadata))
    }
  }

  def appendTouch(metadata: Metadata) : Record[A,P] = {
    append(Touch(metadata))
  }

}