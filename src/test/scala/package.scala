package ohnosequences.db.rna16s

import java.io.File
import ohnosequences.api.rnacentral._

package object test {

  val data: RNACentralData =
    RNACentralData(
      speciesSpecificFasta =
        new File("/opt/data/rnacentral_species_specific_ids.fasta"),
      idMapping = new File("/opt/data/id_mapping.tsv")
    )

  def allEntries: Iterator[Entry] =
    iterators right (entries entriesFrom data)

  val outFASTA: File =
    new File("/opt/data/db.rna16.test.fa")
}
