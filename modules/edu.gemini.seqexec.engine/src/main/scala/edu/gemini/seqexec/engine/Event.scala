package edu.gemini.seqexec.engine

import edu.gemini.seqexec.model.Model.{Conditions, ImageQuality, SkyBackground, WaterVapor}
import Result.{OK, Partial, PartialVal, RetVal}

/**
  * Anything that can go through the Event Queue.
  */
sealed trait Event
case class EventUser(ue: UserEvent) extends Event
case class EventSystem(se: SystemEvent) extends Event

/**
  * Events generated by the user.
  */
sealed trait UserEvent
case class Start(id: Sequence.Id) extends UserEvent
case class Pause(id: Sequence.Id) extends UserEvent
case class Load(id: Sequence.Id, sequence: Sequence[Action]) extends UserEvent
case class Breakpoint(id: Sequence.Id, step: Step.Id, v: Boolean) extends UserEvent
case class SetOperator(name: String) extends UserEvent
case class SetObserver(id: Sequence.Id, name: String) extends UserEvent
case class SetConditions(conditions: Conditions) extends UserEvent
case class SetImageQuality(iq: ImageQuality) extends UserEvent
case class SetWaterVapor(wv: WaterVapor) extends UserEvent
case class SetSkyBackground(wv: SkyBackground) extends UserEvent
case object Poll extends UserEvent
case object Exit extends UserEvent

/**
  * Events generated internally by the Engine.
  */
sealed trait SystemEvent
case class Completed[R<:RetVal](id: Sequence.Id, i: Int, r: OK[R]) extends SystemEvent
case class PartialResult[R<:PartialVal](id: Sequence.Id, i: Int, r: Partial[R]) extends SystemEvent
case class Failed(id: Sequence.Id, i: Int, e: Result.Error) extends SystemEvent
case class Executed(id: Sequence.Id) extends SystemEvent
case class Executing(id: Sequence.Id) extends SystemEvent
case class Finished(id: Sequence.Id) extends SystemEvent

object Event {

  def start(id: Sequence.Id): Event = EventUser(Start(id))
  def pause(id: Sequence.Id): Event = EventUser(Pause(id))
  def load(id: Sequence.Id, sequence: Sequence[Action]): Event = EventUser(Load(id, sequence))
  def breakpoint(id: Sequence.Id, step: Step.Id, v: Boolean): Event = EventUser(Breakpoint(id, step, v))
  def setOperator(name: String): Event = EventUser(SetOperator(name))
  def setObserver(id: Sequence.Id, name: String): Event = EventUser(SetObserver(id, name))
  def setConditions(conditions: Conditions): Event = EventUser(SetConditions(conditions))
  def setImageQuality(iq: ImageQuality): Event = EventUser(SetImageQuality(iq))
  def setWaterVapor(iq: WaterVapor): Event = EventUser(SetWaterVapor(iq))
  def setSkyBackground(iq: SkyBackground): Event = EventUser(SetSkyBackground(iq))
  val poll: Event = EventUser(Poll)
  val exit: Event = EventUser(Exit)

  def failed(id: Sequence.Id, i: Int, e: Result.Error): Event = EventSystem(Failed(id, i, e))
  def completed[R<:RetVal](id: Sequence.Id, i: Int, r: OK[R]): Event = EventSystem(Completed(id, i, r))
  def partial[R<:PartialVal](id: Sequence.Id, i: Int, r: Partial[R]): Event = EventSystem(PartialResult(id, i, r))
  def executed(id: Sequence.Id): Event = EventSystem(Executed(id))
  def executing(id: Sequence.Id): Event = EventSystem(Executing(id))
  def finished(id: Sequence.Id): Event = EventSystem(Finished(id))

}
