// Copyright (c) 2016-2018 Association of Universities for Research in Astronomy, Inc. (AURA)
// For license information see LICENSE or https://opensource.org/licenses/BSD-3-Clause

package seqexec.web.client.handlers

import cats.implicits._
import diode.{ ActionHandler, ActionResult, Effect, ModelRW }
import seqexec.model.InstrumentInUse
import seqexec.model.events.UserNotification
import seqexec.web.client.model._
import seqexec.web.client.actions._
import scala.concurrent.Future
import scala.scalajs.concurrent.JSExecutionContext.Implicits.queue

class NotificationsHandler[M](modelRW: ModelRW[M, UserNotificationState]) extends ActionHandler(modelRW) with Handlers[M, UserNotificationState] {
  def handleUserNotification: PartialFunction[Any, ActionResult[M]] = {
    case ServerMessage(UserNotification(not, _)) =>
      // Update the notification state
      val lens = UserNotificationState.notification.set(not.some)
      // Request opening the dialog box
      val openBoxE = Effect(Future(OpenUserNotificationBox))
      // Update the model as load failed
      val modelUpdateE = not match {
        case InstrumentInUse(id, _) => Effect(Future(SequenceLoadFailed(id)))
        case _                      => VoidEffect
      }
      updatedLE(lens, openBoxE >> modelUpdateE)
  }

  def handleCloseNotification: PartialFunction[Any, ActionResult[M]] = {
    case CloseUserNotificationBox =>
      updatedL(UserNotificationState.notification.set(none))
  }

  def handle: PartialFunction[Any, ActionResult[M]] =
    handleUserNotification
}
