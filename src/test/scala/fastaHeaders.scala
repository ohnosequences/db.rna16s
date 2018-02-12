package ohnosequences.db.rna16s.test

import ohnosequences.api.rnacentral.Entry
import ohnosequences.fastarious._, fasta._

case object fastaFormat {

  val joinDescriptions: Entry => String =
    _.sequenceAnnotations.map(_.description).mkString(" | ")

  // TODO add DB ID and version
  val entryToFASTA: Entry => FASTA =
    entry =>
      FASTA(
        Header(s"${entry.rnaSequence.rnaID} ${joinDescriptions(entry)}"),
        Sequence(entry.rnaSequence.sequence)
    )
}
