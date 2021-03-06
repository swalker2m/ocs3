// Copyright (c) 2016-2019 Association of Universities for Research in Astronomy, Inc. (AURA)
// For license information see LICENSE or https://opensource.org/licenses/BSD-3-Clause

package seqexec.server.gmos

import cats.data.Kleisli
import cats.data.EitherT
import cats.implicits._
import cats.effect.Sync
import gem.enum.LightSinkName
import gsp.math.Angle
import gsp.math.Offset
import gsp.math.syntax.string._
import edu.gemini.spModel.config2.Config
import edu.gemini.spModel.config2.ItemKey
import edu.gemini.spModel.gemini.gmos.GmosCommonType._
import edu.gemini.spModel.gemini.gmos.InstGmosCommon._
import edu.gemini.spModel.guide.StandardGuideOptions
import edu.gemini.spModel.obscomp.InstConstants.{EXPOSURE_TIME_PROP, _}
import edu.gemini.spModel.seqcomp.SeqConfigNames.{INSTRUMENT_KEY, OBSERVE_KEY}
import edu.gemini.spModel.gemini.gmos.GmosCommonType
import java.lang.{Double => JDouble, Integer => JInt}
import org.log4s.{Logger, getLogger}
import scala.concurrent.duration._
import seqexec.model.dhs.ImageFileId
import seqexec.model.enum.Guiding
import seqexec.model.enum.ObserveCommandResult
import seqexec.server.ConfigUtilOps.{ContentError, ConversionError, _}
import seqexec.server.gmos.Gmos.SiteSpecifics
import seqexec.server.gmos.GmosController.Config._
import seqexec.server.gmos.GmosController.Config.NSConfig
import seqexec.server.gmos.GmosController.SiteDependentTypes
import seqexec.server.keywords.{DhsInstrument, KeywordsClient}
import seqexec.server._
import squants.space.Length
import squants.{Seconds, Time}
import squants.space.LengthConversions._

abstract class Gmos[F[_]: Sync, T<:GmosController.SiteDependentTypes](controller: GmosController[F, T], ss: SiteSpecifics[T])(configTypes: GmosController.Config[T]) extends DhsInstrument[F] with InstrumentSystem[F] {
  import Gmos._
  import InstrumentSystem._

  override def sfName(config: Config): LightSinkName = LightSinkName.Gmos

  override val contributorName: String = "gmosdc"

  override val keywordsClient: KeywordsClient[F] = this

  override val observeControl: InstrumentSystem.ObserveControl[F] = CompleteControl(
    StopObserveCmd(controller.stopObserve),
    AbortObserveCmd(controller.abortObserve),
    PauseObserveCmd(controller.pauseObserve),
    ContinuePausedCmd{t: Time => controller.resumePaused(t)},
    StopPausedCmd(controller.stopPaused),
    AbortPausedCmd(controller.abortPaused)
  )

  val Log: Logger = getLogger

  protected def fpuFromFPUnit(n: Option[T#FPU], m: Option[String])(fpu: FPUnitMode): GmosFPU = fpu match {
    case FPUnitMode.BUILTIN     => configTypes.BuiltInFPU(n.getOrElse(ss.fpuDefault))
    case FPUnitMode.CUSTOM_MASK => m match {
      case Some(u) => GmosController.Config.CustomMaskFPU(u)
      case _       => GmosController.Config.UnknownFPU
    }
  }

  // The OT lets beams up to G but in practice it is always A/B
  private val BeamLabels = List('A, 'B, 'C, 'D, 'E, 'F, 'G)

  def configToAngle(s: String): Either[ExtractFailure, Angle] =
    s.parseDoubleOption.toRight(ContentError("Invalid offset value")).map(Angle.fromDoubleArcseconds(_))

  def extractGuiding(config: Config, k: ItemKey): Either[ExtractFailure, Guiding] =
    config
      .extractAs[StandardGuideOptions.Value](k)
      .flatMap(r => Guiding.fromString(r.toString).toRight(KeyNotFound(k)))
      .orElse {
        config.extractAs[String](k).flatMap(Guiding.fromString(_).toRight(KeyNotFound(k)))
      }

  private def nsPosition(config: Config, sc: Int): Either[ExtractFailure, Vector[NSPosition]] = {
    (for {
      i <- 0 to scala.math.min(BeamLabels.length, sc) - 1
    } yield {
      for {
        s <- BeamLabels.lift(i).toRight(ContentError(s"Unknown label at position $i"))
        p <- config.extractAs[String](INSTRUMENT_KEY / s"nsBeam${s.name}-p").flatMap(configToAngle).map(Offset.P.apply)
        q <- config.extractAs[String](INSTRUMENT_KEY / s"nsBeam${s.name}-q").flatMap(configToAngle).map(Offset.Q.apply)
        k = INSTRUMENT_KEY / s"nsBeam${s.name}-guideWithOIWFS"
        g <- extractGuiding(config, k)
      } yield NSPosition(s, Offset(p, q), g)
    }).toVector.sequence
  }

  private def nodAndShuffle(config: Config): Either[ExtractFailure, NSConfig] =
    for {
      cycles <- config.extractAs[JInt](INSTRUMENT_KEY / NUM_NS_CYCLES_PROP).map(_.toInt)
      rows   <- config.extractAs[JInt](INSTRUMENT_KEY / DETECTOR_ROWS_PROP).map(_.toInt)
      sc     <- config.extractAs[JInt](INSTRUMENT_KEY / NS_STEP_COUNT_PROP_NAME)
      pos    <- nsPosition(config, sc)
    } yield NSConfig.NodAndShuffle(cycles, rows, pos)

  private def calcDisperser(disp: T#Disperser, order: Option[DisperserOrder], wl: Option[Length])
  : Either[ConfigUtilOps.ExtractFailure, configTypes.GmosDisperser] =
    if(configTypes.isMirror(disp))
      configTypes.GmosDisperser.Mirror.asRight[ConfigUtilOps.ExtractFailure]
    else order.map{o =>
      if(o === GmosCommonType.Order.ZERO)
        configTypes.GmosDisperser.Order0(disp).asRight[ConfigUtilOps.ExtractFailure]
      else wl.map(w => configTypes.GmosDisperser.OrderN(disp, o, w)
        .asRight[ConfigUtilOps.ExtractFailure]).getOrElse(
          ConfigUtilOps.ContentError(s"Disperser order ${o.displayValue} is missing a wavelength.")
          .asLeft
        )
    }.getOrElse(ConfigUtilOps.ContentError(s"Disperser is missing an order.").asLeft)

  private def ccConfigFromSequenceConfig(config: Config): TrySeq[configTypes.CCConfig] =
    (for {
      filter           <- ss.extractFilter(config)
      disp             <- ss.extractDisperser(config)
      disperserOrder   =  config.extractAs[DisperserOrder](INSTRUMENT_KEY / DISPERSER_ORDER_PROP)
      disperserLambda  =  config.extractAs[JDouble](INSTRUMENT_KEY / DISPERSER_LAMBDA_PROP).map(_.toDouble.nanometers)
      fpuName          =  ss.extractFPU(config)
      fpuMask          =  config.extractAs[String](INSTRUMENT_KEY / FPU_MASK_PROP)
      fpu              <- config.extractAs[FPUnitMode](INSTRUMENT_KEY / FPU_MODE_PROP).map(fpuFromFPUnit(fpuName.toOption, fpuMask.toOption))
      stageMode        <- ss.extractStageMode(config)
      dtax             <- config.extractAs[DTAX](INSTRUMENT_KEY / DTAX_OFFSET_PROP)
      adc              <- config.extractAs[ADC](INSTRUMENT_KEY / ADC_PROP)
      electronicOffset =  config.extractAs[UseElectronicOffset](INSTRUMENT_KEY / USE_ELECTRONIC_OFFSETTING_PROP)
      disperser        <- calcDisperser(disp, disperserOrder.toOption, disperserLambda.toOption)
    } yield configTypes.CCConfig(filter, disperser, fpu, stageMode, dtax, adc, electronicOffset.toOption)).leftMap(e => SeqexecFailure.Unexpected(ConfigUtilOps.explain(e)))

  private def nsConfigFromSequenceConfig(config: Config): TrySeq[NSConfig] =
    (for {
      useNS            <- config.extractAs[java.lang.Boolean](INSTRUMENT_KEY / USE_NS_PROP)
      ns               <- (if (useNS) nodAndShuffle(config) else NSConfig.NoNodAndShuffle.asRight)
    } yield ns).leftMap(e => SeqexecFailure.Unexpected(ConfigUtilOps.explain(e)))

  private def fromSequenceConfig(config: Config): Either[SeqexecFailure, GmosController.GmosConfig[T]] =
    for {
      cc <- ccConfigFromSequenceConfig(config)
      dc <- dcConfigFromSequenceConfig(config)
      ns <- nsConfigFromSequenceConfig(config)
    } yield new GmosController.GmosConfig[T](configTypes)(cc, dc, ns)

  override def calcStepType(config: Config): Either[SeqexecFailure, StepType] =
    if (Gmos.isNodAndShuffle(config)) {
      StepType.NodAndShuffle(instrument).asRight
    } else {
      SequenceConfiguration.calcStepType(config)
    }

  override def observe(config: Config): Kleisli[F, ImageFileId, ObserveCommandResult] =
    Kleisli { fileId =>
      calcObserveTime(config).flatMap { x =>
        controller.observe(fileId, x)
      }
    }

  override def notifyObserveEnd: F[Unit] =
    controller.endObserve

  override def notifyObserveStart: F[Unit] = Sync[F].unit

  override def configure(config: Config): F[ConfigResult[F]] =
    EitherT.fromEither[F](fromSequenceConfig(config))
      .widenRethrowT
      .flatMap(controller.applyConfig)
      .as(ConfigResult(this))

  override def calcObserveTime(config: Config): F[Time] =
    Sync[F].delay(config.extractAs[JDouble](OBSERVE_KEY / EXPOSURE_TIME_PROP)
      .map(v => Seconds(v.toDouble)).getOrElse(Seconds(10000)))

  override def observeProgress(total: Time, elapsed: ElapsedTime): fs2.Stream[F, Progress] =
    controller
      .observeProgress(total, elapsed)
}

object Gmos {
  val name: String = INSTRUMENT_NAME_PROP

  trait SiteSpecifics[T<:SiteDependentTypes] {
    def extractFilter(config: Config): Either[ExtractFailure, T#Filter]

    def extractDisperser(config: Config): Either[ExtractFailure, T#Disperser]

    def extractFPU(config: Config): Either[ExtractFailure, T#FPU]

    def extractStageMode(config: Config): Either[ExtractFailure, T#GmosStageMode]

    val fpuDefault: T#FPU
  }

  def isNodAndShuffle(config: Config): Boolean =
    config.extractAs[java.lang.Boolean](INSTRUMENT_KEY / USE_NS_PROP)
      .map(_.booleanValue())
      .getOrElse(false)

  // It seems this is unused but it shows up on the DC apply config
  private def biasTimeObserveType(observeType: String): BiasTime = observeType match {
    case SCIENCE_OBSERVE_TYPE => BiasTime.BiasTimeUnset
    case FLAT_OBSERVE_TYPE    => BiasTime.BiasTimeUnset
    case ARC_OBSERVE_TYPE     => BiasTime.BiasTimeEmpty
    case DARK_OBSERVE_TYPE    => BiasTime.BiasTimeEmpty
    case BIAS_OBSERVE_TYPE    => BiasTime.BiasTimeUnset
    case _                    => BiasTime.BiasTimeUnset
  }

  private def shutterStateObserveType(observeType: String): ShutterState = observeType match {
    case SCIENCE_OBSERVE_TYPE => ShutterState.OpenShutter
    case FLAT_OBSERVE_TYPE    => ShutterState.OpenShutter
    case ARC_OBSERVE_TYPE     => ShutterState.OpenShutter
    case DARK_OBSERVE_TYPE    => ShutterState.CloseShutter
    case BIAS_OBSERVE_TYPE    => ShutterState.CloseShutter
    case _                    => ShutterState.UnsetShutter
  }

  private def customROIs(config: Config): List[ROI] = {
    def attemptROI(i: Int): Option[ROI] =
      (for {
        xStart <- config.extractAs[JInt](INSTRUMENT_KEY / s"customROI${i}Xmin").map(_.toInt)
        xRange <- config.extractAs[JInt](INSTRUMENT_KEY / s"customROI${i}Xrange").map(_.toInt)
        yStart <- config.extractAs[JInt](INSTRUMENT_KEY / s"customROI${i}Ymin").map(_.toInt)
        yRange <- config.extractAs[JInt](INSTRUMENT_KEY / s"customROI${i}Yrange").map(_.toInt)
      } yield new ROI(xStart, yStart, xRange, yRange)).toOption

    val rois = for {
      i <- 1 to 5
    } yield attemptROI(i)
    rois.toList.flattenOption
  }

  private def toGain(s: String): Either[ExtractFailure, Double] =
    s.parseDoubleOption
      .toRight(ConversionError(INSTRUMENT_KEY / AMP_GAIN_SETTING_PROP, "Bad Amp gain setting"))

  def dcConfigFromSequenceConfig(config: Config): TrySeq[DCConfig] =
    (for {
      obsType      <- config.extractAs[String](OBSERVE_KEY / OBSERVE_TYPE_PROP)
      biasTime     <- biasTimeObserveType(obsType).asRight
      shutterState <- shutterStateObserveType(obsType).asRight
      exposureTime <- config.extractAs[JDouble](OBSERVE_KEY / EXPOSURE_TIME_PROP).map(_.toDouble.seconds)
      ampReadMode  <- config.extractAs[AmpReadMode](AmpReadMode.KEY)
      gainChoice   <- config.extractAs[AmpGain](INSTRUMENT_KEY / AMP_GAIN_CHOICE_PROP)
      ampCount     <- config.extractAs[AmpCount](INSTRUMENT_KEY / AMP_COUNT_PROP)
      gainSetting  <- config.extractAs[String](INSTRUMENT_KEY / AMP_GAIN_SETTING_PROP).flatMap(toGain)
      xBinning     <- config.extractAs[Binning](INSTRUMENT_KEY / CCD_X_BIN_PROP)
      yBinning     <- config.extractAs[Binning](INSTRUMENT_KEY / CCD_Y_BIN_PROP)
      builtInROI   <- config.extractAs[BuiltinROI](INSTRUMENT_KEY / BUILTIN_ROI_PROP)
      customROI = if (builtInROI === BuiltinROI.CUSTOM) customROIs(config) else Nil
      roi          <- RegionsOfInterest.fromOCS(builtInROI, customROI).leftMap(e => ContentError(SeqexecFailure.explain(e)))
    } yield
      DCConfig(exposureTime, biasTime, shutterState, CCDReadout(ampReadMode, gainChoice, ampCount, gainSetting), CCDBinning(xBinning, yBinning), roi))
        .leftMap(e => SeqexecFailure.Unexpected(ConfigUtilOps.explain(e)))

 }
