/*
  # Drop inconsistent assignments

  Here we want to drop "wrong", in the sense of *inconsistent*, assignments from our database. But, what is a wrong assignment? Let's see first an example:

  ## Example of a wrong assignment

  Let's consider the following fragment of the taxonomic tree:

  ```
  tribeX
  ├─ genusABC
  │  ├─ ...
  │  ...
  │  └─ subgenusABC
  │     ├─ speciesA
  │     │  ├─ subspeciesA1
  │     │  └─ subspeciesA2
  │     ├─ speciesB1
  │     ├─ speciesB2
  │     └─ speciesC
  ...
  └─ ...
     └─ ...
        └─ speciesX
  ```

  And a sequence with following taxonomic assignments:

  | ID   | Taxas                                |
  |:-----|:-------------------------------------|
  | seqA | subspeciesA1; subspeciesA2; speciesX |

  In this case `speciesX` is *likely* a wrong assignment, because it's completely *unrelated* with the other, more specific assignments. If we will leave it in the database, the LCA of these nodes will be `tribeX`, instead of `speciesA`.

  ## The definition of wrong

  How could we detect something like the example before? if we have enough similar sequences in the database which are correctly assigned, we could

  1. calculate (by sequence similarity) to what this sequence gets assigned using all the other sequences as a reference
  2. see, for each of the original assignments, if they are "close" to the newly computed assignment; drop those which are "far" in the tree

  Continuing with the example before, we run MG7, and BLAST tells us that `seqA` is *very* similar to `seqB` and `seqC` with the following assignments:

  | ID   | Taxas                |
  |:-----|:---------------------|
  | seqB | speciesB1; speciesB2 |
  | seqC | speciesC             |

  We take their LCA which is `subgenusABC` and look at its parent: `genusABC`. Each of the `seqA`'s assignments has to be a descendant of `genusABC` and `speciesX`, obviously, is not, so we discard it:

  | ID   | Taxas                      |
  |:-----|:---------------------------|
  | seqA | subspeciesA1; subspeciesA2 |

  ## How it works

  This step actually consists in two separate steps:

  1. We run MG7 with input the output from the drop redundant assignments step, and as reference database the same but the sequence we are using as query.
  2. For each sequence we check the relation of its assignments with the corresponding LCA that we've got from MG7. If some assignment is too far away from the LCA in the taxonomic tree, it is discarded. After this step the BLAST database is generated again.

  Almost all `99.8%` of the sequences from the drop redundant assignments step pass  this filter, because it's mostly about filtering out *wrong* assignments and there are not many sequences that get all assignments discarded.
*/
package ohnosequences.db.rna16s.test

import ohnosequences.db._, csvUtils._, collectionUtils._
import ohnosequences.ncbitaxonomy._, titan._
import ohnosequences.fastarious.fasta._
import ohnosequences.statika._
import ohnosequences.mg7._
import ohnosequences.awstools.s3._
import com.amazonaws.auth._
import com.amazonaws.services.s3.transfer._
import ohnosequences.blast.api._, outputFields._
import com.github.tototoshi.csv._
import better.files._

case object dropInconsistentAssignments extends FilterDataFrom(dropRedundantAssignments)(
  deps = clusteringResults, ncbiTaxonomyBundle
) {

  private lazy val taxonomyGraph = ncbiTaxonomyBundle.graph

  // type BlastRow = csv.Row[mg7.parameters.blastOutRec.Keys]

  /* Mapping of sequence IDs to the list of their taxonomic assignments */
  private lazy val referenceMap: Map[ID, Seq[Taxon]] = source.table.csvReader.iterator
    .foldLeft(Map[ID, Seq[Taxon]]()) { (acc, row) =>
      acc.updated(
        row(0),
        row(1).split(';').map(_.trim).toSeq
      )
    }

  /* Mapping of sequence IDs to corresponding FASTA sequences */
  private lazy val id2fasta: Map[ID, Fasta] = source.fasta.stream
    .foldLeft(Map[ID, Fasta]()) { (acc, fasta) =>
      acc.updated(
        fasta.getV(header).id,
        fasta
      )
    }

  private def referenceTaxaFor(id: ID): Seq[Taxon] = referenceMap.get(id).getOrElse(Seq())

  // TODO: this should be a piece of reusable code in MG7
  private def getAccumulatedCounts(taxa: Seq[Taxon]): Map[Taxon, (Int, Seq[Taxon])] = {
    // NOTE: this mutable map is used in getLineage for memoization of the results that we get from the DB
    val lineageMap: scala.collection.mutable.Map[Taxon, Taxa] = scala.collection.mutable.Map()

    def getLineage(id: Taxon): Taxa = lineageMap.get(id).getOrElse {
      val lineage = taxonomyGraph.getTaxon(id)
        .map{ _.ancestors }.getOrElse( Seq() )
        .map{ _.id }
      lineageMap.update(id, lineage)
      lineage
    }

    val counter = loquats.countDataProcessing()

    val directMap      = counter.directCounts(taxa, getLineage)
    val accumulatedMap = counter.accumulatedCounts(directMap, getLineage)

    accumulatedMap
  }

  /* This constant determines how many levels up from the considered node will be the ancestor that we want to take for comparing their cumulative counts */
  // FIXME: this constant needs a review
  val ancestorLevel: Int = 1 // i.e. direct parent by default

  /* This threshold determines minimum percentage of the cumulative count of the considered taxon from its ancestor's count */
  // FIXME: this constant needs a review
  val countsPercentageMinimum: Double = 42.75

  /* This predicate discards those taxons that are underrepresented by comparing its cumulative count to its ancestor's count */
  private def predicate(countsMap: Map[Taxon, (Int, Seq[Taxon])], totalCount: Int): Taxon => Boolean = { taxon =>

    countsMap.get(taxon).flatMap { case (_, lineage) =>

      val ancestorOpt = lineage.drop(ancestorLevel).headOption

      ancestorOpt.flatMap(countsMap.get).map { case (ancestorCount, _) =>

        ((ancestorCount: Double) / totalCount * 100) >= countsPercentageMinimum
      }
    }.getOrElse(false)
  }

  def filterData(): Unit = {

    clusteringResults.clusters.lines
      .foreach { line =>

        val ids: Seq[ID] = line.split(',')

        val taxa: Seq[Taxon] = ids.flatMap { id => referenceTaxaFor(id) }
        val accumulatedCountsMap = getAccumulatedCounts(taxa)
        val totalCount = taxa.length

        // checking each assignment of the query sequence
        ids.foreach { id =>

          val (acceptedTaxa, rejectedTaxa) = referenceTaxaFor(id).partition(
            predicate(accumulatedCountsMap, totalCount)
          )

          writeOutput(
            id,
            acceptedTaxa,
            rejectedTaxa,
            id2fasta(id)
          )
        }
      }

  }

}

case object dropInconsistentAssignmentsAndGenerate extends FilterAndGenerateBlastDB(
  ohnosequences.db.rna16s.dbName,
  ohnosequences.db.rna16s.dbType,
  ohnosequences.db.rna16s.test.dropInconsistentAssignments
)
