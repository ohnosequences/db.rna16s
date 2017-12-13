package ohnosequences.db.rna16s.test

import ohnosequences.statika._, aws._
import com.amazonaws.services.ec2.model.{ Instance => _ , _ }
import ohnosequences.awstools._, ec2._
import era7bio.defaults._
import scala.collection.JavaConverters._

case object rna16s {

  // TODO: move all these things somewhere to make them reusable (era7bio/projects?)
  def getStatikaStatus(inst: Instance): Option[String] = inst.ec2.describeTags(
    new DescribeTagsRequest(List(
      new Filter("resource-type", List("instance").asJava),
      new Filter("resource-id", List(inst.id).asJava),
      new Filter("key", List("statika-status").asJava)
    ).asJava)
  ).getTags.asScala
    .headOption
    .map { _.getValue }

  def waitForCompletion(instance: Instance): Either[Instance, Instance] = {

    @annotation.tailrec
    def checkStatus_rec(previous: Option[String]): Either[Instance, Instance] = {
      val current = getStatikaStatus(instance)
      if (current != previous) println(s"${instance.id}: ${current.getOrElse("...")}")
      current match {
        case Some("failure") => Left(instance)
        case Some("success") => Right(instance)
        case _ => {
          Thread sleep 3000
          checkStatus_rec(current)
        }
      }
    }
    checkStatus_rec(None)
  }

  // use `sbt test:console`:
  // > era7bio.db.test.rna16s.launch(...)
  def launchAndWait[
    B <: AnyBundle,
    T <: AnyInstanceType
  ](compat: compats.DefaultCompatible[B],
    instanceType: T,
    user: AWSUser,
    terminateOnSuccess: Boolean
  )(implicit supportsAMI: T SupportsAMI compats.DefaultAMI
  ): Either[String, String] = {
    val ec2 = EC2Client(credentials = user.profile)

    val instance = ec2.runInstances(
      compat.instanceSpecs(
        instanceType,
        user.keypair.name,
        Some(ec2Roles.projects.name)
      )
    )(1)
      .toOption
      .flatMap(_.headOption)
      .getOrElse(sys.error("Instance launch failed"))

    val id = instance.id
    ec2.waitUntil.instanceStatusOk.withIDs(Seq(id))
    println(s"${id}: ${instance.publicDNS}")

    waitForCompletion(instance) match {
      case Left(instance) => Left(
        s"Bundle launch has failed. Instance ${id} is left running for you to check logs."
      )
      case Right(instance) => {
        if (terminateOnSuccess) { instance.terminate }
        Right(s"Bundle launch finished successfully.")
      }
    }
  }


  case class pipeline(
    user: AWSUser,
    terminateOnSuccess: Boolean = true
  ) {
    def pick16SCandidates(): Either[String, String] =
      launchAndWait(ohnosequences.db.rna16s.test.compats.pick16SCandidates,
        r3.`2xlarge`,
        user,
        terminateOnSuccess
      )

    def dropRedundantAssignmentsAndGenerate(): Either[String, String] =
      launchAndWait(ohnosequences.db.rna16s.test.compats.dropRedundantAssignmentsAndGenerate,
        r3.large,
        user,
        terminateOnSuccess
      )

    // TODO: mg7.pipeline.split.launchLocally
    // TODO: mg7.pipeline.blast.launchLocally

    def clusterSequences(): Either[String, String] =
      launchAndWait(ohnosequences.db.rna16s.test.compats.clusterSequences,
        r3.large,
        user,
        terminateOnSuccess
      )

    def dropInconsistentAssignmentsAndGenerate(): Either[String, String] =
      launchAndWait(ohnosequences.db.rna16s.test.compats.dropInconsistentAssignmentsAndGenerate,
        r3.large,
        user,
        terminateOnSuccess
      )
  }

}
