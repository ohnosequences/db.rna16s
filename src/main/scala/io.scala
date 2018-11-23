package ohnosequences.db.rna16s

import ohnosequences.db.rnacentral.Entry
import ohnosequences.fastarious._, fasta._
import ohnosequences.files.Lines

case object io {

  // Functions for generating a FASTA sequence out of an RNACentral entry.

  /** join the set of original RNACentral FASTA headers */
  private val joinDescriptions: Entry => String =
    _.sequenceAnnotations.map(_.description).mkString(" | ")

  /** FASTA sequence with all headers joined. */
  val entryToFASTA: Entry => FASTA =
    entry =>
      FASTA(
        Header(s"${entry.rnaSequence.rnaID} ${joinDescriptions(entry)}"),
        // All upper case, U -> T
        Sequence(entry.rnaSequence.sequence.toUpperCase.replace('U', 'T'))
    )

  deserializeMappings(lines: Lines): Mappings =
    lines.map { line =>
      val values = line.split("†")

      val rnaID  = values(0)
      val taxIDs = values(1).split(",").toSet

      (rnaID, taxIDs)
    }.toMap

  serializeMappings(mappings: Mappings): Lines =
    mappings.toIterator.map {
      case (rnaID, taxIDs) =>
        rnaID.toString ++ "†" ++ taxIDs.mkString(",")
    })
}
