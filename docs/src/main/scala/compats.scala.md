
```scala
package era7bio.db.rna16s

import era7bio.db._
import ohnosequences.statika._, aws._
import ohnosequences.awstools._, regions.Region._, ec2._, InstanceType._, autoscaling._, s3._

case object compats {

  class DefaultCompatible[B <: AnyBundle](bundle: B, javaHeap: Int) extends Compatible(
    amznAMIEnv(AmazonLinuxAMI(Ireland, HVM, InstanceStore), javaHeap),
    bundle,
    generated.metadata.db.rna16s
  )

  case object pick16SCandidates extends DefaultCompatible(era7bio.db.rna16s.pick16SCandidates, javaHeap = 40)

  case object dropRedundantAssignmentsAndGenerate extends DefaultCompatible(era7bio.db.rna16s.dropRedundantAssignmentsAndGenerate, javaHeap = 10)
  case object dropInconsistentAssignmentsAndGenerate extends DefaultCompatible(era7bio.db.rna16s.dropInconsistentAssignmentsAndGenerate, javaHeap = 10)
}

```




[test/scala/runBundles.scala]: ../../test/scala/runBundles.scala.md
[main/scala/filter2.scala]: filter2.scala.md
[main/scala/mg7pipeline.scala]: mg7pipeline.scala.md
[main/scala/package.scala]: package.scala.md
[main/scala/compats.scala]: compats.scala.md
[main/scala/filter1.scala]: filter1.scala.md
[main/scala/filter3.scala]: filter3.scala.md
[main/scala/release.scala]: release.scala.md