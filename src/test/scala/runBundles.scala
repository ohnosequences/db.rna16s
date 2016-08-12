package ohnosequences.db.rna16s.test

import ohnosequences.statika._, aws._
import ohnosequences.awstools._, regions.Region._, ec2._, InstanceType._, autoscaling._, s3._
import era7.defaults._

case object rna16s {

  // use `sbt test:console`:
  // > era7bio.db.test.rna16s.launch(...)
  def launch[B <: AnyBundle](compat: ohnosequences.db.rna16s.test.compats.DefaultCompatible[B], user: AWSUser): List[String] =
    EC2.create(user.profile)
      .runInstances(
        amount = 1,
        compat.instanceSpecs(
          r3.large,
          user.keypair.name,
          Some(ec2Roles.projects.name)
        )
      )
      .map { _.getInstanceId }

  def pick16SCandidates(user: AWSUser): List[String] = launch(ohnosequences.db.rna16s.test.compats.pick16SCandidates, user)

  def dropRedundantAssignmentsAndGenerate(user: AWSUser): List[String] = launch(ohnosequences.db.rna16s.test.compats.dropRedundantAssignmentsAndGenerate, user)
  def dropInconsistentAssignmentsAndGenerate(user: AWSUser): List[String] = launch(ohnosequences.db.rna16s.test.compats.dropInconsistentAssignmentsAndGenerate, user)
}
