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

  Almost all `99.8%` of the sequences from the drop redundant assignments step pass this filter, because it's mostly about filtering out *wrong* assignments and there are not many sequences that get all assignments discarded.
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
import com.github.tototoshi.csv._
import better.files._

case object dropInconsistentAssignments extends FilterDataFrom(dropRedundantAssignments)(deps = mg7results, ncbiTaxonomyBundle) {

  type ID     = String
  type Taxa   = String
  type Fasta  = FASTA.Value

  private lazy val taxonomyGraph = ncbiTaxonomyBundle.graph

  def filterData(): Unit = {

    /* First we read what we've got from MG7 */
    val id2mg7lca: Map[ID, Taxa] = mg7LCAfromFile(mg7results.lcaTable)

    /* Then we process the source table and compare assignments with LCA from MG7. We know that in the output of dropRedundantAssignments table and fasta IDs are synchronized, so we can just zip them. */
    ( source.table.csvReader.iterator zip
      source.fasta.stream.iterator
    ).foreach {

      case (row, fasta) => {

        val (id, taxas) = idTaxasFromRow(row)

        id2mg7lca.get(id)
          .flatMap(taxonomyGraph.getTaxon)
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
                taxas.partition { taxa => (taxonomyGraph getTaxon taxa).fold(false)( lcaParent isInLineageOf _ ) }

              writeOutput(id, acceptedTaxas, rejectedTaxas, fasta)
            }
          }
      }
    }
  }

  val mg7LCAfromFile: File => Map[ID,Taxa] =
    file =>
      csv.Reader(csv.assignment.columns)(file)
        .rows.map { row => ( row select csv.columns.ReadID ) -> ( row select csv.columns.Taxa ) }
        .toMap

  val idTaxasFromRow: Seq[String] => (ID,Seq[Taxa]) =
    row => ( row(0), row(1).split(';').map(_.trim).toSeq )

  val accept: (ID, Seq[Taxa], Fasta) => Unit =
    (id, taxas, fasta) => writeOutput(id, taxas, Seq(), fasta)

  implicit final class nodeOps(val node: TitanNode) extends AnyVal {

    def isInLineageOf(other: TitanNode): Boolean =
      other.ancestors.exists { _.id == node.id }
  }
}

case object dropInconsistentAssignmentsAndGenerate extends FilterAndGenerateBlastDB(
  ohnosequences.db.rna16s.dbName,
  ohnosequences.db.rna16s.dbType,
  ohnosequences.db.rna16s.test.dropInconsistentAssignments
)

/* This bundle just downloads the output of the MG7 run of the results of the drop redundant assignments step */
case object mg7results extends Bundle() {

  lazy val s3location: S3Object = referenceDBPipeline.outputS3Folder("merge") / "refdb.lca.csv"
  lazy val lcaTable: File = File(s3location.key)

  def instructions: AnyInstructions = LazyTry {
    val transferManager = new TransferManager(new InstanceProfileCredentialsProvider())

    transferManager.download(
      s3location.bucket, s3location.key,
      lcaTable.toJava
    ).waitForCompletion

    transferManager.shutdownNow()
  }
}
