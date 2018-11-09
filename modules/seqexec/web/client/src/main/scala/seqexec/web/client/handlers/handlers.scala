// Copyright (c) 2016-2018 Association of Universities for Research in Astronomy, Inc. (AURA)
// For license information see LICENSE or https://opensource.org/licenses/BSD-3-Clause

package seqexec.web.client.handlers

import cats.implicits._
import diode.ActionHandler
import diode.ActionResult
import diode.Effect
import diode.ModelRW
import diode.NoAction
import gem.enum.Site
import seqexec.model.Observer
import seqexec.model.Operator
import seqexec.model.SequencesQueue
import seqexec.model.SequenceView
import seqexec.web.client.model._
import seqexec.web.client.model.ModelOps._
import seqexec.web.client.actions._
import seqexec.web.client.services.SeqexecWebClient
import scala.scalajs.concurrent.JSExecutionContext.Implicits.queue

/**
  * Handles updates to the operator
  */
class OperatorHandler[M](modelRW: ModelRW[M, Option[Operator]])
    extends ActionHandler(modelRW)
    with Handlers[M, Option[Operator]] {
  override def handle: PartialFunction[Any, ActionResult[M]] = {
    case UpdateOperator(name) =>
      val updateOperatorE = Effect(
        SeqexecWebClient.setOperator(name).map(_ => NoAction))
      updated(name.some, updateOperatorE)
  }
}

/**
  * Handles setting the site
  */
class SiteHandler[M](modelRW: ModelRW[M, Option[Site]])
    extends ActionHandler(modelRW)
    with Handlers[M, Option[Site]] {

  override def handle: PartialFunction[Any, ActionResult[M]] = {
    case Initialize(site) =>
      updated(Some(site))
  }
}

/**
  * Handles updates to the log
  */
class GlobalLogHandler[M](modelRW: ModelRW[M, GlobalLog])
    extends ActionHandler(modelRW)
    with Handlers[M, GlobalLog] {
  override def handle: PartialFunction[Any, ActionResult[M]] = {
    case AppendToLog(s) =>
      updated(value.copy(log = value.log.append(s)))

    case ToggleLogArea =>
      updated(value.copy(display = value.display.toggle))
  }
}

/**
  * Handles updates to the defaultObserver
  */
class DefaultObserverHandler[M](modelRW: ModelRW[M, Observer])
    extends ActionHandler(modelRW)
    with Handlers[M, Observer] {
  override def handle: PartialFunction[Any, ActionResult[M]] = {
    case UpdateDefaultObserver(o) =>
      updated(o)
  }
}

/**
  * Handle for UI debugging events
  */
class DebuggingHandler[M](modelRW: ModelRW[M, SequencesQueue[SequenceView]])
    extends ActionHandler(modelRW)
    with Handlers[M, SequencesQueue[SequenceView]] {
  override def handle: PartialFunction[Any, ActionResult[M]] = {
    case MarkStepAsRunning(obsId, step) =>
      updated(value.copy(sessionQueue = value.sessionQueue.collect {
        case v: SequenceView if v.id === obsId => v.showAsRunning(step)
        case v                                 => v
      }))
  }
}
