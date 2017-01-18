/*
  # Drop inconsistent assignments

  Here we want to drop *inconsistent* assignments from our database; an assignment is inconsistent if it's different from the majority of those in the same sequence-similarity equivalence class.

  1. First we take the sequence clusters `C` and for each of them construct a common set of assignments `Taxa(C)` (using the map we have from the previous steps): .

  2. Then we consider each assignment `T` (from the old map) and decide wheter to drop it or keep it by comparing _its parent's_ cumulative count `Count(Parent(T))` with the total count (which is the size of `Taxa(C)`). If the difference is over some fixed threshold, we keep it, otherwise we drop it. To account for the tree topology, after this process we rescue those assignments which are descendants of a valid one (note that this process is idempotent).
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
import com.bio4j.titan.model.ncbiTaxonomy.TitanNCBITaxonomyGraph

case class inconsistentAssignmentsFilter(
  val taxonomyGraph: TitanNCBITaxonomyGraph,
  val assignmentsTable: File,
  /* This threshold determines minimum ratio of the taxon's parent's count compared to the total count */
  val minimumRatio: Double = 0.75, // 75%
  /* This determines how many levels up we're going to take the ancestor to check the counts ratio; a value of `2` here means that we take the *grandparent*. */
  val ancestryLevel: Int = 2 // grandparent
) {
  type Lineage = Seq[Taxon]

  /* Mapping from sequence IDs to their taxonomic assignments set */
  lazy val referenceMap: Map[ID, Set[Taxon]] =
    CSVReader.open(assignmentsTable.toJava)(csvUtils.UnixCSVFormat).iterator
      .foldLeft(Map[ID, Set[Taxon]]()) { (acc, row) =>
        acc.updated(
          row(0),
          row(1).split(';').map(_.trim).toSet
        )
      }

  def referenceTaxaFor(id: ID): Set[Taxon] = referenceMap.get(id).getOrElse(Set())

  /* This method  returns the cumulative counts and lineages of a sequence of (not necessarily distinct) taxa */
  // TODO: this should be a piece of reusable code in MG7
  def getAccumulatedCounts(taxa: Seq[Taxon]): Map[Taxon, (Int, Lineage)] = {
    // NOTE: this mutable map is used in getLineage for memoization of the results that we get from the DB
    val cacheMap: scala.collection.mutable.Map[Taxon, Lineage] = scala.collection.mutable.Map()

    def getLineage(id: Taxon): Lineage = cacheMap.get(id).getOrElse {
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

  /* This predicate determines whether an assignment will be kept (true) or dropped */
  def predicate(countsMap: Map[Taxon, (Int, Lineage)], totalCount: Int): Taxon => Boolean = { taxon =>

    countsMap.get(taxon)
      /* First we find `taxon`'s ancestor ID */
      .flatMap { case (_, lineage) => lineage.reverse.drop(ancestryLevel + 1).headOption }
      /* then we find out its count */
      .flatMap { ancestorId => countsMap.get(ancestorId) }
      /* and compare it with the total: it has to be more than `minimumRatio` for `taxon` to pass */
      .map { case (ancestorCount, _) =>
        ((ancestorCount: Double) / totalCount) >= minimumRatio
      }
      .getOrElse(false)
  }

  def partitionAssignments(cluster: Seq[ID]): Seq[(ID, Set[Taxon], Set[Taxon])] = {

    /* Corresponding taxa (to all sequence in the cluster together) */
    val taxa: Seq[Taxon] = cluster.flatMap { id => referenceTaxaFor(id) }
    val totalCount = taxa.length

    /* Counts (and lineages) for the taxa */
    val accumulatedCountsMap: Map[Taxon, (Int, Lineage)] = getAccumulatedCounts(taxa)

    /* Checking each assignment of the query sequence */
    cluster.map { id =>

      val (acceptedTaxa, rejectedTaxa) = referenceTaxaFor(id).partition(
        predicate(accumulatedCountsMap, totalCount)
      )

      /* Among previously rejected assignments, we rescue those that are descendants of the accepted taxa and keep them too */
      val (acceptedDescendants, rejectedRest) = rejectedTaxa.partition { taxon =>
        accumulatedCountsMap.get(taxon)
          .map { case (_, lineage) =>
            // at least one ancestor is among accepted ones:
            (lineage.toSet intersect acceptedTaxa).nonEmpty
          }
          .getOrElse(false)
      }

      (id, acceptedTaxa ++ acceptedDescendants, rejectedRest)
    }
  }

}

case object dropInconsistentAssignments extends FilterDataFrom(dropRedundantAssignments)(
  deps = clusteringResults, ncbiTaxonomyBundle
) {

  /* Mapping of sequence IDs to corresponding FASTA sequences */
  lazy val id2fasta: Map[ID, Fasta] = source.fasta.parsed
    .foldLeft(Map[ID, Fasta]()) { (acc, fasta) =>
      acc.updated(
        fasta.getV(header).id,
        fasta
      )
    }

  lazy val filter = inconsistentAssignmentsFilter(
    ncbiTaxonomyBundle.graph,
    source.table.file
  )

  def filterData(): Unit = clusteringResults.clusters.lines
    .flatMap { line => filter.partitionAssignments( line.split(',') ) }
    .foreach { case (id, accepted, rejected) =>

      writeOutput(id, accepted.toSeq, rejected.toSeq, id2fasta(id))
    }
}

case object dropInconsistentAssignmentsAndGenerate extends FilterAndGenerateBlastDB(
  ohnosequences.db.rna16s.dbName,
  ohnosequences.db.rna16s.test.dropInconsistentAssignments
)


/* This object provides some context for further testing in REPL */
case object inconsistentAssignmentsTest {

  import ohnosequencesBundles.statika._
  import com.thinkaurelius.titan.core.TitanFactory
  import com.bio4j.titan.util.DefaultTitanGraph
  import org.apache.commons.configuration.Configuration

  // You need to download some files for testing
  case object local {

    lazy val configuration: Configuration = DefaultBio4jTitanConfig(file"data/in/bio4j-taxonomy-titandb".toJava)

    lazy val taxonomyGraph =
      new TitanNCBITaxonomyGraph(
        new DefaultTitanGraph(TitanFactory.open(configuration))
      )

    val assignmentsTable = file"data/in/table.csv"
  }

  // val testCluster: Seq[ID] = file"data/in/URS00008E71FD-cluster.csv".lines.next.split(',')

  val filter = inconsistentAssignmentsFilter(
    local.taxonomyGraph,
    local.assignmentsTable
  )
}
