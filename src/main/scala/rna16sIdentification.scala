package ohnosequences.db.rna16s

import ohnosequences.db.rnacentral._

case object rna16sIdentification {

  /** determines which entries are included in the db.rna16s database.

    This function will first keep only annotations from [[database.includedDatabases]], and then apply the [[annotation$]] and [[sequence$]] filters.

    Entries are dropped in a fail-fast fashion: entries dropped first could *also* be dropped by any of the subsequent filters.
    */
  lazy val is16s: Entry => Dropped + Entry =
    database
      .filterIncluded(_)
      .right
      .flatMap(annotation.hasRNAType16s)
      .right
      .flatMap(annotation.someDescriptionContains16s)
      .right
      .flatMap(sequence.has16sLength)
      .right
      .flatMap(sequence.qualityOK)

  /** Database filters */
  case object database {

    /** Databases from which we take sequences and annotations. */
    lazy val includedDatabases: Set[Database] = {

      import Database._

      Set(
        ENA,
        RefSeq,
        GreenGenes,
        RDP,
        Rfam
      )
    }

    /** Keep only entry and sequence annotations coming from [[includedDatabases]].

    The full `Entry` will be dropped if it doesn't have at least one annotation of both types (entry and sequence) coming from [[includedDatabases]].
      */
    lazy val filterIncluded: Entry => Dropped + Entry =
      entry => {

        val includedEntryAnnotations =
          entry.entryAnnotations
            .filter { a =>
              includedDatabases contains a.databaseEntry.database
            }

        val includedSequenceAnnotations =
          entry.sequenceAnnotations
            .filter { sa =>
              includedEntryAnnotations exists {
                _.ncbiTaxonomyID == sa.ncbiTaxonomyID
              }
            }

        if (includedEntryAnnotations.isEmpty || includedSequenceAnnotations.isEmpty)
          Left(Dropped.FromExcludedDBs(entry))
        else
          Right(
            entry.copy(
              entryAnnotations = includedEntryAnnotations,
              sequenceAnnotations = includedSequenceAnnotations
            )
          )
      }
  }

  case object sequence {

    /** Keep the `Entry` if it [[hasLength16s]]. */
    lazy val has16sLength: Entry => Dropped + Entry =
      entry =>
        if (hasLength16s(entry))
          Right(entry)
        else
          Left(
            Dropped.InvalidSequenceLength(entry.rnaSequence.sequence.length,
                                          entry))

    /** true if it lies in the accepted 16S sequence length interval. */
    lazy val length16s: Int => Boolean =
      len => len >= 1300 && len <= 1700

    /** checks if an `Entry` length is within the accepted 16S length interval. @see [[length16s]]*/
    lazy val hasLength16s: Entry => Boolean =
      entry => length16s(entry.rnaSequence.sequence.length)

    /** Keep the `Entry` if the sequence is formed only by 'A's, 'T's, 'C's and 'G's. */
    lazy val qualityOK: Entry => Dropped + Entry =
      entry =>
        if (!hasAmbiguousCharacters(entry.rnaSequence.sequence)) Right(entry)
        else Left(Dropped.LowQualitySequence(entry))

    /** Check whether a sequence has characters not in `{'A', 'T', 'U', 'C', 'G'}` */
    lazy val hasAmbiguousCharacters: String => Boolean =
      _.exists { char =>
        !"ATCGU".contains(char.toUpper)
      }
  }

  case object annotation {

    /** the entry is dropped if *none* of the entry annotations have [[rnaType16s]]. */
    lazy val hasRNAType16s: Entry => Dropped + Entry =
      entry =>
        if (entry.entryAnnotations exists { _.rnaType == rnaType16s })
          Right(entry)
        else
          Left(Dropped.isNotrRNA(entry))

    /** The `RNAType` which a 16S sequence is required to have. */
    lazy val rnaType16s: RNAType =
      RNAType.rRNA

    /**  */
    lazy val someDescriptionContains16s: Entry => Dropped + Entry =
      entry =>
        if (entry.sequenceAnnotations exists { a =>
              contains16sStr(a.description)
            })
          Right(entry)
        else
          Left(Dropped.No16sAnnotation(entry))

    lazy val contains16sStr: String => Boolean =
      _.toUpperCase containsSlice "16S"
  }

  sealed abstract class Dropped {

    def entry: Entry
  }
  case object Dropped {

    final case class FromExcludedDBs(entry: Entry) extends Dropped
    final case class isNotrRNA(entry: Entry)       extends Dropped
    final case class No16sAnnotation(entry: Entry) extends Dropped
    final case class InvalidSequenceLength(length: Int, entry: Entry)
        extends Dropped
    final case class LowQualitySequence(entry: Entry) extends Dropped
  }
}
