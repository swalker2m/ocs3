// Copyright (c) 2016-2019 Association of Universities for Research in Astronomy, Inc. (AURA)
// For license information see LICENSE or https://opensource.org/licenses/BSD-3-Clause

package seqexec.model

import cats.Eq
import cats.implicits._
import gem.Observation
import squants.Time

final case class ObservationProgress(obsId:     Observation.Id,
                                     stepId:    StepId,
                                     total:     Time,
                                     remaining: Time)

object ObservationProgress {

  implicit val equal: Eq[ObservationProgress] =
    Eq.by(x => (x.obsId, x.stepId, x.total, x.remaining))

}
