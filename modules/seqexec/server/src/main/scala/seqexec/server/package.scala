// Copyright (c) 2016-2019 Association of Universities for Research in Astronomy, Inc. (AURA)
// For license information see LICENSE or https://opensource.org/licenses/BSD-3-Clause

package seqexec

import cats.data._
import cats.effect.IO
import cats.effect.LiftIO
import cats.implicits._
import cats.Eq
import cats.Endo
import cats.MonadError
import cats.~>
import edu.gemini.spModel.`type`.SequenceableSpType
import edu.gemini.spModel.guide.StandardGuideOptions
import fs2.concurrent.Queue
import fs2.Stream
import gem.Observation
import monocle.macros.Lenses
import monocle.Lens
import monocle.Optional
import monocle.macros.GenLens
import monocle.function.Index._
import monocle.function.At._
import seqexec.engine.Engine
import seqexec.engine.Result.PartialVal
import seqexec.engine.Result.RetVal
import seqexec.engine.Result.PauseContext
import seqexec.engine.Result
import seqexec.model.ClientId
import seqexec.model.CalibrationQueueId
import seqexec.model.CalibrationQueueName
import seqexec.model.QueueId
import seqexec.model.Conditions
import seqexec.model.Observer
import seqexec.model.Operator
import seqexec.model.SequenceState
import seqexec.model.BatchCommandState
import seqexec.model.enum._
import seqexec.model.Notification
import seqexec.model.UserDetails
import seqexec.model.dhs.ImageFileId
import seqexec.model.StepId
import seqexec.engine.Event
import seqexec.engine.Handle
import seqexec.engine.Sequence
import seqexec.server.SequenceGen.StepGen
import squants.Time

package server {
  @Lenses
  final case class EngineState(queues: ExecutionQueues, selected: Map[Instrument, Observation.Id], conditions: Conditions, operator: Option[Operator], sequences: Map[Observation.Id, SequenceData[IO]])

  // TODO EngineState extending Engine.State is problematic when trying to remove the strong IO dependency
  object EngineState extends Engine.State[EngineState]{
    val default: EngineState =
      EngineState(
        Map(CalibrationQueueId -> ExecutionQueue.init(CalibrationQueueName)),
        Map.empty,
        Conditions.Default,
        None, Map.empty)

    def instrumentLoadedL(
      instrument: Instrument
    ): Lens[EngineState, Option[Observation.Id]] =
      GenLens[EngineState](_.selected) ^|-> at(instrument)

    def atSequence(sid:Observation.Id): Optional[EngineState, SequenceData[IO]] =
      EngineState.sequences ^|-? index(sid)

    override def sequenceStateIndex(
      sid: Observation.Id
    ): Optional[EngineState, Sequence.State[IO]] =
      atSequence(sid) ^|-> SequenceData.seq

    implicit final class WithEventOps(val f: Endo[EngineState]) extends AnyVal {
      def withEvent(ev: SeqEvent): EngineState => (EngineState, SeqEvent) = f >>> {(_, ev)}
    }
  }

  sealed trait SeqEvent extends Product with Serializable

  object SeqEvent {
    final case class SetOperator(name: Operator, user: Option[UserDetails]) extends SeqEvent
    final case class SetObserver(id: Observation.Id, user: Option[UserDetails], name: Observer) extends SeqEvent
    final case class SetConditions(conditions: Conditions, user: Option[UserDetails]) extends SeqEvent
    final case class LoadSequence(sid: Observation.Id) extends SeqEvent
    final case class UnloadSequence(id: Observation.Id) extends SeqEvent
    final case class AddLoadedSequence(instrument: Instrument, sid: Observation.Id, user: UserDetails, clientId: ClientId) extends SeqEvent
    final case class ClearLoadedSequences(user: Option[UserDetails]) extends SeqEvent
    final case class SetImageQuality(iq: ImageQuality, user: Option[UserDetails]) extends SeqEvent
    final case class SetWaterVapor(wv: WaterVapor, user: Option[UserDetails]) extends SeqEvent
    final case class SetSkyBackground(wv: SkyBackground, user: Option[UserDetails]) extends SeqEvent
    final case class SetCloudCover(cc: CloudCover, user: Option[UserDetails]) extends SeqEvent
    final case class NotifyUser(memo: Notification, clientID: ClientId) extends SeqEvent
    final case class StartQueue(qid: QueueId, clientID: ClientId) extends SeqEvent
    final case class StopQueue(qid: QueueId, clientID: ClientId) extends SeqEvent
    final case class UpdateQueueAdd(qid: QueueId, seqs: List[Observation.Id]) extends SeqEvent
    final case class UpdateQueueRemove(qid: QueueId, seqs: List[Observation.Id], pos: List[Int]) extends SeqEvent
    final case class UpdateQueueMoved(qid: QueueId, cid: ClientId, oid: Observation.Id, pos: Int) extends SeqEvent
    final case class UpdateQueueClear(qid: QueueId) extends SeqEvent
    final case class StartSysConfig(sid: Observation.Id, stepId: StepId, res: Resource) extends SeqEvent
    final case class Busy(sid: Observation.Id, cid: ClientId) extends SeqEvent
    final case class SequenceStart(sid: Observation.Id, stepId: StepId) extends SeqEvent
    final case class ResourceBusy(sid: Observation.Id, stepId: StepId, res: Resource, clientID: ClientId) extends SeqEvent
    case object NullSeqEvent extends SeqEvent
  }

  sealed trait ControlStrategy extends Product with Serializable
  object ControlStrategy {
    // System will be fully controlled by Seqexec
    case object FullControl extends ControlStrategy
    // Seqexec connects to system, but only to read values
    case object ReadOnly extends ControlStrategy
    // All system interactions are internally simulated
    case object Simulated extends ControlStrategy

    def fromString(v: String): Option[ControlStrategy] = v match {
      case "full"      => Some(FullControl)
      case "readOnly"  => Some(ReadOnly)
      case "simulated" => Some(Simulated)
      case _           => None
    }

    implicit val eq: Eq[ControlStrategy] = Eq.fromUniversalEquals
  }

  final case class HeaderExtraData(conditions: Conditions, operator: Option[Operator], observer: Option[Observer])
  object HeaderExtraData {
    val default: HeaderExtraData = HeaderExtraData(Conditions.Default, None, None)
  }

  sealed trait Response extends RetVal
  object Response {

    final case class Configured(resource: Resource) extends Response

    final case class Observed(fileId: ImageFileId) extends Response

    object Ignored extends Response

  }

  final case class FileIdAllocated(fileId: ImageFileId) extends PartialVal
  final case class RemainingTime(self: Time) extends AnyVal
  final case class Progress(total: Time, remaining: RemainingTime) extends PartialVal {
    val progress: Time = total - remaining.self
  }

  final case class ObserveContext[F[_]](t: ObserveCommandResult => F[Result[F]], expTime: Time) extends PauseContext[F]

}

package object server {
  implicit def geEq[D <: SequenceableSpType]: Eq[D] =
    Eq[String].contramap(_.sequenceValue())

  implicit val sgoEq: Eq[StandardGuideOptions.Value] =
    Eq[Int].contramap(_.ordinal())

  type TrySeq[A]                 = Either[SeqexecFailure, A]

  object TrySeq {
    def apply[A](a: A): TrySeq[A]              = Either.right(a)
    def fail[A](p:  SeqexecFailure): TrySeq[A] = Either.left(p)
  }

  type ExecutionQueues = Map[QueueId, ExecutionQueue]

  // TODO move this out of being a global. This act as an anchor to the rest of the code
  val executeEngine: Engine[EngineState, SeqEvent] = new Engine[EngineState, SeqEvent](EngineState)

  type EventQueue[F[_]] = Queue[F, executeEngine.EventType]

  implicit class StreamIOOps[A](s: Stream[IO, A]) {
    def streamLiftIO[F[_]: LiftIO]: fs2.Stream[F, A] =
      s.translate(λ[IO ~> F](_.to))
  }

  implicit class EitherTFailureOps[F[_]: MonadError[?[_], Throwable], A](s: EitherT[F, SeqexecFailure, A]) {
    def liftF: F[A] =
      s.value.flatMap(_.liftTo[F])
  }

  implicit class EitherTOps[F[_],  A, B](fa: EitherT[F, A, B]) {
    def widenRethrowT[T](
      implicit me: MonadError[F, T],
               at: A <:< T
    ): F[B] =
      fa.leftMap(at).rethrowT
  }

  // This assumes that there is only one instance of e in l
  private def moveElement[T](l: List[T], e: T, delta: Int)(implicit eq: Eq[T]): List[T] = {
    val idx = l.indexOf(e)

    if (delta === 0 || idx < 0) {
      l
    } else {
      val (h, t) = l.filterNot(_ === e).splitAt(idx + delta)
      (h :+ e) ++ t
    }
  }

  implicit class ExecutionQueueOps(val q: ExecutionQueue) extends AnyVal {
    def status(st: EngineState): BatchExecState = {
      val statuses: Seq[SequenceState] = q.queue.map(sid => st.sequences.get(sid))
        .collect{ case Some(x) => x }
        .map(_.seq.status)

      q.cmdState match {
        case BatchCommandState.Idle         => BatchExecState.Idle
        case BatchCommandState.Run(_, _, _) => if(statuses.forall(_.isCompleted)) BatchExecState.Completed
                                               else if(statuses.exists(_.isRunning)) BatchExecState.Running
                                                    else BatchExecState.Waiting
        case BatchCommandState.Stop         => if(statuses.exists(_.isRunning)) BatchExecState.Stopping
                                               else BatchExecState.Idle
      }
    }

    def addSeq(sid: Observation.Id): ExecutionQueue = q.copy(queue = q.queue :+ sid)
    def addSeqs(sids: List[Observation.Id]): ExecutionQueue = q.copy(queue = q.queue ++ sids)
    def removeSeq(sid: Observation.Id): ExecutionQueue = q.copy(queue = q.queue.filter(_ =!= sid))
    def moveSeq(sid:Observation.Id, delta: Int): ExecutionQueue = q.copy(queue = moveElement(q.queue, sid, delta))
    def clear: ExecutionQueue = q.copy(queue = List.empty)
  }

  implicit class ControlStrategyOps(v: ControlStrategy) {
    val connect: Boolean = v match {
      case ControlStrategy.Simulated => false
      case _         => true
    }
    // If connected, then use real values for keywords
    val realKeywords: Boolean = connect
    val command: Boolean = v match {
      case ControlStrategy.FullControl => true
      case _           => false
    }
  }

  implicit final class ToHandle[A](f: EngineState => (EngineState, A)) {
    import Handle.StateToHandle
    def toHandle: Handle[EngineState, Event[IO, executeEngine.ConcreteTypes], A] =
      StateT[IO, EngineState, A]{ st => IO(f(st)) }.toHandle
  }

  def toStepList[F[_]](seq: SequenceGen[F], d: HeaderExtraData): List[engine.Step[F]] =
    seq.steps.map(StepGen.generate(_, d))

  // If f is true continue, otherwise fail
  def failUnlessM[F[_]: MonadError[?[_], Throwable]](f: F[Boolean], err: Exception): F[Unit] =
    f.flatMap {
      MonadError[F, Throwable].raiseError(err).unlessA
    }

}
