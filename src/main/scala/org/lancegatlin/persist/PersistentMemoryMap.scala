package org.lancegatlin.persist

import java.util.concurrent.ConcurrentSkipListMap

import org.joda.time.Instant
import s_mach.concurrent._
import s_mach.datadiff._

import scala.collection.mutable
import scala.concurrent.{ExecutionContext, Future}
import scala.util.Try

object PersistentMemoryMap {
  def apply[ID,A,P](kv: (ID,A)*)(implicit
    ec: ExecutionContext,
    dataDiff:DataDiff[A,P],
    m: Metadata
    ) = new PersistentMemoryMap[ID,A,P](m, kv:_*)
}

class PersistentMemoryMap[ID,A,P](initialMetadata: Metadata, kv: (ID,A)*)(implicit
  ec: ExecutionContext,
  val dataDiff:DataDiff[A,P]
) extends
  PersistentMap[ID,A,P] {
  import org.lancegatlin.persist.PersistentMap._

  // TODO: create derived ConcurrentSkipListMap that can perform atomic ops to avoid locking below
  val whenToOldState = {
    val initial = new java.util.HashMap[Long,_OldState]()
    val when = Instant.now()
    val initialState = _OldState(
      when = when,
      active = kv.toMap,
      inactive = Map.empty,
      oomEvent = kv.map { case (k,v) =>
        (k,Event.Put[A,P](v,initialMetadata))
      }.toList
    )
    initial.put(when.getMillis,initialState)
    new ConcurrentSkipListMap[Long,_OldState](initial)
  }

  val idToRecord : mutable.Map[ID,Record[A,P]] = {
    mutable.Map(
      kv.map { case (k,v) =>
        (k,Record[A,P](v,allInterval,initialMetadata))
      }:_*
    )
  }

  def print : String = {
    import scala.collection.JavaConverters._
    val sb = new StringBuilder
    sb.append("whenToOldState\n")
    whenToOldState.descendingMap().entrySet().asScala.foreach { entry =>
      sb.append(s"${new Instant(entry.getKey)} => ${entry.getValue}\n")
    }
    sb.append("idToRecord\n")
    idToRecord.foreach { case (id, record) =>
      sb.append(s"$id => $record\n")
    }
    sb.result()
  }

  override def findRecord(id: ID, interval: (Instant,Instant)) : Future[Option[Record[A,P]]] = {
    idToRecord.get(id).map(_.mkSubRecord(interval))
  }.future

  case class _OldState(
    when: Instant,
    active: Map[ID,A],
    inactive: Map[ID,A],
    oomEvent: List[(ID,Event[A,P])]
  ) extends OldState {
    override def find(id: ID): Future[Option[A]] =
      active.get(id).future

    override def count: Future[Count] =
      Count(
        activeRecordCount = active.size,
        inactiveRecordCount = inactive.size
      ).future

    override def findActiveIds: Future[Set[ID]] =
      active.keys.toSet.future

    override def findInactiveIds: Future[Set[ID]] =
      inactive.keys.toSet.future

    override def toMap: Future[Map[ID, A]] =
      active.future
  }

  override def old(when: Instant) = {
    val k = whenToOldState.floorKey(when.getMillis)
    whenToOldState.get(k) match {
      case null => NoOldState
      case oldState => oldState
    }
  }

  def merge(oomEvent: List[(ID,Event[A,P])]) : Unit = {
    idToRecord.synchronized {
      // Merge events from newState to idToRecord
      oomEvent.foreach {
        case (id,Event.Put(value,metadata)) =>
          idToRecord.put(id,Record(value,allInterval,metadata))
        case (id,Event.Replace(patch,metadata)) =>
          val oldRecord = idToRecord(id)
          val newRecord = oldRecord.appendReplace(patch,metadata)
          idToRecord.update(id,newRecord)
        case (id,Event.Deactivate(metadata)) =>
          val oldRecord = idToRecord(id)
          val newRecord = oldRecord.appendDeactivate(metadata)
          idToRecord.update(id,newRecord)
        case (id,Event.Reactivate(metadata)) =>
          val oldRecord = idToRecord(id)
          val newRecord = oldRecord.appendReactivate(metadata)
          idToRecord.update(id,newRecord)
        case (id,Event.Touch(metadata)) =>
          val oldRecord = idToRecord(id)
          val newRecord = oldRecord.appendTouch(metadata)
          idToRecord.update(id,newRecord)
      }
    }
  }

  override def now = {
    def setState[X](
      f: (Instant,_OldState) => (_OldState,X)
    ) : Future[X] = {
      def loop() : X = {
        val lastEntry = whenToOldState.lastEntry
        val nextWhen = Instant.now()
        val currentState = lastEntry.getValue
        val (newState, x) = f(nextWhen, currentState)
        if(newState eq currentState) {
          x
        } else {
          // Note: need synchronized here to ensure no new states are inserted during check-execute below
          // lock time has been minimized
          val isSuccess =
            whenToOldState.synchronized {
              if (lastEntry.getKey == whenToOldState.lastEntry.getKey) {
                whenToOldState.put(nextWhen.getMillis, newState)
                merge(newState.oomEvent)
                true
              } else {
                false
              }
            }
          if(isSuccess) {
            x
          } else {
            loop()
          }
        }
      }
      Future { loop() }
    }


    new NowState {
      def currentState = whenToOldState.lastEntry.getValue

      override def count = currentState.count

      override def toMap = currentState.toMap

      override def findActiveIds = currentState.findActiveIds

      override def find(id: ID) = currentState.find(id)

      override def findInactiveIds = currentState.findInactiveIds

      override def put[X](id: ID)(f: OldState => (A,X))(implicit metadata: Metadata): Future[X] = {
        setState { (when, state) =>
          val (value,x) = f(state)
          val newState =
            state.copy(
              when = when,
              active = state.active + ((id,value)),
              inactive = state.inactive - id,
              oomEvent = (id,Event.Put[A,P](value,metadata.copy(when=when))) :: Nil
            )
          (newState, x)
        }
      }

      override def replace[X](id: ID)(f: OldState => (A,X))(implicit metadata: Metadata): Future[X] = {
        setState { (when, state) =>
          val oldValue = state.active(id)
          val (newValue,x) = f(state)
          val patch = oldValue calcDiff newValue
          val newState =
            state.copy(
              when = when,
              active = state.active + ((id,newValue)),
              inactive = state.inactive - id,
              oomEvent = (id,Event.Replace[A,P](patch,metadata.copy(when=when))) :: Nil
            )
          (newState, x)
        }
      }

      override def reactivate(id: ID)(implicit metadata: Metadata): Future[Boolean] = {
        setState { (when,state) =>
          state.inactive.get(id) match {
            case Some(a) =>
              val newState =
                state.copy(
                  when = when,
                  inactive = state.inactive - id,
                  active = state.active + ((id,a)),
                  oomEvent = (id,Event.Reactivate[A,P](metadata.copy(when=when))) :: Nil
                )

              (newState, true)
            case None =>
              (state, false)
          }
        }
      }

      override def deactivate(id: ID)(implicit metadata: Metadata): Future[Boolean] = {
        setState { (when,state) =>
          state.active.get(id) match {
            case Some(a) =>
              val newState =
                state.copy(
                  when = when,
                  inactive = state.inactive + ((id,a)),
                  active = state.active - id,
                  oomEvent = (id,Event.Deactivate[A,P](metadata.copy(when=when))) :: Nil
                )

              (newState, true)
            case None =>
              (state, false)
          }
        }
      }

    }
  }

  override def future(when: Instant)(implicit m: Metadata): FutureState = ???

  override def atomically(f: (OldState, FutureState) => FutureState): Future[Try[OldState]] = ???

  override def asMap: Map[ID, A] = ???
}
