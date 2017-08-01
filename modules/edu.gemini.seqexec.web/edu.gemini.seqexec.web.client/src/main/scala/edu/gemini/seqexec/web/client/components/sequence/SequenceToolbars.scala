package edu.gemini.seqexec.web.client.components.sequence

import edu.gemini.seqexec.model.Model.{Instrument, SequenceId, SequenceState, SequenceView}
import edu.gemini.seqexec.web.client.model._
import edu.gemini.seqexec.web.client.model.ModelOps._
import edu.gemini.seqexec.web.client.semanticui.elements.button.Button
import edu.gemini.seqexec.web.client.components.SeqexecStyles
import edu.gemini.seqexec.web.client.semanticui.elements.input.InputEV
import edu.gemini.seqexec.web.client.semanticui.elements.label.{FormLabel, Label}
import edu.gemini.seqexec.web.client.semanticui.elements.icon.Icon._
import japgolly.scalajs.react.extra.{StateSnapshot, TimerSupport}
import japgolly.scalajs.react.vdom.html_<^._
import japgolly.scalajs.react.{BackendScope, Callback, CallbackTo, ScalaComponent, ScalazReact}
import japgolly.scalajs.react.ScalazReact._
import japgolly.scalajs.react.component.Scala.Unmounted
import org.scalajs.dom.html.Div
import diode.react.ModelProxy

import scalaz.syntax.equal._
import scalaz.syntax.std.boolean._
import scalaz.syntax.std.option._
import scalaz.std.string._
import scalaz.std.option._
import scala.concurrent.duration._

import scalacss.ScalaCssReact._

object SequenceInfo {
  case class Props(p: ModelProxy[StatusAndSequenceInfo])

  private def component = ScalaComponent.builder[Props]("SequenceInfo")
    .stateless
    .render_P { p =>
      val StatusAndSequenceInfo(isLogged, name, observer) = p.p()
      val obsName = name.filter(_.nonEmpty).getOrElse("Unknown.")
      <.div(
        ^.cls := "ui form",
        <.div(
          ^.cls := "fields",
          SeqexecStyles.fieldsNoBottom.unless(isLogged),
          <.div(
            ^.cls := "field",
            Label(Label.Props(obsName, basic = true))
          ).when(isLogged),
          <.div(
            ^.cls := "field",
            Label(Label.Props("Observer:", basic = true, color = "red".some))
          ).unless(isLogged),
          <.div(
            ^.cls := "field",
            Label(Label.Props(observer.getOrElse("Unknown."), basic = true))
          ).unless(isLogged)
        )
      )
    }.build

  def apply(p: ModelProxy[StatusAndSequenceInfo]): Unmounted[Props, Unit, Unit] = component(Props(p))
}

object SequenceObserverField {
  case class Props(p: ModelProxy[StatusAndObserverInfo])

  case class State(currentText: Option[String])

  class Backend(val $: BackendScope[Props, State]) extends TimerSupport {
    def updateObserver(id: SequenceId, name: String): Callback =
      $.props >>= { p => Callback.when(p.p().isLogged)(p.p.dispatchCB(UpdateObserver(id, name))) }

    def updateState(value: String): Callback =
      $.state >>= { s => Callback.when(!s.currentText.contains(value))($.modState(_.copy(currentText = Some(value)))) }

    def submitIfChanged: Callback =
      ($.state zip $.props) >>= {
        case (s, p) => Callback.when(p.p().isLogged && p.p().observer.fold(s.currentText.forall(_.nonEmpty))(o => s.currentText.forall(_ =/= o)))(p.p().id.map(updateObserver(_, s.currentText.getOrElse(""))).getOrEmpty)
      }

    def setupTimer: Callback =
      // Every 2 seconds check if the field has changed and submit
      setInterval(submitIfChanged, 2.second)

    def render(p: Props, s: State): VdomTagOf[Div] = {
      val observerEV = StateSnapshot(~s.currentText)(updateState)
      <.div(
        ^.cls := "ui form",
        <.div(
          ^.cls := "ui inline fields",
          <.div(
            ^.cls := "field four wide required",
            FormLabel(FormLabel.Props("Observer"))
          ),
          <.div(
            ^.cls := "field fourteen wide",
            InputEV(InputEV.Props(
              p.p().instrument + ".observer",
              p.p().instrument + ".observer",
              observerEV,
              placeholder = "Observer...",
              onBlur = _ => submitIfChanged))
          )
        )
      )
    }
  }

  private val component = ScalaComponent.builder[Props]("SequenceObserverField")
    .initialState(State(None))
    .renderBackend[Backend]
    .configure(TimerSupport.install)
    .componentWillMount(f => f.backend.$.props >>= {p => Callback.when(p.p().observer.isDefined)(f.backend.updateState(p.p().observer.getOrElse("")))})
    .componentDidMount(_.backend.setupTimer)
    .componentWillReceiveProps { f =>
      val observer = f.nextProps.p().observer
      // Update the observer field
      Callback.when((observer =/= f.state.currentText) && observer.nonEmpty)(f.backend.updateState(observer.getOrElse("")))
    }
    .shouldComponentUpdatePure { f =>
      val observer = f.nextProps.p().observer
      observer =/= f.currentState.currentText
    }
    .build

  def apply(p: ModelProxy[StatusAndObserverInfo]): Unmounted[Props, State, Backend] = component(Props(p))
}

object SequenceControl {
  case class Props(p: ModelProxy[SequenceControlModel])
  case class State(runRequested: Boolean, pauseRequested: Boolean, syncRequested: Boolean)

  private val ST = ReactS.Fix[State]

  def requestRun(s: SequenceId): ScalazReact.ReactST[CallbackTo, State, Unit] =
    ST.retM(Callback(SeqexecCircuit.dispatch(RequestRun(s)))) >> ST.mod(_.copy(runRequested = true, pauseRequested = false, syncRequested = false)).liftCB

  def requestSync(s: SequenceId): ScalazReact.ReactST[CallbackTo, State, Unit] =
    ST.retM(Callback(SeqexecCircuit.dispatch(RequestSync(s)))) >> ST.mod(_.copy(runRequested = false, pauseRequested = false, syncRequested = true)).liftCB

  def requestPause(s: SequenceId): ScalazReact.ReactST[CallbackTo, State, Unit] =
    ST.retM(Callback(SeqexecCircuit.dispatch(RequestPause(s)))) >> ST.mod(_.copy(runRequested = false, pauseRequested = true, syncRequested = false)).liftCB

  private def component = ScalaComponent.builder[Props]("SequencesDefaultToolbar")
    .initialState(State(runRequested = false, pauseRequested = false, syncRequested = false))
    .renderPS { ($, p, s) =>
      val SequenceControlModel(isLogged, isConnected, control) = p.p()
      <.div(
        control.whenDefined { m =>
          val ControlModel(id, isPartiallyExecuted, nextStep, status) = m
          val nextStepToRun = nextStep.getOrElse(0) + 1
          val runContinueTooltip = s"${isPartiallyExecuted ? "Continue" | "Run"} the sequence from the step $nextStepToRun"
          val runContinueButton = s"${isPartiallyExecuted ? "Continue" | "Run"} from step $nextStepToRun"
          List(
            <.h3(
              ^.cls := "ui green header",
              "Sequence complete"
            ).when(status === SequenceState.Completed),
            Button(
              Button.Props(
                icon = Some(IconPlay),
                labeled = true,
                onClick = $.runState(requestRun(id)),
                color = Some("blue"),
                dataTooltip = Some(runContinueTooltip),
                disabled = !isConnected || s.runRequested || s.syncRequested),
              runContinueButton
            ).when(status.isError),
            Button(
              Button.Props(
                icon = Some(IconRefresh),
                labeled = true,
                onClick = $.runState(requestSync(id)),
                color = Some("purple"),
                dataTooltip = Some(s"Sync sequence"),
                disabled = !isConnected || s.runRequested || s.syncRequested),
              s" Synkeys"
            ).when(status === SequenceState.Idle),
            Button(
              Button.Props(
                icon = Some(IconPlay),
                labeled = true,
                onClick = $.runState(requestRun(id)),
                color = Some("blue"),
                dataTooltip = Some(runContinueTooltip),
                disabled = !isConnected || s.runRequested || s.syncRequested),
              runContinueButton
            ).when(status === SequenceState.Idle),
            Button(
              Button.Props(
                icon = Some(IconPause),
                labeled = true,
                onClick = $.runState(requestPause(id)),
                color = Some("teal"),
                dataTooltip = Some("Pause the sequence after the current step completes"),
                disabled = !isConnected || s.pauseRequested || s.syncRequested || status === SequenceState.Stopping),
              "Pause"
            ).when(status === SequenceState.Running || status === SequenceState.Stopping),
            Button(
              Button.Props(
                icon = Some(IconPlay),
                labeled = true,
                onClick = $.runState(requestPause(id)),
                color = Some("teal"),
                disabled = !isConnected || s.syncRequested),
              "Continue from step 1"
            ).when(status === SequenceState.Paused)
          ).toTagMod
        }
      )
    }.componentWillReceiveProps { f =>
      // Update state of run requested depending on the run state
      // Callback.when(f.nextProps.s.status === SequenceState.Running && f.state.runRequested)(f.modState(_.copy(runRequested = false)))
      Callback.empty
    }.build

  def apply(p: ModelProxy[SequenceControlModel]): Unmounted[Props, State, Unit] = component(Props(p))
}

object SequenceDefaultToolbar {
  case class Props(instrument: Instrument)

  val sequenceInfoConnects = Instrument.gsInstruments.list.toList.map(i => (i, SeqexecCircuit.connect(SeqexecCircuit.sequenceInfo(i)))).toMap
  val sequenceObserverConnects = Instrument.gsInstruments.list.toList.map(i => (i, SeqexecCircuit.connect(SeqexecCircuit.sequenceObserver(i)))).toMap
  val sequenceControlConnects = Instrument.gsInstruments.list.toList.map(i => (i, SeqexecCircuit.connect(SeqexecCircuit.sequenceControl(i)))).toMap

  private def component = ScalaComponent.builder[Props]("SequencesDefaultToolbar")
    .render_P( p =>
      <.div(
        ^.cls := "ui row",
        <.div(
          sequenceInfoConnects.get(p.instrument).whenDefined(c => c(SequenceInfo.apply))
        ),
        <.div(
          ^.cls := "ui two column grid",
          <.div(
            ^.cls := "ui left column eight wide computer sixteen wide tablet only",
            SeqexecStyles.controlColumn,
            sequenceControlConnects.get(p.instrument).whenDefined(c => c(SequenceControl.apply)),
          ),
          <.div(
            ^.cls := "ui right column eight wide computer eight wide tablet sixteen wide mobile",
            SeqexecStyles.controlColumn,
            sequenceObserverConnects.get(p.instrument).whenDefined(c => c(m => SequenceObserverField(m)))
          )
        )
      )
    ).build

  def apply(p: Instrument): Unmounted[Props, Unit, Unit] = component(Props(p))
}

object SequenceAnonymousToolbar {
  case class Props(instrument: Instrument)

  val instrumentConnects = Instrument.gsInstruments.list.toList.map(i => (i, SeqexecCircuit.connect(SeqexecCircuit.sequenceInfo(i)))).toMap

  private def component = ScalaComponent.builder[Props]("SequencesDefaultToolbar")
    .stateless
    .render_P ( p =>
      <.div(
        ^.cls := "ui column",
        <.div(
          ^.cls := "ui row",
          <.div(
            ^.cls := "left column bottom aligned sixteen wide computer ten wide tablet only",
            instrumentConnects.get(p.instrument).whenDefined(_(SequenceInfo.apply))
          )
        )
      )
    ).build

  def apply(i: Instrument): Unmounted[Props, Unit, Unit] = component(Props(i))
}

object StepConfigToolbar {
  case class Props(s: SequenceView, isLogged: Boolean, step: Int)

  def backToSequence(s: SequenceView): Callback = Callback {SeqexecCircuit.dispatch(UnShowStep(s))}

  private val component = ScalaComponent.builder[Props]("StepConfigToolbar")
    .stateless
    .render_P( p =>
      <.div(
        <.div(
          ^.cls := "ui row",
          <.div(
            ^.cls := "left column bottom aligned sixteen wide computer ten wide tablet only",
            // SequenceInfo(SequenceInfo.Props(p.s, p.isLogged)),
            <.h3(
              ^.cls := "ui green header",
              "Sequence complete"
            ).when(p.s.status === SequenceState.Completed)
          )
        ),
        <.div(
          ^.cls := "row",
          Button(Button.Props(icon = Some(IconChevronLeft), onClick = backToSequence(p.s)), "Back"),
          <.h5(
            ^.cls := "ui header",
            SeqexecStyles.inline,
            s" Configuration for step ${p.step + 1}"
          )
        )
      )
    ).build

  def apply(p: Props): Unmounted[Props, Unit, Unit] = component(p)
}
