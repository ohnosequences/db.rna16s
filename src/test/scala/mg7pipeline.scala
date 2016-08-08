package ohnosequences.db.rna16s

import ohnosequences.mg7._, loquats._, dataflows._
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

case object referenceDBPipeline {

  /* As the reference database we use the one generated from dropRedundantAssignments */
  case object rna16sRefDB extends ReferenceDB(
    ohnosequences.db.rna16s.dbName,
    dropRedundantAssignmentsAndGenerate.s3,
    dropRedundantAssignments.output.table.s3
  )

  /* As input we use the FASTA accepted by dropRedundantAssignments */
  val splitInputs: Map[ID, S3Resource] = Map(
    "refdb" -> S3Resource(ohnosequences.db.rna16s.dropRedundantAssignments.output.fasta.s3)
  )

  def outputS3Folder(step: String): S3Folder = ohnosequences.db.rna16s.s3prefix / "mg7" / step /

  case object mg7parameters extends MG7Parameters(
    (_, step) => outputS3Folder(step),
    readsLength = bp250, // NOTE: this does not have any influence
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
  )
  {

    /* The only basic thing we require is at least 100% **query** coverage. If we miss sequences this way, this should be solved through trimming/quality filtering */
    override def blastFilter(row: csv.Row[BlastOutRecKeys]): Boolean =
      ( row.select(outputFields.qcovs).toDouble >= 100 ) &&
      /* IMPORTANT: exclude the query from the results */
      ( row.select(outputFields.qseqid) != row.select(outputFields.sseqid) )
  }

  lazy val dataflow = NoFlashDataflow(mg7parameters)(splitInputs)

  /* This class is a default loquat configuration for this pipeline */
  abstract class RefDBLoquatConfig(
    val loquatName: String,
    val dataMappings: List[AnyDataMapping],
    val workersNumber: Int = 1
  ) extends AnyLoquatConfig {

    val metadata: AnyArtifactMetadata = generated.metadata.db.rna16s

    // TODO: we should probably have a restricted role for this:
    val iamRoleName: String = "era7-projects"
    val logsBucketName: String = "era7-projects-loquats"

    val defaultAMI = AmazonLinuxAMI(Ireland, HVM, InstanceStore)

    val managerConfig: AnyManagerConfig = ManagerConfig(
      InstanceSpecs(defaultAMI, m3.medium),
      purchaseModel = Spot(maxPrice = Some(0.01))
    )

    val workersConfig: AnyWorkersConfig = WorkersConfig(
      instanceSpecs = InstanceSpecs(defaultAMI, m3.medium),
      purchaseModel = Spot(maxPrice = Some(0.02)),
      groupSize = AutoScalingGroupSize(0, workersNumber, workersNumber*2)
    )

    val terminationConfig = TerminationConfig(
      terminateAfterInitialDataMappings = true
    )
  }

  /*
    ### mg7 steps

    These objects define the mg7 pipeline steps. You need to run them in the order they are written here.

    For running them, go to the scala console and run

    ```
    ohnosequences.db.rna16s.referenceDBPipeline.<name>Loquat.deploy(era7.defaults.<yourUser>)
    ```
  */

  case object splitConfig extends RefDBLoquatConfig("split", dataflow.splitDataMappings)
  case object splitLoquat extends Loquat(splitConfig, splitDataProcessing(mg7parameters))

  case object blastConfig extends RefDBLoquatConfig("blast", dataflow.blastDataMappings, 100) {
    // NOTE: we don't want to check input objects here because they are too many and
    //   checking them one by one will take too long and likely fail
    override val checkInputObjects = false

    override val workersConfig: AnyWorkersConfig = WorkersConfig(
      instanceSpecs = InstanceSpecs(defaultAMI, c3.large),
      purchaseModel = Spot(maxPrice = Some(0.03)),
      groupSize = AutoScalingGroupSize(0, workersNumber, workersNumber)
    )
  }
  case object blastLoquat extends Loquat(blastConfig, blastDataProcessing(mg7parameters))


  case object assignConfig extends RefDBLoquatConfig("assign", dataflow.assignDataMappings, 10) {
    override val checkInputObjects = false

    override lazy val amiEnv = amznAMIEnv(ami, javaHeap = 10)

    override val workersConfig: AnyWorkersConfig = WorkersConfig(
      instanceSpecs = InstanceSpecs(defaultAMI, m3.xlarge),
      purchaseModel = Spot(maxPrice = Some(0.03)),
      groupSize = AutoScalingGroupSize(0, workersNumber, workersNumber)
    )
  }
  case object assignLoquat extends Loquat(assignConfig, assignDataProcessing(mg7parameters))

  case object mergeConfig extends RefDBLoquatConfig("merge", dataflow.mergeDataMappings) {
    override val skipEmptyResults = false

    override val workersConfig: AnyWorkersConfig = WorkersConfig(
      instanceSpecs = InstanceSpecs(defaultAMI, c3.large),
      purchaseModel = Spot(maxPrice = Some(0.03)),
      groupSize = AutoScalingGroupSize(0, workersNumber, workersNumber*2)
    )
  }
  case object mergeLoquat extends Loquat(mergeConfig, mergeDataProcessing)
}
