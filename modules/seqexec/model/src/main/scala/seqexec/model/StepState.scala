// Copyright (c) 2016-2019 Association of Universities for Research in Astronomy, Inc. (AURA)
// For license information see LICENSE or https://opensource.org/licenses/BSD-3-Clause

package seqexec.model

import cats.Eq
import cats.implicits._

sealed abstract class StepState(val canRunFrom: Boolean)
  extends Product with Serializable

object StepState {

        case object Pending             extends StepState(true)
        case object Completed           extends StepState(false)
        case object Skipped             extends StepState(false)
  final case class  Failed(msg: String) extends StepState(true)
        case object Running             extends StepState(false)
        case object Paused              extends StepState(true)

  implicit val equal: Eq[StepState] =
    Eq.instance {
      case (Pending, Pending)     => true
      case (Completed, Completed) => true
      case (Skipped, Skipped)     => true
      case (Failed(a), Failed(b)) => a === b
      case (Running, Running)     => true
      case (Paused, Paused)       => true
      case _                      => false
    }

}
