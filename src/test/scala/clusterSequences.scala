package ohnosequences.db.rna16s.test

import ohnosequences.db._, csvUtils._, collectionUtils._
import ohnosequences.fastarious.fasta._
import ohnosequences.statika._
import ohnosequences.mg7._
import ohnosequences.awstools.s3._
import com.amazonaws.auth._
import com.amazonaws.services.s3.transfer._
import ohnosequences.blast.api._, outputFields._
import com.github.tototoshi.csv._
import better.files._



case object clusterSequences extends Bundle(mg7BlastResults) { bundle =>

  lazy val name: String = "clusters"

  final lazy val s3: S3Folder = ohnosequences.db.rna16s.s3prefix / name /
  final lazy val outputName: String = name + ".csv"


  case object output {
    lazy val file: File   = File(outputName).createIfNotExists()
    lazy val s3: S3Object = bundle.s3 / outputName

    lazy val csv = CSVWriter.open(this.file.toJava, append = true)(csvUtils.UnixCSVFormat)

    def upload() = {

      val transferManager = new TransferManager(new InstanceProfileCredentialsProvider())

      transferManager.upload(
        this.s3.bucket, this.s3.key,
        this.file.toJava
      ).waitForCompletion

      transferManager.shutdownNow()
    }
  }


  // TODO: tailrec
  def addCluster(cluster: Set[ID], acc: List[Set[ID]]): List[Set[ID]] = {

    val (related, rest) = acc.partition { _.intersect(cluster).nonEmpty }
    val newCluster = (cluster :: related).reduce { _ union _ }

    newCluster :: rest
  }

  def clusters(correspondences: Iterator[Set[ID]]): List[Set[ID]] =
    correspondences.foldLeft(List[Set[ID]]()) {
      case (acc: List[Set[ID]], (ids: Set[ID])) =>
        addCluster(ids, acc)
    }

  type BlastRow = csv.Row[mg7.parameters.blastOutRec.Keys]

  def instructions: AnyInstructions = {

    LazyTry {

      val blastReader = csv.Reader(mg7.parameters.blastOutRec.keys)(mg7BlastResults.blastResult)

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
    say(s"Clastered sequences uploaded to [${output.s3}]")

  }

}

case object clusteringResults extends Bundle() {

  lazy val s3location: S3Object = clusterSequences.output.s3
  lazy val clusters: File = File(s3location.key).createIfNotExists()

  def instructions: AnyInstructions = LazyTry {
    val transferManager = new TransferManager(new InstanceProfileCredentialsProvider())

    transferManager.download(
      s3location.bucket, s3location.key,
      clusters.toJava
    ).waitForCompletion

    transferManager.shutdownNow()
  } -&-
  say(s"Clusters downloaded to ${clusters}")
}


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
