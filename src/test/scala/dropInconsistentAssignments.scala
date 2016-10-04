/*
  # Drop inconsistent assignments

  Here we want to drop *inconsistent* assignments from our database. The idea here is to determine it based on sequences equivalence classes (1) (the we got from the previous clustering step) and their assignments taxonomic similarity (2).

  1. First we take the sequence clusters and for each of them (`C`) construct a common set of assignments (using the map we have from the previous steps): `Taxa(C)`.

  2. Then we consider each assignment `T` (from the old map) and decide wheter to drop it or to keep by comparing _its parent's_ cumulative count `Count(Parent(T))` with the total count (which is the size of `Taxa(C)`). If it's over some fixed threshold, we keep it, otherwise drop.
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

  lazy val taxonomyGraph = ncbiTaxonomyBundle.graph

  /* Mapping of sequence IDs to the list of their taxonomic assignments */
  lazy val referenceMap: Map[ID, Seq[Taxon]] = source.table.csvReader.iterator
    .foldLeft(Map[ID, Seq[Taxon]]()) { (acc, row) =>
      acc.updated(
        row(0),
        row(1).split(';').map(_.trim).toSeq
      )
    }

  /* Mapping of sequence IDs to corresponding FASTA sequences */
  lazy val id2fasta: Map[ID, Fasta] = source.fasta.stream
    .foldLeft(Map[ID, Fasta]()) { (acc, fasta) =>
      acc.updated(
        fasta.getV(header).id,
        fasta
      )
    }

  def referenceTaxaFor(id: ID): Seq[Taxon] = referenceMap.get(id).getOrElse(Seq())

  /* This method for a given sequence of taxons (with repeats) returns their cumulative counts and lineages */
  // TODO: this should be a piece of reusable code in MG7
  def getAccumulatedCounts(taxa: Seq[Taxon]): Map[Taxon, (Int, Seq[Taxon])] = {
    // NOTE: this mutable map is used in getLineage for memoization of the results that we get from the DB
    val cacheMap: scala.collection.mutable.Map[Taxon, Taxa] = scala.collection.mutable.Map()

    /* This method looks up the lineage in the cache or queries the DB and updates the cache */
    def getLineage(id: Taxon): Taxa = cacheMap.get(id).getOrElse {
      val lineage = taxonomyGraph.getTaxon(id)
        .map{ _.ancestors }.getOrElse( Seq() )
        .map{ _.id }
      cacheMap.update(id, lineage)
      lineage
    }

    val counter = loquats.countDataProcessing()

    val directMap      = counter.directCounts(taxa, getLineage)
    val accumulatedMap = counter.accumulatedCounts(directMap, getLineage)

    accumulatedMap
  }

  /* This threshold determines minimum percentage of the taxon's parent's count compared to the total count */
  // FIXME: this constant needs a review
  val countsPercentageMinimum: Double = 42.75 // % minimum

  /* This predicate determines whether to drop the taxon or to keep it */
  def predicate(countsMap: Map[Taxon, (Int, Seq[Taxon])], totalCount: Int): Taxon => Boolean = { taxon =>

    /* First we find `taxon` in the `countsMap` to get its lineage */
    countsMap.get(taxon).flatMap { case (_, lineage) =>

      /* and take its direct parent */
      val ancestorOpt = lineage.drop(1).headOption

      /* then we find out its count */
      ancestorOpt.flatMap(countsMap.get).map { case (ancestorCount, _) =>

        /* and compare it with the total: it has to be more than `countsPercentageMinimum`% for `taxon` to pass */
        ((ancestorCount: Double) / totalCount) >= (countsPercentageMinimum / 100)
      }
    }.getOrElse(false)
  }

  def filterData(): Unit = {

    clusteringResults.clusters.lines
      .foreach { line =>

        /* Sequence IDs of the cluster */
        val ids: Seq[ID] = line.split(',')
        // println(s"Processing cluster: ${ids.mkString(", ")}")

        /* Corresponding taxa (to all sequence in the cluster together) */
        val taxa: Seq[Taxon] = ids.flatMap { id => referenceTaxaFor(id) }
        // println(s"Corresponding taxa: ${taxa.mkString(", ")}")

        /* Counts (and lineages) for the taxa */
        val accumulatedCountsMap: Map[Taxon, (Int, Seq[Taxon])] = getAccumulatedCounts(taxa)
        // println(s"Accumulated counts: ${accumulatedCountsMap.mkString(", ")}")

        val totalCount = taxa.length
        // println(s"Total count:        ${totalCount}")

        /* Checking each assignment of the query sequence */
        ids.foreach { id =>

          // println(s"\n  * ${id}")

          val (acceptedTaxa, rejectedTaxa) = referenceTaxaFor(id).partition(
            predicate(accumulatedCountsMap, totalCount)
          )
          // println(s"    - accepted: ${acceptedTaxa}")
          // println(s"    - rejected: ${rejectedTaxa}")

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
