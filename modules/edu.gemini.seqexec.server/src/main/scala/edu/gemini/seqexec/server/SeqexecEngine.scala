package edu.gemini.seqexec.server

import java.time.LocalDate

import edu.gemini.pot.sp.SPObservationID
import edu.gemini.seqexec.engine
import edu.gemini.seqexec.engine.{Action, Event, QState, Result, Sequence}

import scalaz._
import Scalaz._
import scalaz.concurrent.Task
import scalaz.stream.Process
import edu.gemini.seqexec.model.SharedModel._
import edu.gemini.seqexec.model.SharedModel.SeqexecEvent._

/**
  * Created by jluhrs on 10/7/16.
  */
object SeqexecEngine {

  val qs: QState = QState.init(
    engine.Queue(
      List[Sequence[Action]](
      )
    )
  )

  // TODO: Add seqId: SPObservationID as parameter
  def start(q: engine.EventQueue): Task[SeqexecFailure \/ Unit] =
    q.enqueueOne(Event.start).map(_.right)

  // TODO: Add seqId: SPObservationID as parameter
  def requestPause(q: engine.EventQueue): Task[SeqexecFailure \/ Unit ]=
    q.enqueueOne(Event.pause).map(_.right)

  // TODO: Add seqId: SPObservationID as parameter
  def setBreakpoint(q: engine.EventQueue): Task[SeqexecFailure \/ Unit]= ???

  // TODO: Add seqId: SPObservationID as parameter
  def setSkipMark(q: engine.EventQueue): Task[SeqexecFailure \/ Unit] = ???

  def requestRefresh(q: engine.EventQueue): Task[Unit] = q.enqueueOne(Event.poll)

  def eventProcess(q: engine.EventQueue): Process[Task, SeqexecEvent] =
    engine.Handler.processT(q)(qs).map {
      case (ev, qs) =>
        toSeqexecEvent(ev)(qs.toQueue.sequences.map(viewSequence))
    }

  def load(q: engine.EventQueue, seqId: SPObservationID): Task[SeqexecFailure \/ Unit] = {
    val t = EitherT( for {
        odbSeq <- Task(ODBProxy.read(seqId))
        now <- Task(LocalDate.now)
      } yield odbSeq.flatMap(s => SeqTranslate.sequence(DhsClientSim(now))(seqId.stringValue(), s))
    )
    val u = t.flatMapF(x => q.enqueueOne(Event.load(x)).map(_.right))
    u.run
  }


  private def toSeqexecEvent(ev: Event.Event)(svs: List[SequenceView]): SeqexecEvent = ev match {
    case Event.EventUser(ue) => ue match {
      case Event.Start => SequenceStart(svs)
      case Event.Pause => SequencePauseRequested(svs)
      case Event.Poll  => NewLogMessage("Immediate State requested")
      case Event.Exit  => NewLogMessage("Exit requested by user")
    }
    case Event.EventSystem(se) => se match {
      // TODO: Sequence completed event not emited by engine.
      case Event.Completed(_, _) => NewLogMessage("Action completed")
      case Event.Failed(_, _)    => NewLogMessage("Action failed")
      case Event.Executed        => StepExecuted(svs)
      case Event.Finished        => NewLogMessage("Execution finished")
    }
  }

    // TODO: Better name and move it to `engine`
    type QueueAR = engine.Queue[engine.Action \/ engine.Result]
    type SequenceAR = engine.Sequence[engine.Action \/ engine.Result]
    type StepAR = engine.Step[engine.Action \/ engine.Result]


    def viewSequence(seq: SequenceAR): SequenceView =
      // TODO: Implement willStopIn
      SequenceView(statusSequence(seq), engineSteps(seq), None)

    private def statusSequence(seq: SequenceAR): SequenceState =
      engine.Sequence.status(seq) match {
          case engine.Status.Waiting   => SequenceState.Idle
          case engine.Status.Completed => SequenceState.Completed
          case engine.Status.Running   => SequenceState.Running
        }

    private def engineSteps(seq: SequenceAR): List[Step] = {

      def statusStep(step: StepAR): StepState =
        engine.Step.status(step) match {
          case engine.Status.Waiting   => StepState.Pending
          case engine.Status.Completed => StepState.Completed
          case engine.Status.Running   => StepState.Running
        }

      def viewStep(step: StepAR): Step =
        StandardStep(
          // TODO: Add configuration parameter to Engine Step
          Map.empty,
          statusStep(step),
          // TODO: Implement breakpoints at Engine level
          false,
          // TODO: Implement skipping at Engine level
          false,
          Map.empty,
          // TODO: Implement standard step at Engine level
          ActionStatus.Pending
        )

      seq.steps.map(viewStep)
    }
  }