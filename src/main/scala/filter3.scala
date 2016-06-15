package era7bio.db.rna16s

import era7bio.db._, csvUtils._, collectionUtils._

import ohnosequences.mg7._, bio4j.taxonomyTree._, bio4j.titanTaxonomyTree._
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

case object filter3 extends FilterDataFrom(filter2)(
  deps = mg7results, bio4j.taxonomyBundle
) {

  type ID = String
  type Taxa = String
  type Fasta = FASTA.Value

  private lazy val taxonomyGraph = ohnosequences.mg7.bio4j.taxonomyBundle.graph

  def filterData(): Unit = {

    /* First we read what we've got from MG7 */
    val id2mg7lca: Map[ID, Taxa] = csv.newReader(mg7results.lcaTable)
      .allWithHeaders.map { row =>
        row(csv.columnNames.ReadID) -> row(csv.columnNames.TaxID)
      }.toMap

    /* Then we process the source table and compare assignments with LCA from MG7.
       We know that in the output of filter2 table and fasta IDs are synchronized,
       so we can just zip them. */
    ( source.table.csvReader.iterator zip
      source.fasta.stream.iterator
    ).foreach { case (row, fasta) =>

      val id: ID = row(0)
      val taxas: Seq[Taxa] = row(1).split(';').map(_.trim).toSeq

      if (taxas.length == 1) {
        /* If there's only one assignment we don't touch it */
        writeOutput(id, taxas, Seq(), fasta)
      } else {

        id2mg7lca.get(id)
          .flatMap(taxonomyGraph.getNode)
          .flatMap(_.parent) match {

          /* Either this id is not in the MG7 lca output, then it means that
             this query sequence has no hits with anything except of itself,
             i.e. is distinct enough and good for us.
             Or the `lca` has no parent (is the root node) */
          case None => writeOutput(id, taxas, Seq(), fasta)

          /* Otherwise we want to filter out those taxa assignments,
             that are too different from the LCA obtained from MG7,
             i.e. are not descendants of its parent */
          case Some(lcaParent) => {
            val (acceptedTaxas, rejectedTaxas) = taxas.partition { taxa =>

              taxonomyGraph.getNode(taxa).map { node =>
                // lcaParent is in the lineage:
                node.lineage.exists { _.id == lcaParent.id }
              }.getOrElse(false)
            }

            writeOutput(id, acceptedTaxas, rejectedTaxas, fasta)
          }
        }
      }
    }

  }
}


case object filter3AndGenerate extends FilterAndGenerateBlastDB(
  era7bio.db.rna16s.dbName,
  era7bio.db.rna16s.dbType,
  era7bio.db.rna16s.filter3
)
