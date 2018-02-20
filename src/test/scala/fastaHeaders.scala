package ohnosequences.db.rna16s.test

import ohnosequences.api.rnacentral.Entry
import ohnosequences.fastarious._, fasta._

/**
  Functions for generating a FASTA sequence out of an RNACentral entry.
  */
case object fastaFormat {

  /** join the set of original RNACentral FASTA headers */
  val joinDescriptions: Entry => String =
    _.sequenceAnnotations.map(_.description).mkString(" | ")

  /** FASTA sequence with all headers joined. */
  val entryToFASTA: Entry => FASTA =
    entry =>
      FASTA(
        Header(s"${entry.rnaSequence.rnaID} ${joinDescriptions(entry)}"),
        // Every 'u' or 'U' character is written as 'T'.
        Sequence(entry.rnaSequence.sequence.replaceAll("(?i)u", "T"))
    )
}
