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
  def addPair(qseq: ID, others: Seq[ID], acc: List[Set[ID]]): List[Set[ID]] = {
    acc match {
      case Nil => List( others.toSet + qseq )
      case h :: t =>
        if (h.contains(qseq)) (h ++ others) :: t
        else h :: addPair(qseq, others, t)
    }
  }

  def clusters(correspondences: Iterator[(ID, Seq[ID])]): List[Set[ID]] =
    correspondences.foldLeft(List[Set[ID]]()) {
      case (acc: List[Set[ID]], (qseq: ID, others: Seq[ID])) =>
        addPair(qseq, others, acc)
    }

  type BlastRow = csv.Row[mg7.parameters.blastOutRec.Keys]

  def instructions: AnyInstructions = {

    LazyTry {

      val blastReader = csv.Reader(mg7.parameters.blastOutRec.keys)(mg7BlastResults.blastResult)

      val correspondences: Iterator[(ID, Seq[ID])] = blastReader.rows
        // grouping rows by the query sequence id
        .contiguousGroupBy { _.select(qseqid) }
        .map { case (qseq: ID, hits: Seq[BlastRow]) =>

          qseq -> hits.map { _.select(sseqid) }
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
