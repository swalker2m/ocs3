// Copyright (c) 2016-2019 Association of Universities for Research in Astronomy, Inc. (AURA)
// For license information see LICENSE or https://opensource.org/licenses/BSD-3-Clause

package seqexec.server.niri

import cats.effect.{ IO, Timer }
import cats.implicits._
import edu.gemini.seqexec.server.niri.{Camera => JCamera}
import edu.gemini.seqexec.server.niri.{BeamSplitter => JBeamSplitter}
import edu.gemini.seqexec.server.niri.{Mask => JMask}
import edu.gemini.seqexec.server.niri.{Disperser => JDisperser}
import edu.gemini.seqexec.server.niri.{BuiltInROI => JBuiltInROI}
import edu.gemini.spModel.gemini.niri.Niri.Disperser
import edu.gemini.spModel.gemini.niri.Niri.Mask
import edu.gemini.spModel.gemini.niri.Niri.Filter
import edu.gemini.spModel.gemini.niri.Niri.Camera
import edu.gemini.spModel.gemini.niri.Niri.BeamSplitter
import edu.gemini.spModel.gemini.niri.Niri.BuiltinROI
import org.log4s.getLogger
import scala.concurrent.ExecutionContext
import seqexec.model.dhs.ImageFileId
import seqexec.model.enum.ObserveCommandResult
import seqexec.model.enum.ApplyCommandResult
import seqexec.server.{EpicsCodex, Progress, ProgressUtil, SeqexecFailure}
import seqexec.server.EpicsUtil._
import seqexec.server.EpicsCodex._
import seqexec.server.niri.NiriController._
import squants.{Seconds, Time}
import squants.time.TimeConversions._

trait NiriEncoders {

  implicit val focusEncoder: EncodeEpicsValue[Focus, String] = EncodeEpicsValue { _.getStringValue }

  implicit val cameraEncoder: EncodeEpicsValue[Camera, JCamera] = EncodeEpicsValue {
    case Camera.F6     => JCamera.F6
    case Camera.F14    => JCamera.F14
    case Camera.F32 |
         Camera.F32_PV => JCamera.F32
  }

  implicit val beamSplitterEncoder: EncodeEpicsValue[BeamSplitter, JBeamSplitter] =
    EncodeEpicsValue {
      case BeamSplitter.same_as_camera => JBeamSplitter.SameAsCamera
      case BeamSplitter.f6             => JBeamSplitter.F6
      case BeamSplitter.f14            => JBeamSplitter.F14
      case BeamSplitter.f32            => JBeamSplitter.F32
    }

  implicit val filterEncoder: EncodeEpicsValue[Filter, String] = EncodeEpicsValue {
    case Filter.BBF_Y            => "Y"
    case Filter.BBF_J            => "J"
    case Filter.BBF_H            => "H"
    case Filter.BBF_KPRIME       => "K(prime)"
    case Filter.BBF_KSHORT       => "K(short)"
    case Filter.BBF_K            => "K"
    case Filter.BBF_LPRIME       => "L(prime)"
    case Filter.BBF_MPRIME       => "M(prime)"
    case Filter.BBF_J_ORDER_SORT => "J order sort"
    case Filter.BBF_H_ORDER_SORT => "H order sort"
    case Filter.BBF_K_ORDER_SORT => "K order sort"
    case Filter.BBF_L_ORDER_SORT => "L order sort"
    case Filter.BBF_M_ORDER_SORT => "M order sort"
    case Filter.J_CONTINUUM_106  => "Jcon(1065)"
    case Filter.NBF_HEI          => "HeI"
    case Filter.NBF_PAGAMMA      => "Pa(gamma)"
    case Filter.J_CONTINUUM_122  => "Jcon(112)"
    case Filter.NBF_H            => "H"
    case Filter.NBF_PABETA       => "Pa(beta)"
    case Filter.NBF_HCONT        => "H-con(157)"
    case Filter.NBF_CH4SHORT     => "CH4(short)"
    case Filter.NBF_CH4LONG      => "CH4(long)"
    case Filter.NBF_FEII         => "FeII"
    case Filter.NBF_H2O_2045     => "H2Oice(2045)"
    case Filter.NBF_HE12P2S      => "HeI(2p2s)"
    case Filter.NBF_KCONT1       => "Kcon(227)"
    case Filter.NBF_H210         => "H2 1-0 S1"
    case Filter.NBF_BRGAMMA      => "Br(gamma)"
    case Filter.NBF_H221         => "H2 2-1 S1"
    case Filter.NBF_KCONT2       => "Kcon(227)"
    case Filter.NBF_CH4ICE       => "CH4ice(2275)"
    case Filter.NBF_CO20         => "CO 2-0(bh)"
    case Filter.NBF_CO31         => "CO 3-1(bh)"
    case Filter.NBF_H2O          => "H2Oice"
    case Filter.NBF_HC           => "hydrocarb"
    case Filter.NBF_BRACONT      => "Br(alpha)Con"
    case Filter.NBF_BRA          => "Br(alpha)"
  }

  implicit val maskEncoder: EncodeEpicsValue[Mask, JMask] = EncodeEpicsValue{
    case Mask.MASK_IMAGING => JMask.Imaging
    case Mask.MASK_1       => JMask.F6_2Pix_Center
    case Mask.MASK_2       => JMask.F6_4Pix_Center
    case Mask.MASK_3       => JMask.F6_6Pix_Center
    case Mask.MASK_4       => JMask.F6_2Pix_Blue
    case Mask.MASK_5       => JMask.F6_4Pix_Blue
    case Mask.MASK_6       => JMask.F6_6Pix_Blue
    case Mask.MASK_7 |
         Mask.MASK_8       => JMask.Polarimetry
    case Mask.MASK_9       => JMask.F32_4Pix_Center
    case Mask.MASK_10      => JMask.F32_7Pix_Center
    case Mask.MASK_11      => JMask.F6_2Pix_Center // f/32 10pix and f/6 2pix use the same mask
    case Mask.PINHOLE_MASK => JMask.PinHole
  }

  implicit val disperserEncoder: EncodeEpicsValue[Disperser, JDisperser] = EncodeEpicsValue{
    case Disperser.NONE      => JDisperser.None
    case Disperser.J         => JDisperser.J
    case Disperser.H         => JDisperser.H
    case Disperser.K         => JDisperser.K
    case Disperser.L         => JDisperser.L
    case Disperser.M         => JDisperser.M
    case Disperser.WOLLASTON => JDisperser.Wollaston
    case Disperser.J_F32     => JDisperser.F32_J
    case Disperser.H_F32     => JDisperser.F32_H
    case Disperser.K_F32     => JDisperser.F32_K
  }

  implicit val builtinRoiEncoder: EncodeEpicsValue[BuiltInROI, JBuiltInROI] = EncodeEpicsValue{
    case BuiltinROI.FULL_FRAME    => JBuiltInROI.FullFrame
    case BuiltinROI.CENTRAL_768   => JBuiltInROI.Central768
    case BuiltinROI.CENTRAL_512   => JBuiltInROI.Central512
    case BuiltinROI.CENTRAL_256   => JBuiltInROI.Central256
    case BuiltinROI.SPEC_1024_512 => JBuiltInROI.Spec1024x512
  }

}

object NiriControllerEpics extends NiriEncoders {
  private val Log = getLogger

  implicit val ioTimer: Timer[IO] =
    IO.timer(ExecutionContext.global)

  import EpicsCodex._

  private val epicsSys = NiriEpics.instance

  /**
   * The instrument has three filter wheels with a status channel for each one. But it does not have
   * a status channel for the virtual filter, so I have to calculate it. The assumption is that only
   * one wheel can be in a not open position at a given time.
   */
  private def currentFilter: IO[Option[String]] = {
    val filter1 = epicsSys.filter1
    val filter2 = epicsSys.filter2
    val filter3 = epicsSys.filter3
    val Open = "open"

    for {
      iof1 <- filter1
      iof2 <- filter2
      iof3 <- filter3
    } yield {
      (iof1, iof2, iof3).mapN{ (f1, f2, f3) =>
        val l = List(f1, f2, f3).filterNot(_ === Open)
        if(l.length === 1) l.headOption
        else none
      }.flatten.map(removePartName)
    }
  }

  private def setFocus(f: Focus): IO[Option[IO[Unit]]] = {
    val encoded = encode(f)
    smartSetParamF(encoded, epicsSys.focus, epicsSys.configCmd.setFocus(encoded))
  }

  private def setCamera(c: Camera): IO[Option[IO[Unit]]] = {
    val encoded = encode(c)
    smartSetParamF(encoded.toString, epicsSys.camera, epicsSys.configCmd.setCamera(encoded))
  }

  private def setBeamSplitter(b: BeamSplitter): IO[Option[IO[Unit]]] = {
    val encoded = encode(b)
    smartSetParamF(encoded.toString, epicsSys.beamSplitter,
      epicsSys.configCmd.setBeamSplitter(encoded))
  }

  private def setFilter(f: Filter): IO[Option[IO[Unit]]] = {
    val encoded = encode(f)

    smartSetParamF(encoded, currentFilter, epicsSys.configCmd.setFilter(encoded))
  }

  private def setBlankFilter: IO[Option[IO[Unit]]] = {
    val BlankFilter = "blank"

    smartSetParamF(BlankFilter, currentFilter, epicsSys.configCmd.setFilter(BlankFilter))
  }

  private def setMask(m: Mask): IO[Option[IO[Unit]]] = {
    val encoded = encode(m)

    smartSetParamF(encoded.toString, epicsSys.mask, epicsSys.configCmd.setMask(encoded))
  }

  private def setDisperser(d: Disperser): IO[Unit] = {
    val encoded = encode(d)

    // There is no status for the disperser
    epicsSys.configCmd.setDisperser(encoded)
  }

  val WindowOpen = "open"
  val WindowClosed = "closed"

  private def setWindowCover(pos: String): IO[Option[IO[Unit]]] =
    smartSetParamF(pos, epicsSys.windowCover, epicsSys.windowCoverConfig.setWindowCover(pos))

  private def setExposureTime(t: ExposureTime): IO[Option[IO[Unit]]] = {
    val ExposureTimeTolerance = 0.001
    smartSetDoubleParamF[IO](ExposureTimeTolerance)(t.toSeconds, epicsSys.integrationTime,
      epicsSys.configCmd.setExposureTime(t.toSeconds)
    )
  }

  private def setCoadds(n: Coadds): IO[Option[IO[Unit]]] =
    smartSetParamF(n, epicsSys.coadds, epicsSys.configCmd.setCoadds(n))

  private def setROI(r: BuiltInROI): IO[Unit] = {
    val encoded = encode(r)

    // There is no status for the builtin ROI
    epicsSys.configCmd.setBuiltInROI(encoded)
  }

  // There is no status for the read mode
  private def setReadMode(rm: ReadMode): IO[Unit] =
    epicsSys.configCmd.setReadMode(rm)

  private def configDC(cfg: DCConfig): List[IO[Option[IO[Unit]]]] =
    List(
      setExposureTime(cfg.exposureTime),
        setCoadds(cfg.coadds),
        setReadMode(cfg.readMode).wrapped,
        setROI(cfg.builtInROI).wrapped)

  private def configCommonCC(cfg: Common): List[IO[Option[IO[Unit]]]] =
    List(
      setBeamSplitter(cfg.beamSplitter),
      setCamera(cfg.camera),
      setDisperser(cfg.disperser).wrapped,
      setFocus(cfg.focus),
      setMask(cfg.mask))

  private def configDarkCC(cfg: Dark): List[IO[Option[IO[Unit]]]] =
    List(
      setWindowCover(WindowClosed),
      setBlankFilter) ++
      configCommonCC(cfg.common)

  private def configIlluminatedCC(cfg: Illuminated): List[IO[Option[IO[Unit]]]] =
    List(
      setWindowCover(WindowOpen),
      setFilter(cfg.filter)) ++
      configCommonCC(cfg.common)

  private def configCC(cfg: CCConfig): List[IO[Option[IO[Unit]]]] = cfg match {
    case d@Dark(_)           => configDarkCC(d)
    case i@Illuminated(_, _) => configIlluminatedCC(i)
  }

  def calcObserveTimeout(cfg: DCConfig): IO[Time] = {
    epicsSys.minIntegration.map { t =>
      val MinIntTime = t.map(Seconds(_)).getOrElse(0.seconds)
      val CoaddOverhead = 2.5
      val TotalOverhead = 30.seconds

      (cfg.exposureTime + MinIntTime) * cfg.coadds.toDouble * CoaddOverhead + TotalOverhead
    }
  }

  private val ConfigTimeout: Time = Seconds(180)
  private val DefaultTimeout: Time = Seconds(60)

  def apply(): NiriController[IO] = new NiriController[IO] {
    private def actOnDHSNotConected(act: IO[Unit]): IO[Unit] =
      epicsSys.dhsConnected.map(_.exists(identity)).ifM(IO.unit, act)

    private def actOnArrayNotActive(act: IO[Unit]): IO[Unit] =
      epicsSys.arrayActive.map(_.exists(identity)).ifM(IO.unit, act)

    private def failOnDHSNotConected: IO[Unit] =
      actOnDHSNotConected(IO.raiseError(SeqexecFailure.Execution("NIRI is not connected to DHS")))

    private def failOnArrayNotActive: IO[Unit] =
      actOnArrayNotActive(IO.raiseError(SeqexecFailure.Execution("NIRI detector array is not active")))

    private def warnOnDHSNotConected: IO[Unit] =
      actOnDHSNotConected(IO(Log.warn("NIRI is not connected to DHS")))

    private def warnOnArrayNotActive: IO[Unit] =
      actOnArrayNotActive(IO(Log.warn("NIRI detector array is not active")))

    override def applyConfig(config: NiriController.NiriConfig): IO[Unit] = {
      val paramsDC = configDC(config.dc)
      val params =  paramsDC ++ configCC(config.cc)

      val cfgActions1 = if(params.isEmpty) IO.pure(ApplyCommandResult.Completed)
                        else executeIfNeeded(params,
                          epicsSys.configCmd.setTimeout[IO](ConfigTimeout) *>
                          epicsSys.configCmd.post[IO])
      // Weird NIRI behavior. The main IS apply is nor connected to the DC apply, but triggering the
      // IS apply writes the DC parameters. So to configure the DC, we need to set the DC parameters
      // in the IS, trigger the IS apply, and then trigger the DC apply.
      val cfgActions = if(paramsDC.isEmpty) cfgActions1
                       else cfgActions1 *>
                         epicsSys.configDCCmd.setTimeout[IO](DefaultTimeout) *>
                         epicsSys.configDCCmd.post[IO]

      IO(Log.debug("Starting NIRI configuration")) *>
        warnOnDHSNotConected *>
        warnOnArrayNotActive *>
        cfgActions *>
        IO(Log.debug("Completed NIRI configuration"))
    }

    override def observe(fileId: ImageFileId, cfg: DCConfig): IO[ObserveCommandResult] =
      IO(Log.info("Start NIRI observe")) *>
        failOnDHSNotConected *>
        failOnArrayNotActive *>
        epicsSys.observeCmd.setLabel(fileId) *>
        calcObserveTimeout(cfg).flatMap(epicsSys.observeCmd.setTimeout[IO]) *>
        epicsSys.observeCmd.post[IO]

    override def endObserve: IO[Unit] =
      IO(Log.debug("Send endObserve to NIRI")) *>
        epicsSys.endObserveCmd.setTimeout[IO](DefaultTimeout) *>
        epicsSys.endObserveCmd.mark[IO] *>
        epicsSys.endObserveCmd.post[IO].void

    override def stopObserve: IO[Unit] =
      IO(Log.info("Stop NIRI exposure")) *>
        epicsSys.stopCmd.setTimeout[IO](DefaultTimeout) *>
        epicsSys.stopCmd.mark[IO] *>
        epicsSys.stopCmd.post[IO].void

    override def abortObserve: IO[Unit] =
      IO(Log.info("Abort NIRI exposure")) *>
        epicsSys.abortCmd.setTimeout[IO](DefaultTimeout) *>
        epicsSys.abortCmd.mark[IO] *>
        epicsSys.abortCmd.post[IO].void

    override def observeProgress(total: Time): fs2.Stream[IO, Progress] =
      ProgressUtil.countdown[IO](total, 0.seconds)

    override def calcTotalExposureTime(cfg: DCConfig): IO[Time] =
      epicsSys.minIntegration.map { f =>
        val MinIntTime = f.map(Seconds(_)).getOrElse(0.seconds)

        (cfg.exposureTime + MinIntTime) * cfg.coadds.toDouble
      }

  }
}
