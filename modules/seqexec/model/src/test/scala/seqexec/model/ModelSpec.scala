// Copyright (c) 2016-2019 Association of Universities for Research in Astronomy, Inc. (AURA)
// For license information see LICENSE or https://opensource.org/licenses/BSD-3-Clause

package seqexec.model

import cats.tests.CatsSuite
import cats.kernel.laws.discipline._
import gem.arb.ArbEnumerated._
import seqexec.model.enum._
import seqexec.model.SeqexecModelArbitraries._
import seqexec.model.events.SingleActionEvent
import seqexec.model.arb.ArbRunningStep._
import seqexec.model.arb.ArbNotification._
import seqexec.model.arb.ArbM2GuideConfig._
import seqexec.model.arb.ArbM1GuideConfig._
import seqexec.model.arb.ArbTelescopeGuideConfig._
import squants.time.Time
import squants.time.TimeUnit

/**
  * Tests Model typeclasses
  */
final class ModelSpec extends CatsSuite {

  checkAll("Eq[UserDetails]", EqTests[UserDetails].eqv)
  checkAll("Eq[SystemName]", EqTests[SystemName].eqv)
  checkAll("Eq[StepConfig]", EqTests[StepConfig].eqv)
  checkAll("Order[Resource]", OrderTests[Resource].order)
  checkAll("Eq[Resource]", EqTests[Resource].eqv)
  checkAll("Eq[List]", EqTests[List[(Resource, ActionStatus)]].eqv)
  checkAll("Eq[Instrument]", EqTests[Instrument].eqv)
  checkAll("Eq[Operator]", EqTests[Operator].eqv)
  checkAll("Eq[StepState]", EqTests[StepState].eqv)
  checkAll("Eq[ActionStatus]", EqTests[ActionStatus].eqv)
  checkAll("Eq[Step]", EqTests[Step].eqv)
  checkAll("Eq[StandardStep]", EqTests[StandardStep].eqv)
  checkAll("Eq[SequenceState]", EqTests[SequenceState].eqv)
  checkAll("Eq[ActionType]", EqTests[ActionType].eqv)
  checkAll("Eq[SequenceMetadata]", EqTests[SequenceMetadata].eqv)
  checkAll("Eq[SequenceView]", EqTests[SequenceView].eqv)
  checkAll("Eq[SequencesQueue[SequenceView]]",
           EqTests[SequencesQueue[SequenceView]].eqv)
  checkAll("Eq[StepType]", EqTests[StepType].eqv)
  checkAll("Order[TelescopeOffset.P]", OrderTests[TelescopeOffset.P].order)
  checkAll("Order[TelescopeOffset.Q]", OrderTests[TelescopeOffset.Q].order)
  checkAll("Eq[TelescopeOffset]", EqTests[TelescopeOffset].eqv)
  checkAll("Eq[Guiding]", EqTests[Guiding].eqv)
  checkAll("Eq[FPUMode]", EqTests[FPUMode].eqv)
  checkAll("Eq[CloudCover]", EqTests[CloudCover].eqv)
  checkAll("Eq[WaterVapor]", EqTests[WaterVapor].eqv)
  checkAll("Eq[ImageQuality]", EqTests[ImageQuality].eqv)
  checkAll("Eq[SkyBackground]", EqTests[SkyBackground].eqv)
  checkAll("Eq[Conditions]", EqTests[Conditions].eqv)
  checkAll("Eq[ClientId]", EqTests[ClientId].eqv)
  checkAll("Order[ClientId]", OrderTests[ClientId].eqv)
  checkAll("Eq[QueueId]", EqTests[QueueId].eqv)
  checkAll("Order[QueueId]", OrderTests[QueueId].eqv)
  checkAll("Eq[ServerLogLevel]", EqTests[ServerLogLevel].eqv)
  checkAll("Eq[Notification]", EqTests[Notification].eqv)
  checkAll("Eq[ResourceConflict]", EqTests[ResourceConflict].eqv)
  checkAll("Eq[InstrumentInUse]", EqTests[InstrumentInUse].eqv)
  checkAll("Eq[RequestFailed]", EqTests[RequestFailed].eqv)
  checkAll("Eq[SubsystemBusy]", EqTests[SubsystemBusy].eqv)
  checkAll("Eq[ExecutionQueueView]", EqTests[ExecutionQueueView].eqv)
  checkAll("Eq[ObservationProgress]", EqTests[ObservationProgress].eqv)
  checkAll("Eq[TimeUnit]", EqTests[TimeUnit].eqv)
  checkAll("Eq[Time]", EqTests[Time].eqv)
  checkAll("Eq[SingleActionOp]", EqTests[SingleActionOp].eqv)
  checkAll("Eq[SingleActionEvent]", EqTests[SingleActionEvent].eqv)
  checkAll("Eq[RunningStep]", EqTests[RunningStep].eqv)
  checkAll("Eq[MountGuideOption]", EqTests[MountGuideOption].eqv)
  checkAll("Eq[ComaOption]", EqTests[ComaOption].eqv)
  checkAll("Eq[TipTiltSource]", EqTests[TipTiltSource].eqv)
  checkAll("Eq[M2GuideConfig]", EqTests[M2GuideConfig].eqv)
  checkAll("Eq[M1Source]", EqTests[M1Source].eqv)
  checkAll("Eq[M1GuideConfig]", EqTests[M1GuideConfig].eqv)
  checkAll("Eq[TelescopeGuideConfig]", EqTests[TelescopeGuideConfig].eqv)
  checkAll("Eq[BatchCommandState]", EqTests[BatchCommandState].eqv)
  checkAll("Eq[ApplyCommandResult]", EqTests[ApplyCommandResult].eqv)
  checkAll("Eq[ObserveCommandResult]", EqTests[ObserveCommandResult].eqv)
}
