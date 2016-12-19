package ohnosequences.db.rna16s.test

import ohnosequences.mg7._, loquats._
import ohnosequences.datasets._, illumina._
import ohnosequences.cosas._, types._, klists._
import ohnosequences.loquat._, utils._
import ohnosequences.statika._, aws._
import ohnosequences.blast.api._

import ohnosequences.awstools.ec2._, InstanceType._
import ohnosequences.awstools.s3._
import ohnosequences.awstools.autoscaling._
import ohnosequences.awstools.regions.Region._

import com.amazonaws.services.s3.transfer._
import com.amazonaws.auth._, profile._

import better.files._

case object mg7 {

  /* As the reference database we use the one generated from dropRedundantAssignments */
  case object rna16sRefDB extends ReferenceDB(
    ohnosequences.db.rna16s.dbName,
    dropRedundantAssignmentsAndGenerate.s3,
    dropRedundantAssignments.output.table.s3
  )

  case object parameters extends MG7Parameters(
    splitChunkSize = 100,
    splitInputFormat = FastaInput,
    blastCommand = blastn,
    blastOutRec  = defaults.blastnOutputRecord,
    blastOptions = defaults.blastnOptions.update(
      num_threads(2)              ::
      word_size(150)              ::
      evalue(BigDecimal(1E-100))  ::
      max_target_seqs(10000)      ::
      perc_identity(99.0)         ::
      *[AnyDenotation]
    ).value,
    referenceDBs = Set(rna16sRefDB)
  ) {

    /* The only basic thing we require is at least 99% **query** coverage. */
    override def blastFilter(row: csv.Row[BlastOutRecKeys]): Boolean =
      ( row.select(outputFields.qcovs).toDouble >= 99 ) &&
      /* IMPORTANT: exclude the query from the results */
      ( row.select(outputFields.qseqid) != row.select(outputFields.sseqid) )
  }

  case object pipeline extends MG7Pipeline(parameters) {

    val metadata: AnyArtifactMetadata = ohnosequences.db.generated.metadata.rna16s
    // TODO: we should probably have a restricted role for this:
    val iamRoleName: String = "era7-projects"
    val logsBucketName: String = "era7-projects-loquats"

    /* As input we use the FASTA accepted by dropRedundantAssignments */
    lazy val inputSamples: Map[ID, S3Resource] = Map(
      "refdb" -> S3Resource(ohnosequences.db.rna16s.test.dropRedundantAssignments.output.fasta.s3)
    )

    lazy val outputS3Folder: (SampleID, StepName) => S3Folder = { (_, stepName) =>
      ohnosequences.db.rna16s.s3prefix / "mg7" / stepName /
    }

    val splitConfig  = SplitConfig(1)
    val blastConfig  = BlastConfig(100)
    // these steps are not needed:
    val assignConfig = AssignConfig(10)
    val mergeConfig  = MergeConfig(1)
    val countConfig  = CountConfig(1)
  }
}

/* This bundle just downloads the output of the MG7 Blast step and merges the chunks */
case object mg7BlastResults extends Bundle() {

  lazy val s3location: S3Folder = mg7.pipeline.outputS3Folder("", "blast") / "chunks" /

  lazy val blastChunks: File = File(s3location.key)
  lazy val blastResult: File = (blastChunks.parent / "blastResult.csv").createIfNotExists()

  def instructions: AnyInstructions = LazyTry {
    val transferManager = new TransferManager(new DefaultAWSCredentialsProviderChain())

    transferManager.downloadDirectory(
      s3location.bucket, s3location.key,
      File(".").toJava
    ).waitForCompletion

    transferManager.shutdownNow()
  } -&- LazyTry {

    loquats.mergeDataProcessing().mergeChunks(blastChunks, blastResult)
  }
}
