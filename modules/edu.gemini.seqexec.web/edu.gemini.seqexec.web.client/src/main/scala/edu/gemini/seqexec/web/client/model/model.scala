package edu.gemini.seqexec.web.client.model

import diode.data.{Empty, Pot, PotAction}
import edu.gemini.seqexec.web.common.{Instrument, SeqexecQueue, Sequence}

import scalaz._
import Scalaz._

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

// Action to add a sequence to the queue
case class AddToQueue(s: Sequence)
// Action to remove a sequence from the search results
case class RemoveFromSearch(s: Sequence)
// Action to select a sequence for display
case class SelectToDisplay(s: Sequence)

// End Actions

// UI model
sealed trait SearchAreaState
case object SearchAreaOpen extends SearchAreaState
case object SearchAreaClosed extends SearchAreaState

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

object SequencesOnDisplay {
  val empty = SequencesOnDisplay(Instrument.instruments.map(SequenceTab(_, None)).toZipper)
}

/**
  * Root of the UI Model of the application
  */
case class SeqexecAppRootModel(queue: Pot[SeqexecQueue], searchAreaState: SearchAreaState, searchResults: Pot[List[Sequence]], sequencesOnDisplay: SequencesOnDisplay)