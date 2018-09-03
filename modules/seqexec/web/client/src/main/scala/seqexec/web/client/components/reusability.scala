// Copyright (c) 2016-2018 Association of Universities for Research in Astronomy, Inc. (AURA)
// For license information see LICENSE or https://opensource.org/licenses/BSD-3-Clause

package seqexec.web.client

import diode.data.PotState
import diode.react.ModelProxy
import gem.Observation
import gem.enum.Site
import japgolly.scalajs.react.CatsReact._
import japgolly.scalajs.react.extra.Reusability
import seqexec.model.enum.Instrument
import seqexec.web.client.model.{ AvailableTab, ClientStatus, SectionVisibilityState, UserNotificationState, WebSocketConnection }
import seqexec.web.client.circuit._
import seqexec.model.{ Observer, StepState, Step, StepConfig, UserDetails }

package object reusability {
  implicit val stepStateReuse          : Reusability[StepState]                = Reusability.byEq
  implicit val instrumentReuse         : Reusability[Instrument]               = Reusability.byEq
  implicit val obsIdReuse              : Reusability[Observation.Id]           = Reusability.byEq
  implicit val siteReuse               : Reusability[Site]                     = Reusability.byEq
  implicit val observerReuse           : Reusability[Observer]                 = Reusability.byEq
  implicit val stepConfigReuse         : Reusability[StepConfig]               = Reusability.byEq
  implicit val stepReuse               : Reusability[Step]                     = Reusability.byEq
  implicit val clientStatusReuse       : Reusability[ClientStatus]             = Reusability.byEq
  implicit val stepsTableFocusReuse    : Reusability[StepsTableFocus]          = Reusability.byEq
  implicit val statusAndStepFocusReuse : Reusability[StatusAndStepFocus]       = Reusability.byEq
  implicit val stsfReuse               : Reusability[StepsTableAndStatusFocus] = Reusability.byEq
  implicit val sectonReuse             : Reusability[SectionVisibilityState]   = Reusability.byRef
  implicit val potStateReuse           : Reusability[PotState]                 = Reusability.byRef
  implicit val webSocketConnectionReuse: Reusability[WebSocketConnection]      = Reusability.by(_.ws.state)
  implicit val availableTabsReuse      : Reusability[AvailableTab]             = Reusability.byEq
  implicit val userDetailsReuse        : Reusability[UserDetails]              = Reusability.byEq
  implicit val userNotificationReuse   : Reusability[UserNotificationState]    = Reusability.byEq

  implicit def modelProxyReuse[A: Reusability]: Reusability[ModelProxy[A]] = Reusability.by(_())
}
