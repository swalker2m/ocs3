// Copyright (c) 2016-2019 Association of Universities for Research in Astronomy, Inc. (AURA)
// For license information see LICENSE or https://opensource.org/licenses/BSD-3-Clause

package seqexec.server.gcal

import cats.{Eq, Show}
import cats.implicits._
import edu.gemini.spModel.gemini.calunit.CalUnitParams.Shutter

trait GcalController[F[_]] {

  import GcalController._

  def getConfig: F[GcalConfig]

  def applyConfig(config: GcalConfig): F[Unit]

}

object GcalController {
  sealed trait LampState extends Product with Serializable

  object LampState {

    case object Off extends LampState

    case object On extends LampState

    implicit val eq: Eq[LampState] =
      Eq.fromUniversalEquals

  }

  final case class ArLampState(self: LampState)

  object ArLampState {
    implicit val eq: Eq[ArLampState] =
      Eq[LampState].contramap(_.self)
  }

  final case class CuArLampState(self: LampState)

  object CuArLampState {
    implicit val eq: Eq[CuArLampState] =
      Eq[LampState].contramap(_.self)
  }

  final case class QHLampState(self: LampState)

  object QHLampState {
    implicit val eq: Eq[QHLampState] =
      Eq[LampState].contramap(_.self)
  }

  final case class ThArLampState(self: LampState)

  object ThArLampState {
    implicit val eq: Eq[ThArLampState] =
      Eq[LampState].contramap(_.self)
  }

  final case class XeLampState(self: LampState)

  object XeLampState {
    implicit val eq: Eq[XeLampState] =
      Eq[LampState].contramap(_.self)
  }

  final case class IrLampState(self: LampState)

  object IrLampState {
    implicit val eq: Eq[IrLampState] =
      Eq[LampState].contramap(_.self)
  }

  type Shutter = edu.gemini.spModel.gemini.calunit.CalUnitParams.Shutter

  type Filter = edu.gemini.spModel.gemini.calunit.CalUnitParams.Filter

  type Diffuser = edu.gemini.spModel.gemini.calunit.CalUnitParams.Diffuser

  final case class GcalConfig(
                         lampAr: Option[ArLampState],
                         lampCuAr: Option[CuArLampState],
                         lampQh: Option[QHLampState],
                         lampThAr: Option[ThArLampState],
                         lampXe: Option[XeLampState],
                         lampIr: Option[IrLampState],
                         shutter: Option[Shutter],
                         filter: Option[Filter],
                         diffuser: Option[Diffuser]
                       )

  object GcalConfig {

    val allOff: GcalConfig = GcalConfig(Some(ArLampState(LampState.Off)),
      Some(CuArLampState(LampState.Off)),
      Some(QHLampState(LampState.Off)),
      Some(ThArLampState(LampState.Off)),
      Some(XeLampState(LampState.Off)),
      Some(IrLampState(LampState.Off)),
      Some(Shutter.CLOSED),
      None,
      None
    )

    def fullConfig(ar: ArLampState,
                   cuAr: CuArLampState,
                   qh: QHLampState,
                   thAr: ThArLampState,
                   xe: XeLampState,
                   ir: IrLampState,
                   sh: Shutter,
                   flt: Filter,
                   diff: Diffuser
                  ): GcalConfig = GcalConfig(Some(ar), Some(cuAr), Some(qh), Some(thAr), Some(xe),
      Some(ir), Some(sh), Some(flt), Some(diff))

  }

  implicit val gcalConfigShow: Show[GcalConfig] = Show.show( config =>
    List(
      s"lampAr = ${config.lampAr}",
      s"lampCuar = ${config.lampCuAr}",
      s"lampQH = ${config.lampQh}",
      s"lampThAr = ${config.lampThAr}",
      s"lampXe = ${config.lampXe}",
      s"lampIr = ${config.lampIr}",
      s"shutter = ${config.shutter}",
      s"filter = ${config.filter}",
      s"diffuser = ${config.diffuser}"
    ).mkString
  )

}
