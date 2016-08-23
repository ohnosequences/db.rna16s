/*
  # Drop no-hits assignments

  With

  1. `rep(t)` the sequences with assignment set containing `t`
  2. `mg7(seqs)` the union of the sequences appearing in the set of valid hits for each sequence in `seqs`

  Then we drop from `rep(t)` those assignments `s -> t` for which

  1. the size of `rep(t)` is at least 2 (so that we can meaningfully compare something)
  2. `s` is not in `mg7(rep(t))`
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

case object dropNoHitsAssignments extends FilterDataFrom(dropInconsistentAssignments)(deps = mg7results, ncbiTaxonomyBundle) {

  def filterData(): Unit = ()
}
