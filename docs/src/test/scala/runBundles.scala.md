
```scala
package era7bio.db.test

import ohnosequences.statika._, aws._
import ohnosequences.awstools._, regions.Region._, ec2._, InstanceType._, autoscaling._, s3._

import era7bio.db._
import era7.defaults._


case object rna16s {

  // use `sbt test:console`:
  // > era7bio.db.test.bundles.runBundle(...)
  def runBundle[B <: AnyBundle](compat: era7bio.db.rna16s.compats.DefaultCompatible[B], user: AWSUser): List[String] =
    EC2.create(user.profile)
      .runInstances(
        amount = 1,
        compat.instanceSpecs(
          r3.x2large,
          user.keypair.name,
          Some(ec2Roles.projects.name)
        )
      )
      .map { _.getInstanceId }
}

```




[main/scala/bio4jTaxonomy.scala]: ../../main/scala/bio4jTaxonomy.scala.md
[main/scala/compats.scala]: ../../main/scala/compats.scala.md
[main/scala/filter1.scala]: ../../main/scala/filter1.scala.md
[main/scala/filter2.scala]: ../../main/scala/filter2.scala.md
[main/scala/generateBlastDB.scala]: ../../main/scala/generateBlastDB.scala.md
[main/scala/package.scala]: ../../main/scala/package.scala.md
[main/scala/release.scala]: ../../main/scala/release.scala.md
[test/scala/runBundles.scala]: runBundles.scala.md