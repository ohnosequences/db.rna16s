package era7bio.db.rna16s

import era7bio.db._

import ohnosequences.blast.api._
import ohnosequences.fastarious.fasta._
import ohnosequences.awstools._, ec2._, InstanceType._, s3._, regions._
import ohnosequences.statika._, aws._

import com.github.tototoshi.csv._
import era7bio.db.RNACentral5._
import era7bio.db.csvUtils._
import era7bio.db.collectionUtils._

import ohnosequencesBundles.statika._

import com.thinkaurelius.titan.core._, schema._
import com.bio4j.model.ncbiTaxonomy.NCBITaxonomyGraph._
import com.bio4j.titan.model.ncbiTaxonomy._
import com.bio4j.titan.util.DefaultTitanGraph

import better.files._


case object generate extends GenerateBlastDB(
  dbType = rna16s.dbType,
  dbName = rna16s.dbName,
  sourceFastaS3 = filter2.acceptedS3Prefix / filter2.fastaName,
  outputS3Prefix = rna16s.s3prefix / "blastdb" /
)(
  deps = filter2
)
