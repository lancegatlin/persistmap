package org.lancegatlin.persist

import org.joda.time.Instant
import s_mach.concurrent._

import scala.concurrent.Future
import scala.concurrent.duration.FiniteDuration
import scala.util.Try

object PersistentMap {
  val beginTime = new Instant(0)
  val endTime = new Instant(Long.MaxValue)
  val allInterval = (beginTime,endTime)


  case class Count(
    activeRecordCount: Int,
    inactiveRecordCount: Int
  ) {
    val totalRecordCount = activeRecordCount + inactiveRecordCount
  }
}

trait PersistentMap[ID,A,P] {
  import org.lancegatlin.persist.PersistentMap._

  def findRecord(
    id: ID,
    interval: (Instant,Instant) = allInterval
  ) : Future[Option[Record[A,P]]]

  sealed trait State

  trait QueryState extends State {
    def count : Future[Count]

    def findActiveIds: Future[Set[ID]]
    def findInactiveIds: Future[Set[ID]]

    def find(id: ID) : Future[Option[A]]

    def toMap : Future[Map[ID, A]]
  }
  
  trait OldState extends QueryState {
    def when: Instant
  }

  case object NoOldState extends OldState {
    override val when = new Instant(0)
    override val count = Count(0,0).future
    override val findActiveIds = Set.empty[ID].future
    override val findInactiveIds = Set.empty[ID].future
    override def find(id: ID) = None.future
    override val toMap = Map.empty[ID,A].future
  }

  def old(when: Instant) : OldState

  abstract class NowState extends QueryState {
    def deactivate(id: ID)(implicit metadata: Metadata) : Future[Boolean]
    def reactivate(id: ID)(implicit metadata: Metadata) : Future[Boolean]

    def put(id: ID, value: A)(implicit metadata:Metadata) : Future[Boolean] =
      put(id)(_ => (value,true))
    def put[X](id: ID)(f: OldState => (A,X))(implicit metadata:Metadata) : Future[X]

    def replace(id: ID, value: A)(implicit metadata:Metadata) : Future[Boolean] =
      replace(id)(_ => (value,true))
    def replace[X](id: ID)(f: OldState => (A,X))(implicit metadata:Metadata) : Future[X]
  }

  def now : NowState

  abstract class FutureState extends State {
    def deactivate(id: ID) : FutureState
    def reactivate(id: ID) : FutureState

    def put(id: ID, value: A) : FutureState
    def replace(record: Record[A,P], value: A) : FutureState

    def commit() : Future[Try[OldState]]
  }

  def future(expire: Instant)(implicit m:Metadata) : FutureState
  def future(expire: FiniteDuration)(implicit m:Metadata) : FutureState =
    future(Instant.now().plus(expire.toMillis.toInt))


  def atomically(
    f: (OldState,FutureState) => FutureState
  ) : Future[Try[OldState]]

  def asMap : Map[ID, A]
}