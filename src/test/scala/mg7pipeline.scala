package ohnosequences.db.rna16s.test

import ohnosequences.mg7._
import ohnosequences.datasets._
import ohnosequences.cosas._, types._, klists._
import ohnosequences.statika._
import ohnosequences.blast.api._
import ohnosequences.awstools._, s3._
import better.files._

/*
  # BLAST reference sequences comparison

  This computes the relation which serves as input for the clustering step. We require

  1. close to complete query coverage
  2. close to 100% identity
*/
case object mg7 {

  /* As the reference database we use the one generated from dropRedundantAssignments */
  case object rna16sRefDB extends ReferenceDB(
    ohnosequences.db.rna16s.dbName,
    dropRedundantAssignmentsAndGenerate.s3destination,
    dropRedundantAssignments.output.table.s3
  )

  case object parameters extends MG7Parameters(
    splitChunkSize = 10,
    splitInputFormat = FastaInput,
    blastCommand = blastn,
    blastOutRec  = defaults.blastnOutputRecord,
    blastOptions = defaults.blastnOptions.update(
      num_threads(2)              ::
      word_size(42)               ::
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
    override lazy val name = "db-rna16s"

    val metadata: AnyArtifactMetadata = ohnosequences.db.generated.metadata.rna16s
    // TODO: we should probably have a restricted role for this:
    val iamRoleName: String = "era7-projects"
    val logsS3Prefix: S3Folder = s3"era7-projects-loquats" /

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
    val assignConfig = AssignConfig(20)
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
    val s3client = s3.defaultClient
    s3client.download(s3location, File(".").toJava).get
    s3client.shutdown()
  } -&- LazyTry {

    loquats.mergeDataProcessing().mergeChunks(blastChunks.toJava, blastResult.toJava)
  }
}
