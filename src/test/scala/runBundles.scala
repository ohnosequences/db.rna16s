package ohnosequences.db.rna16s.test

import ohnosequences.statika._, aws._
import ohnosequences.awstools._, regions.Region._, ec2._, InstanceType._, autoscaling._, s3._
import era7.defaults._

case object rna16s {

  // use `sbt test:console`:
  // > era7bio.db.test.rna16s.launch(...)
  def launch[
    B <: AnyBundle,
    T <: AnyInstanceType
  ](compat: compats.DefaultCompatible[B],
    instanceType: T
  )(user: AWSUser)(implicit
    supportsAMI: T SupportsAMI compats.DefaultAMI
  ): List[String] = {

    EC2.create(user.profile).runInstances(amount = 1,
      compat.instanceSpecs(
        instanceType,
        user.keypair.name,
        Some(ec2Roles.projects.name)
      )
    ).map { _.getInstanceId }
  }

  def pick16SCandidates(user: AWSUser): List[String] =
    launch(ohnosequences.db.rna16s.test.compats.pick16SCandidates, r3.x2large)(user)

  def dropRedundantAssignmentsAndGenerate(user: AWSUser): List[String] =
    launch(ohnosequences.db.rna16s.test.compats.dropRedundantAssignmentsAndGenerate, r3.large)(user)

  def clusterSequences(user: AWSUser): List[String] =
    launch(ohnosequences.db.rna16s.test.compats.clusterSequences, r3.large)(user)

  def dropInconsistentAssignmentsAndGenerate(user: AWSUser): List[String] =
    launch(ohnosequences.db.rna16s.test.compats.dropInconsistentAssignmentsAndGenerate, r3.large)(user)
}
