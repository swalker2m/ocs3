// Copyright (c) 2016-2019 Association of Universities for Research in Astronomy, Inc. (AURA)
// For license information see LICENSE or https://opensource.org/licenses/BSD-3-Clause

package gem
package enum

import cats.instances.string._
import cats.syntax.eq._
import gem.util.Enumerated

/**
 * Enumerated type for Giapi Status Apply.
 * @group Enumerations (Generated)
 */
sealed abstract class GiapiStatusApply(
  val tag: String,
  val instrument: Instrument,
  val statusType: GiapiType,
  val statusItem: String,
  val applyItem: String
) extends Product with Serializable

object GiapiStatusApply {

  /** @group Constructors */ case object GpiAdc extends GiapiStatusApply("GpiAdc", Instrument.Gpi, GiapiType.Int, "gpi:adcDeploy", "gpi:selectAdc.deploy")
  /** @group Constructors */ case object GpiUseAo extends GiapiStatusApply("GpiUseAo", Instrument.Gpi, GiapiType.Int, "gpi:ao:useAo", "gpi:configAo.useAo")
  /** @group Constructors */ case object GpiAoOptimize extends GiapiStatusApply("GpiAoOptimize", Instrument.Gpi, GiapiType.Int, "gpi:ao:optimization", "gpi:configAo.optimize")
  /** @group Constructors */ case object GpiUseCal extends GiapiStatusApply("GpiUseCal", Instrument.Gpi, GiapiType.Int, "gpi:cal:useCal", "gpi:configCal.useCal")
  /** @group Constructors */ case object GpiFpmPinholeBias extends GiapiStatusApply("GpiFpmPinholeBias", Instrument.Gpi, GiapiType.Int, "gpi:cal:fpmPinholeBias", "gpi:configCal.fpmPinholeBias")
  /** @group Constructors */ case object GpiIntegrationTime extends GiapiStatusApply("GpiIntegrationTime", Instrument.Gpi, GiapiType.Float, "gpi:currentIntegrationTime", "gpi:configIfs.integrationTime")
  /** @group Constructors */ case object GpiNumCoadds extends GiapiStatusApply("GpiNumCoadds", Instrument.Gpi, GiapiType.Int, "gpi:currentNumCoadds", "gpi:configIfs.numCoadds")
  /** @group Constructors */ case object GpiMagI extends GiapiStatusApply("GpiMagI", Instrument.Gpi, GiapiType.Float, "gpi:starIntensity", "gpi:configAo.magnitudeI")
  /** @group Constructors */ case object GpiMagH extends GiapiStatusApply("GpiMagH", Instrument.Gpi, GiapiType.Float, "gpi:cal:magH", "gpi:configCal.magnitudeH")
  /** @group Constructors */ case object GpiCalEntranceShutter extends GiapiStatusApply("GpiCalEntranceShutter", Instrument.Gpi, GiapiType.Int, "gpi:calEntranceShutter", "gpi:selectShutter.calEntranceShutter")
  /** @group Constructors */ case object GpiCalReferenceShutter extends GiapiStatusApply("GpiCalReferenceShutter", Instrument.Gpi, GiapiType.Int, "gpi:referenceShutter", "gpi:selectShutter.calReferenceShutter")
  /** @group Constructors */ case object GpiCalScienceShutter extends GiapiStatusApply("GpiCalScienceShutter", Instrument.Gpi, GiapiType.Int, "gpi:scienceShutter", "gpi:selectShutter.calScienceShutter")
  /** @group Constructors */ case object GpiEntranceShutter extends GiapiStatusApply("GpiEntranceShutter", Instrument.Gpi, GiapiType.Int, "gpi:omssEntranceShutter", "gpi:selectShutter.entranceShutter")
  /** @group Constructors */ case object GpiCalExitShutter extends GiapiStatusApply("GpiCalExitShutter", Instrument.Gpi, GiapiType.Int, "gpi:calExitShutter", "gpi:selectShutter.calExitShutter")
  /** @group Constructors */ case object GpiPupilCamera extends GiapiStatusApply("GpiPupilCamera", Instrument.Gpi, GiapiType.Int, "gpi:pupilViewingMirror", "gpi:selectPupilCamera.deploy")
  /** @group Constructors */ case object GpiSCPower extends GiapiStatusApply("GpiSCPower", Instrument.Gpi, GiapiType.Float, "gpi:artificialSourceSCpower", "gpi:selectSource.sourceSCpower")
  /** @group Constructors */ case object GpiSCAttenuation extends GiapiStatusApply("GpiSCAttenuation", Instrument.Gpi, GiapiType.Float, "gpi:artificialSourceSCDb", "gpi:selectSource.sourceSCatten")
  /** @group Constructors */ case object GpiSrcVis extends GiapiStatusApply("GpiSrcVis", Instrument.Gpi, GiapiType.Int, "gpi:artificialSourceVIS", "gpi:selectSource.sourceVis")
  /** @group Constructors */ case object GpiSrcIR extends GiapiStatusApply("GpiSrcIR", Instrument.Gpi, GiapiType.Int, "gpi:artificialSourceIR", "gpi:selectSource.sourceIr")
  /** @group Constructors */ case object GpiPolarizerDeplay extends GiapiStatusApply("GpiPolarizerDeplay", Instrument.Gpi, GiapiType.Int, "gpi:polarModulatorDeploy", "gpi:configPolarizer.deploy")
  /** @group Constructors */ case object GpiPolarizerAngle extends GiapiStatusApply("GpiPolarizerAngle", Instrument.Gpi, GiapiType.Float, "gpi:polarizerAngle", "gpi:configPolarizer.angle")
  /** @group Constructors */ case object GpiObservationMode extends GiapiStatusApply("GpiObservationMode", Instrument.Gpi, GiapiType.String, "gpi:observationMode", "gpi:observationMode.mode")
  /** @group Constructors */ case object GpiIFSFilter extends GiapiStatusApply("GpiIFSFilter", Instrument.Gpi, GiapiType.String, "gpi:ifsFilter", "gpi:ifs:selectIfsFilter.maskStr")
  /** @group Constructors */ case object GpiPPM extends GiapiStatusApply("GpiPPM", Instrument.Gpi, GiapiType.String, "gpi:ppmMask", "gpi:selectPupilPlaneMask.maskStr")
  /** @group Constructors */ case object GpiFPM extends GiapiStatusApply("GpiFPM", Instrument.Gpi, GiapiType.String, "gpi:fpmMask", "gpi:selectFocalPlaneMask.maskStr")
  /** @group Constructors */ case object GpiLyot extends GiapiStatusApply("GpiLyot", Instrument.Gpi, GiapiType.String, "gpi:lyotMask", "gpi:selectLyotMask.maskStr")
  /** @group Constructors */ case object GpiAlignAndCalib extends GiapiStatusApply("GpiAlignAndCalib", Instrument.Gpi, GiapiType.Int, "gpi:alignAndCalib.part1", "gpi:alignAndCalib.part1")
  /** @group Constructors */ case object GpiIFSReadMode extends GiapiStatusApply("GpiIFSReadMode", Instrument.Gpi, GiapiType.Int, "gpi:currentReadMode", "gpi:configIfs.readoutMode")
  /** @group Constructors */ case object GpiIFSStartX extends GiapiStatusApply("GpiIFSStartX", Instrument.Gpi, GiapiType.Int, "gpi:currentStartX", "gpi:gpi:configIfs.startx")
  /** @group Constructors */ case object GpiIFSStartY extends GiapiStatusApply("GpiIFSStartY", Instrument.Gpi, GiapiType.Int, "gpi:currentStartY", "gpi:gpi:configIfs.starty")
  /** @group Constructors */ case object GpiIFSEndX extends GiapiStatusApply("GpiIFSEndX", Instrument.Gpi, GiapiType.Int, "gpi:currentEndX", "gpi:gpi:configIfs.endx")
  /** @group Constructors */ case object GpiIFSEndY extends GiapiStatusApply("GpiIFSEndY", Instrument.Gpi, GiapiType.Int, "gpi:currentEndY", "gpi:gpi:configIfs.endy")

  /** All members of GiapiStatusApply, in canonical order. */
  val all: List[GiapiStatusApply] =
    List(GpiAdc, GpiUseAo, GpiAoOptimize, GpiUseCal, GpiFpmPinholeBias, GpiIntegrationTime, GpiNumCoadds, GpiMagI, GpiMagH, GpiCalEntranceShutter, GpiCalReferenceShutter, GpiCalScienceShutter, GpiEntranceShutter, GpiCalExitShutter, GpiPupilCamera, GpiSCPower, GpiSCAttenuation, GpiSrcVis, GpiSrcIR, GpiPolarizerDeplay, GpiPolarizerAngle, GpiObservationMode, GpiIFSFilter, GpiPPM, GpiFPM, GpiLyot, GpiAlignAndCalib, GpiIFSReadMode, GpiIFSStartX, GpiIFSStartY, GpiIFSEndX, GpiIFSEndY)

  /** Select the member of GiapiStatusApply with the given tag, if any. */
  def fromTag(s: String): Option[GiapiStatusApply] =
    all.find(_.tag === s)

  /** Select the member of GiapiStatusApply with the given tag, throwing if absent. */
  def unsafeFromTag(s: String): GiapiStatusApply =
    fromTag(s).getOrElse(throw new NoSuchElementException(s"GiapiStatusApply: Invalid tag: '$s'"))

  /** @group Typeclass Instances */
  implicit val GiapiStatusApplyEnumerated: Enumerated[GiapiStatusApply] =
    new Enumerated[GiapiStatusApply] {
      def all = GiapiStatusApply.all
      def tag(a: GiapiStatusApply) = a.tag
      override def unsafeFromTag(s: String): GiapiStatusApply =
        GiapiStatusApply.unsafeFromTag(s)
    }

}