package ohnosequences.db.rna16s.test

import java.io.File
import ohnosequences.api.rnacentral._

case object input {

  lazy val fasta: File =
    new File("/opt/data/rnacentral_species_specific_ids.fasta")

  lazy val idMapping: File =
    new File("/opt/data/id_mapping.tsv")

  lazy val rnaCentralData: RNACentralData =
    RNACentralData(
      speciesSpecificFasta = fasta,
      idMapping = idMapping
    )

  def rnaCentralEntries: Iterator[Entry] =
    iterators right (entries entriesFrom rnaCentralData)
}
