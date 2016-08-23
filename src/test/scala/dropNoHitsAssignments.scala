/*
  # Drop no-hits assignments

  With

  1. `rep(t)` the sequences with assignment set containing `t`
  2. `mg7(seqs)` the union of the sequences appearing in the set of valid hits for each sequence in `seqs`

  Then we drop from `rep(t)` those assignments `s -> t` for which

  1. the size of `rep(t)` is at least 2 (so that we can meaningfully compare something)
  2. `s` is not in `mg7(rep(t))`

  We can do something slightly different but mostly equivalent through the mg7 LCA assignment: For `rep(t)`, if there are *other* sequences in `rep(t)`, as 1. above, and at least one of them *does* have an mg7 assignment, then we need to drop those with *no* **mg7** assignment.
*/
package ohnosequences.db.rna16s.test

import ohnosequences.db._, csvUtils._, collectionUtils._
import ohnosequences.ncbitaxonomy._, titan._
import ohnosequences.fastarious.fasta._
import ohnosequences.statika._
import ohnosequences.awstools.s3._
import com.amazonaws.auth._
import com.amazonaws.services.s3.transfer._
import com.github.tototoshi.csv._
import better.files._

case object dropNoHitsAssignments extends FilterDataFrom(dropInconsistentAssignments)(deps = mg7results) {

  type ID     = String
  type Taxon  = String
  type Fasta  = FASTA.Value

  /* Mapping of sequence IDs to the list of their taxonomic assignments */
  // id1 -> taxa1, taxa2, taxa3
  // id2 -> taxa2, taxa4
  // ...
  lazy val assignments: Map[ID, Seq[Taxon]] = source.table.csvReader.iterator
  .foldLeft(Map[ID, Seq[Taxon]]()) { (acc, row) =>
    acc.updated(
      row(0),
      row(1).split(';').map(_.trim).toSeq
    )
  }
  /* Transposed mapping of taxas to the sequence IDs that have this assignment; `rep` from the docs above. */
  // taxa1 -> id1
  // taxa2 -> id1, id2
  // taxa3 -> id1
  // taxa4 -> id2
  // ...
  lazy val assignedTo: Map[Taxon, Seq[ID]] = assignments.trans

  lazy val mg7Assignment: Map[ID, Taxon] = dropInconsistentAssignments mg7LCAfromFile mg7results.lcaTable


  def filterData(): Unit = {

    assignedTo foreach {
      // ids = rep(taxon)
      case (taxon, ids) => if (ids.size >= 2) {

        ids foreach { id =>
          if( ids.exists(mg7Assignment.contains(_)) && (!mg7Assignment.contains(id)) ) {
            // drop assignment
          } else {
            // keep assignment
          }
        }
      }
    }

    ()
  }
}
