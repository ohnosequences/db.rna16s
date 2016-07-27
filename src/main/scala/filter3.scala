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

/* This bundle just downloads the output of the MG7 run of the results of dropRedundantAssignments */
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

case object dropInconsistentAssignments extends FilterDataFrom(dropRedundantAssignments)(deps = mg7results, bio4j.taxonomyBundle) {

  type ID     = String
  type Taxa   = String
  type Fasta  = FASTA.Value

  private lazy val taxonomyGraph = ohnosequences.mg7.bio4j.taxonomyBundle.graph

  def filterData(): Unit = {

    /* First we read what we've got from MG7 */
    val id2mg7lca: Map[ID, Taxa] = mg7LCAfromFile(mg7results.lcaTable)

    /* Then we process the source table and compare assignments with LCA from MG7. We know that in the output of dropRedundantAssignments table and fasta IDs are synchronized, so we can just zip them. */
    ( source.table.csvReader.iterator zip
      source.fasta.stream.iterator
    ).foreach {

      case (row, fasta) => {

        val (id, taxas) = idTaxasFromRow(row)

        /* If there's only one assignment we don't touch it */
        // NOTE: see https://github.com/ohnosequences/db.rna16s/pull/32#discussion_r71972097 for the reasons
        if (taxas.length == 1)
          accept(id, taxas, fasta)
        else {

          id2mg7lca.get(id)
            .flatMap(taxonomyGraph.getNode)
            .flatMap(_.parent)
            /*
              Note that the previous filters guarantee that the mg7 LCA IDs *are* in the NCBI taxonomy graph; thus this option will be None if either

              1. this id is not in the MG7 lca output, so that this query sequence has no hits with anything but itself. In that case we need to consider its assignment correct, thus the base case of the fold.
              2. If the lca has no parent = is the root node, then we can already accept it: it will be in the lineage of every node
            */
            .fold( accept(id, taxas, fasta) ) {
              lcaParent => {
                /* Here we discard those taxa whose lineage does **not** contain the *parent* of the lca assignment. */
                val (acceptedTaxas, rejectedTaxas) =
                  taxas.partition { taxa => (taxonomyGraph getNode taxa).fold(false)( lcaParent isInLineageOf _ ) }

                writeOutput(id, acceptedTaxas, rejectedTaxas, fasta)
              }
            }
        }
      }
    }
  }

  val mg7LCAfromFile: File => Map[ID,Taxa] =
    file =>
      (csv newReader file)
        .allWithHeaders.map { row => ( row(csv.columnNames.ReadID) -> row(csv.columnNames.TaxID) ) }
        .toMap

  val idTaxasFromRow: Seq[String] => (ID,Seq[Taxa]) =
    row => ( row(0), row(1).split(';').map(_.trim).toSeq )

  val accept: (ID, Seq[Taxa], Fasta) => Unit =
    (id, taxas, fasta) => writeOutput(id, taxas, Seq(), fasta)

  implicit final class nodeOps(val node: TitanTaxonNode) {

    def isInLineageOf(other: TitanTaxonNode): Boolean =
      other.lineage.exists { _.id == node.id }
  }
}


case object dropInconsistentAssignmentsAndGenerate extends FilterAndGenerateBlastDB(
  era7bio.db.rna16s.dbName,
  era7bio.db.rna16s.dbType,
  era7bio.db.rna16s.dropInconsistentAssignments
)
