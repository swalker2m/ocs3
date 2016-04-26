package edu.gemini.seqexec.web.client.model

import diode.data.{Empty, Pot, PotAction}
import edu.gemini.seqexec.model.SeqexecEvent
import edu.gemini.seqexec.web.common.{Instrument, SeqexecQueue, Sequence}
import org.scalajs.dom.WebSocket

import scalaz._
import Scalaz._

import upickle.default._

// Actions

// Request loading the queue
case class UpdatedQueue(potResult: Pot[SeqexecQueue]) extends PotAction[SeqexecQueue, UpdatedQueue] {
  override def next(newResult: Pot[SeqexecQueue]) = {
    UpdatedQueue(newResult)
  }
}
// Request a search
case class SearchSequence(criteria: String, potResult: Pot[List[Sequence]] = Empty) extends PotAction[List[Sequence], SearchSequence] {
  override def next(newResult: Pot[List[Sequence]]) = {
    SearchSequence(criteria, newResult)
  }
}

// Actions to close and/open the search area
case object OpenSearchArea
case object CloseSearchArea

// Actions to close and/open the dev console area
case object ToggleDevConsole

// Action to add a sequence to the queue
case class AddToQueue(s: Sequence)
// Action to remove a sequence from the search results
case class RemoveFromSearch(s: Sequence)
// Action to select a sequence for display
case class SelectToDisplay(s: Sequence)

// Actions related to web sockets
case object ConnectionOpened
case object ConnectionClosed
case class NewMessage(s: String)
case class ConnectionError(s: String)

// End Actions

// UI model
sealed trait SectionVisibilityState
case object SectionOpen extends SectionVisibilityState
case object SectionClosed extends SectionVisibilityState

case class SequenceTab(instrument: Instrument.Instrument, sequence: Option[Sequence])

// Model for the tabbed area of sequences
case class SequencesOnDisplay(instrumentSequences: Zipper[SequenceTab]) {
  def select(s: Sequence):SequencesOnDisplay =
    // Focus on the given sequence if it exists, otherwise ignore it
    copy(instrumentSequences.findZor(_.sequence.exists(_ == s), instrumentSequences))

  def sequenceForInstrument(s: Sequence):SequencesOnDisplay = {
    // Replace the sequence for the instrument and focus
    val q = instrumentSequences.findZ(_.instrument === s.instrument).map(_.modify(_.copy(sequence = s.some)))
    copy(q | instrumentSequences)
  }
}

case class WebSocketsLog(log: List[SeqexecEvent]) {
  // Upper bound of accepted events or we may run out of memory
  val maxLength = 100
  def append(s: String):WebSocketsLog = copy((log :+ read[SeqexecEvent](s)).take(maxLength - 1))
}

object SequencesOnDisplay {
  val empty = SequencesOnDisplay(Instrument.instruments.map(SequenceTab(_, None)).toZipper)
}

/**
  * Root of the UI Model of the application
  */
case class SeqexecAppRootModel(queue: Pot[SeqexecQueue],
                               searchAreaState: SectionVisibilityState,
                               devConsoleState: SectionVisibilityState,
                               webSocketLog: WebSocketsLog,
                               searchResults: Pot[List[Sequence]],
                               sequencesOnDisplay: SequencesOnDisplay)

object SeqexecAppRootModel {
  val initial = SeqexecAppRootModel(Empty, SectionClosed, SectionClosed, WebSocketsLog(Nil), Empty, SequencesOnDisplay.empty)
}
