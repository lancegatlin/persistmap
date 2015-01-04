package org.lancegatlin.persist

import org.joda.time.Instant
import s_mach.concurrent._
import s_mach.datadiff.DataDiff

import scala.concurrent.{ExecutionContext, Future}
import scala.concurrent.duration.FiniteDuration
import scala.util.{Failure, Success, Try}

object PersistentMap {
  private[this] val _empty : PersistentMap[Any,Any,Any] = ???
//    new PersistentMap[Any,Any,Any] {
//    override implicit def executionContext: ExecutionContext = ???
//    override def asMap: Map[, A] = ???
//
//    override def now: NowState = ???
//
//    override def base: Future[(Map[ID, A], Metadata)] = ???
//
//    override def zomCommit: Future[List[(Commit[ID, A, P], Metadata)]] = ???
//
//    override def old(when: Instant): OldState = ???
//
//    override def future(expire: Instant)(implicit m: Metadata): FutureState = ???
//
//    override implicit def dataDiff: DataDiff[A, P] = ???
//
//    override def atomically(f: (OldState, FutureState) => FutureState): Future[Try[OldState]] = ???
//  }
  def empty[ID,A,P] = _empty.asInstanceOf[PersistentMap[ID,A,P]]

  val beginTime = new Instant(0)
  val endTime = new Instant(Long.MaxValue)
  val allInterval = (beginTime,endTime)

  case class Count(
    activeRecordCount: Int,
    inactiveRecordCount: Int
  ) {
    val totalRecordCount = activeRecordCount + inactiveRecordCount
  }

  case class Record[+A](
    value: A,
    version: Long
  )
  case class BaseState[ID,+A](
    active: Map[ID,Record[A]],
    inactive: Map[ID,Record[A]]
  )

  object BaseState {
    private[this] val _empty = BaseState[Any,Nothing](Map.empty,Map.empty)
    def empty[ID,A,P] = _empty.asInstanceOf[BaseState[ID,A]]
  }
}

trait PersistentMap[ID,A,P] {
  import PersistentMap._

  implicit def executionContext: ExecutionContext
  implicit def dataDiff: DataDiff[A,P]

  def base: Future[(BaseState[ID,A], Metadata)]
  def zomCommit: Future[List[(Commit[ID,A,P], Metadata)]]

  sealed trait State

  trait QueryState extends State {
    def count : Future[Count]

    def findActiveIds: Future[Set[ID]]
    def findInactiveIds: Future[Set[ID]]

    def find(id: ID) : Future[Option[A]]

    def toMap : Future[Map[ID, A]]

    def checkout(filter: ID => Boolean) : Future[PersistentMap[ID,A,P]]
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
    override def checkout(filter: ID => Boolean) = ??? //PersistentMap.empty[ID,A,P].future
  }

  def old(when: Instant) : OldState

  abstract class NowState extends QueryState {
    def deactivate(id: ID)(implicit metadata: Metadata) : Future[Boolean]
    def reactivate(id: ID)(implicit metadata: Metadata) : Future[Boolean]

    def put(id: ID, value: A)(implicit metadata:Metadata) : Future[Boolean] =
      putFold(id)(_ => (value,true),_ => false)

    def putFold[X](id: ID)(
      f: OldState => (A,X),
      g: Exception => X
    )(implicit metadata:Metadata) : Future[X]

    def replace(id: ID, value: A)(implicit metadata:Metadata) : Future[Boolean] =
      replaceFold(id)(_ => (value,true),_ => false)

    def replaceFold[X](id: ID)(
      f: OldState => (A,X),
      g: Exception => X
    )(implicit metadata:Metadata) : Future[X]

    def commit(commit: Commit[ID,A,P])(implicit
      metadata: Metadata
    ) : Future[Boolean] =
      commitFold(_ => (commit,true), _ => false)

    def commitFold[X](
      f: OldState => (Commit[ID,A,P],X),
      g: Exception => X
    )(implicit metadata: Metadata) : Future[X]

    def merge(
      other: PersistentMap[ID,A,P]
    )(implicit metadata: Metadata) : Future[Boolean] =
      mergeFold(_ => (other,true),_ => false)

    def mergeFold[X](
      f: OldState => (PersistentMap[ID,A,P],X),
      g: Exception => X
    )(implicit metadata: Metadata) : Future[X]
  }

  def now : NowState

  abstract class FutureState extends QueryState {
    def deactivate(id: ID) : FutureState
    def reactivate(id: ID) : FutureState

    def put(id: ID, value: A) : FutureState
    def replace(id: ID, value: A) : FutureState

    def commit() : Future[Try[OldState]]
  }

  def future(expire: Instant)(implicit m:Metadata) : FutureState
  def future(expire: FiniteDuration)(implicit m:Metadata) : FutureState =
    future(Instant.now().plus(expire.toMillis.toInt))

//  def merge(other: PersistentMap[ID,A,P]) : Future[Boolean]

  def atomically(
    f: (OldState,FutureState) => FutureState
  ) : Future[Try[OldState]]

  def asMap : Map[ID, A]
}