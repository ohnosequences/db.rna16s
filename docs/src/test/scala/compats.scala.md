
```scala
package era7bio.db

import ohnosequences.statika._, aws._
import ohnosequences.awstools._, regions.Region._, ec2._, InstanceType._, autoscaling._, s3._

case object rna16sCompats {

  case object generateRna16sDB extends Compatible(
    amznAMIEnv(
      AmazonLinuxAMI(Ireland, HVM, InstanceStore),
      javaHeap = 40 // in G
    ),
    rna16s.generate,
    generated.metadata.db.rna16s
  )
}

```




[main/scala/rna16s.scala]: ../../main/scala/rna16s.scala.md
[test/scala/compats.scala]: compats.scala.md
[test/scala/Dbrna16s.scala]: Dbrna16s.scala.md
[test/scala/runBundles.scala]: runBundles.scala.md