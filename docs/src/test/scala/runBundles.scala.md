
```scala
package ohnosequences.db.rna16s.test

import ohnosequences.statika._, aws._
import ohnosequences.awstools._, regions._, ec2._, autoscaling._, s3._
import era7bio.defaults._

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
  ): Seq[String] = {

    EC2Client(credentials = user.profile).runInstances(
      compat.instanceSpecs(
        instanceType,
        user.keypair.name,
        Some(ec2Roles.projects.name)
      )
    )(1)
      .getOrElse(sys.error("Couldn't launch instances"))
      .map { _.id }
  }

  def pick16SCandidates(user: AWSUser): Seq[String] =
    launch(ohnosequences.db.rna16s.test.compats.pick16SCandidates, r3.`2xlarge`)(user)

  def dropRedundantAssignmentsAndGenerate(user: AWSUser): Seq[String] =
    launch(ohnosequences.db.rna16s.test.compats.dropRedundantAssignmentsAndGenerate, r3.large)(user)

  def clusterSequences(user: AWSUser): Seq[String] =
    launch(ohnosequences.db.rna16s.test.compats.clusterSequences, r3.large)(user)

  def dropInconsistentAssignmentsAndGenerate(user: AWSUser): Seq[String] =
    launch(ohnosequences.db.rna16s.test.compats.dropInconsistentAssignmentsAndGenerate, r3.large)(user)
}

```




[main/scala/data.scala]: ../../main/scala/data.scala.md
[main/scala/package.scala]: ../../main/scala/package.scala.md
[test/scala/clusterSequences.scala]: clusterSequences.scala.md
[test/scala/compats.scala]: compats.scala.md
[test/scala/dropInconsistentAssignments.scala]: dropInconsistentAssignments.scala.md
[test/scala/dropRedundantAssignments.scala]: dropRedundantAssignments.scala.md
[test/scala/mg7pipeline.scala]: mg7pipeline.scala.md
[test/scala/package.scala]: package.scala.md
[test/scala/pick16SCandidates.scala]: pick16SCandidates.scala.md
[test/scala/releaseData.scala]: releaseData.scala.md
[test/scala/runBundles.scala]: runBundles.scala.md