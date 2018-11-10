// Copyright (c) 2016-2018 Association of Universities for Research in Astronomy, Inc. (AURA)
// For license information see LICENSE or https://opensource.org/licenses/BSD-3-Clause

package seqexec.web.client.circuit

import cats.Eq
import cats.implicits._
import gem.Observation
import monocle.Getter
import monocle.macros.Lenses
import seqexec.model._
import seqexec.model.enum._
import seqexec.web.client.model._
import seqexec.web.client.model.ModelOps._
import seqexec.web.client.components.sequence.steps.StepsTable
import web.client.table._

@Lenses
final case class StepsTableFocus(
  id:                  Observation.Id,
  instrument:          Instrument,
  state:               SequenceState,
  steps:               List[Step],
  stepConfigDisplayed: Option[Int],
  nextStepToRun:       Option[Int],
  isPreview:           Boolean,
  tableState:          TableState[StepsTable.TableColumn])

@SuppressWarnings(Array("org.wartremover.warts.PublicInference"))
object StepsTableFocus {
  implicit val eq: Eq[StepsTableFocus] =
    Eq.by(
      x =>
        (x.id,
         x.instrument,
         x.state,
         x.steps,
         x.stepConfigDisplayed,
         x.nextStepToRun,
         x.isPreview,
         x.tableState))

  def stepsTableG(
    id: Observation.Id
  ): Getter[SeqexecAppRootModel, Option[StepsTableFocus]] =
    SeqexecAppRootModel.sequencesOnDisplayL.composeGetter(
      SequencesOnDisplay.tabG(id)) >>> {
      _.flatMap {
        case SeqexecTabActive(tab, _) =>
          tab.sequence.map { sequence =>
            StepsTableFocus(sequence.id,
                            sequence.metadata.instrument,
                            sequence.status,
                            sequence.steps,
                            tab.stepConfigDisplayed,
                            sequence.nextStepToRun,
                            tab.isPreview,
                            tab.tableState)
          }
      }
    }
}
