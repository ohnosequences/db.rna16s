
```scala
package era7bio.db.test

import ohnosequences.statika._, aws._
import ohnosequences.awstools._, regions.Region._, ec2._, InstanceType._, autoscaling._, s3._

import era7bio.db._
import era7.defaults._


case object rna16sDBRelease {

  def launch(user: AWSUser): List[String] = {
    EC2.create(user.profile)
      .runInstances(
        amount = 1,
        rna16sCompats.generateRna16sDB.instanceSpecs(
          r3.x2large,
          user.keypair.name,
          Some(ec2Roles.projects.name)
        )
      )
      .map { _.getInstanceId }
  }

}

```




[main/scala/rna16s.scala]: ../../main/scala/rna16s.scala.md
[test/scala/compats.scala]: compats.scala.md
[test/scala/Dbrna16s.scala]: Dbrna16s.scala.md
[test/scala/runBundles.scala]: runBundles.scala.md