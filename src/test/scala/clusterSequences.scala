package ohnosequences.db.rna16s.test

import ohnosequences.db._, collectionUtils._
import ohnosequences.statika._
import ohnosequences.mg7._
import ohnosequences.awstools._, s3._
import ohnosequences.blast.api._, outputFields._
import com.github.tototoshi.csv._
import better.files._


/*
  # Sequences clustering

  This procedure groups all sequences into equivalence classes based on sequence similarity, computed through pairwise BLAST alignments.

  We run BLAST on each reference sequence, with all the reference sequences (but the one we are querying, of course) as database. Then, we compute the free equivalence relation (reflexive, symmetric and transitive) on it. Clusters are just equivalence classes.

  For example, we have following hits:

  | Sequence | Hits       |
  |:--------:|:-----------|
  |   `a3`   | `a2`       |
  |   `b1`   |            |
  |   `a2`   | `a1`       |
  |   `a1`   | `a2`, `a3` |
  |   `b3`   |            |
  |   `b2`   | `b1`, `b3` |

  Then we consider every hit as the evidence of relation between the given elements:

  * `a3 → a2` therefore `a2 → a3` (symmetry)
  * also `a2 → a1`, so `a3 → a1` (transitivity)

  So the clusters that we should get from this are

  * `{ a1, a2, a3 }`
  * `{ b1, b2, b3 }`
*/
case object clusterSequences extends Bundle(mg7BlastResults) { bundle =>

  lazy val name: String = "clusters"

  final lazy val s3destination: S3Folder = ohnosequences.db.rna16s.s3prefix / name /
  final lazy val outputName: String = name + ".csv"


  case object output {
    lazy val file: File   = File(outputName).createIfNotExists()
    lazy val s3destination: S3Object = bundle.s3destination / outputName

    lazy val csv = CSVWriter.open(this.file.toJava, append = true)(csvUtils.UnixCSVFormat)

    def upload(): Unit = {
      val s3client = s3.defaultClient
      s3client.upload(this.file.toJava, this.s3destination).get
      s3client.shutdown()
    }
  }


  /*
    This is the key method for the clustering procedure.

     If we already have some clusters

     * `{ b1 }`
     * `{ a3, a2 }`
     * `{ b3, b4 }`

     and want to add a new set of equivalent elements `{b2, b1, b3}`, then we need to filter out all clusters that intersect with this set (`{ b1 }` and `{ b3, b4 }`), join them all in one new class, and add to the rest of the clusters:

     * `{ b1, b2, b3, b4 }`
     * `{ a3, a3 }`
  */
  def addCluster(cluster: Set[ID], acc: List[Set[ID]]): List[Set[ID]] = {

    val (related, rest) = acc.partition { _.intersect(cluster).nonEmpty }
    val newCluster = (cluster :: related).reduce { _ union _ }

    newCluster :: rest
  }

  /* This method folds over the hits applying `addCluster` */
  def clusters(correspondences: Iterator[Set[ID]]): List[Set[ID]] =
    correspondences.foldLeft(List[Set[ID]]()) {
      case (acc: List[Set[ID]], (ids: Set[ID])) =>
        addCluster(ids, acc)
    }

  type BlastRow = csv.Row[mg7.parameters.blastOutRec.Keys]

  def instructions: AnyInstructions = {

    LazyTry {

      val blastReader = csv.Reader(mg7.parameters.blastOutRec.keys)(mg7BlastResults.blastResult.toJava)

      val correspondences: Iterator[Set[ID]] = blastReader.rows
        // grouping rows by the query sequence id
        .contiguousGroupBy { _.select(qseqid) }
        .map { case (qseq: ID, hits: Seq[BlastRow]) =>

          hits.map{ _.select(sseqid) }.toSet + qseq
        }

      clusters(correspondences).foreach { ids => output.csv.writeRow(ids.toSeq) }

    } -&-
    LazyTry {
      println("Uploading the results...")
      output.upload()
    } -&-
    say(s"Clustered sequences uploaded to [${output.s3destination}]")
  }
}

/* This bundle just downloads the results of `clusterSequences` */
case object clusteringResults extends Bundle() {

  lazy val s3destination: S3Object = clusterSequences.output.s3destination
  lazy val clusters: File = File(s3destination.key)

  def instructions: AnyInstructions = LazyTry {
    val s3client = s3.defaultClient
    s3client.download(s3destination, clusters.toJava).get
    s3client.shutdown()
  } -&-
  say(s"Clusters downloaded to ${clusters}")
}


/*
  ## A small test

  This is is a simple test for checking that clustering works as expected.
*/
case object ClusteringTestCtx {

  val hits: List[Set[ID]] = List(
    Set("a1", "a2", "a3"),
    Set("a2", "a1", "a4"),
    Set("a3", "a2"),
    Set("a4"),

    Set("b1", "b2"),
    Set("b2", "b1"),

    Set("c1"),
    Set("c3"),
    Set("c2", "c1", "c3")
  )
}

class ClusteringTest extends org.scalatest.FunSuite {
  import ClusteringTestCtx._
  import clusterSequences._

  test("clustering") {

    val abc = clusters(hits.toIterator)
    info(abc.mkString("\n"))

    assertResult( List() ) {

      abc diff List(
        Set("a1", "a2", "a3", "a4"),
        Set("b1", "b2"),
        Set("c1", "c2", "c3")
      )
    }
  }
}
