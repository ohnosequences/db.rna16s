package era7bio.db.rna16s

import era7bio.db._, csvUtils._, collectionUtils._, bio4jTaxonomyBundle._

import ohnosequences.mg7._, bio4j.taxonomyTree._
import ohnosequences.fastarious.fasta._
import ohnosequences.statika._
import ohnosequences.awstools.s3._

import com.amazonaws.auth._
import com.amazonaws.services.s3.transfer._
import com.github.tototoshi.csv._
import better.files._

/* This bundle just downloads the output of the MG7 run of the results of filter2 */
case object mg7results extends Bundle() {

  lazy val s3location: S3Object = referenceDBPipeline.outputS3Folder("merge") / "refdb.lca.csv"
  lazy val lcaTable: File = File(s3location.key)

  def instructions: AnyInstructions = LazyTry {
    val transferManager = new TransferManager(new InstanceProfileCredentialsProvider())

    transferManager.download(
      s3location.bucket, s3location.key,
      lcaTable.toJava
    ).waitForCompletion
  }
}

case object filter3 extends FilterData(
  sourceTableS3 = filter2.accepted.table.s3,
  sourceFastaS3 = filter2.accepted.fasta.s3,
  outputS3Prefix = era7bio.db.rna16s.s3prefix / "filter3" /
)(
  deps = mg7results, bio4jTaxonomyBundle
) {

  type ID = String
  type Taxa = String
  type Fasta = FASTA.Value

  def filterData(): Unit = {

    /* First we read what we've got from MG7 */
    val id2mg7lca: Map[ID, Taxa] = csv.newReader(mg7results.lcaTable)
      .allWithHeaders.map { row =>
        row(csv.columnNames.ReadID) -> row(csv.columnNames.TaxID)
      }.toMap

    /* Then we process the source table and compare assignments with LCA from MG7.
       We know that in the output of filter2 table and fasta IDs are synchronized,
       so we can just zip them. */
    ( source.table.reader.iterator zip
      source.fasta.stream.iterator
    ).foreach { case (row, fasta) =>

      val id: ID = row(0)
      val taxas: Seq[Taxa] = row(1).split(';').map(_.trim).toSeq

      id2mg7lca.get(id).flatMap(_.parentID) match {
        /* Either this id is not in the MG7 lca output, then it means that
           this query sequence has no hits with anything except of itself,
           i.e. is distinct enough and good for us.
           Or the `lca` has no parent (is the root node) */
        case None => accepted.table.writer.writeRow(row)

        /* Otherwise we want to filter out those taxa assignments,
           that are too different from the LCA obtained from MG7,
           i.e. are not descendants of its parent */
        case Some(lcaParent) => {
          val (acceptedTaxas, rejectedTaxas) = taxas.partition{ _.isDescendantOf(lcaParent) }

          if (acceptedTaxas.nonEmpty) {
            accepted.table.writer.writeRow( Seq(id, acceptedTaxas.mkString(";")) )
            accepted.fasta.file.appendLine( fasta.asString )
          }
          // absolutely the same for rejected.*
          if (rejectedTaxas.nonEmpty) {
            rejected.table.writer.writeRow( Seq(id, rejectedTaxas.mkString(";")) )
            rejected.fasta.file.appendLine( fasta.asString )
          }
        }
      }
    }

  }
}
