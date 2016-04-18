package edu.gemini.seqexec.web.server.http4s

import edu.gemini.pot.sp.SPObservationID
import edu.gemini.seqexec.server.SeqexecFailure.Unexpected
import edu.gemini.seqexec.server.{ExecutorImpl, SeqexecFailure}
import org.http4s._
import org.http4s.dsl._
import edu.gemini.seqexec.web.common._
import edu.gemini.seqexec.web.server.model.CannedModel
import upickle.default._
import edu.gemini.seqexec.web.server.model.Conversions._

import scalaz._
import Scalaz._

/**
  * Rest Endpoints under the /api route
  */
object SeqexecUIApiRoutes {
  val service = HttpService {
    case req @ GET -> Root  / "seqexec" / "current" / "queue" =>
      Ok(write(CannedModel.currentQueue))
    case req @ GET -> Root  / "seqexec" / "sequence" / id =>
      val r = for {
        obsId <- \/.fromTryCatchNonFatal(new SPObservationID(id)).leftMap((t:Throwable) => Unexpected(t.getMessage))
        s     <- ExecutorImpl.read(obsId)
      } yield (obsId, s)

      r match {
        case \/-((i, s)) => Ok(write(List(Sequence(i.stringValue(), SequenceState.NotRunning, "Flamingos2", s.toSequenceSteps, None))))
        case -\/(e)      => NotFound(SeqexecFailure.explain(e))
      }
  }
}