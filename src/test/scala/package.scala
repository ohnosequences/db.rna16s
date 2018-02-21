package ohnosequences.db.rna16s

import java.io.File
import ohnosequences.api.rnacentral._

package object test {

  type +[A, B] =
    Either[A, B]

  implicit final class PredicateOps[X](val p: X => Boolean) {

    def &&(other: X => Boolean): X => Boolean =
      x => p(x) && other(x)
  }

  val speciesSpecificFasta =
    new File("/opt/data/rnacentral_species_specific_ids.fasta")

  val idMapping =
    new File("/opt/data/id_mapping.tsv")

  val data: RNACentralData =
    RNACentralData(
      speciesSpecificFasta,
      idMapping
    )

  def allEntries: Iterator[Entry] =
    iterators right (entries entriesFrom data)

  val outFASTA: File =
    new File("/opt/data/db.rna16.test.fa")
}
