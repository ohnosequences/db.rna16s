
```scala
package ohnosequences.db.rna16s

import era7bio.db._
import ohnosequences.statika._, aws._
import ohnosequences.awstools._, regions.Region._, ec2._, InstanceType._, autoscaling._, s3._

case object compats {

  class DefaultCompatible[B <: AnyBundle](bundle: B, javaHeap: Int) extends Compatible(
    amznAMIEnv(AmazonLinuxAMI(Ireland, HVM, InstanceStore), javaHeap),
    bundle,
    generated.metadata.db.rna16s
  )

  case object pick16SCandidates extends DefaultCompatible(ohnosequences.db.rna16s.pick16SCandidates, javaHeap = 40)

  case object dropRedundantAssignmentsAndGenerate extends DefaultCompatible(ohnosequences.db.rna16s.dropRedundantAssignmentsAndGenerate, javaHeap = 10)
  case object dropInconsistentAssignmentsAndGenerate extends DefaultCompatible(ohnosequences.db.rna16s.dropInconsistentAssignmentsAndGenerate, javaHeap = 10)
}

```




[test/scala/dropRedundantAssignments.scala]: dropRedundantAssignments.scala.md
[test/scala/runBundles.scala]: runBundles.scala.md
[test/scala/mg7pipeline.scala]: mg7pipeline.scala.md
[test/scala/compats.scala]: compats.scala.md
[test/scala/dropInconsistentAssignments.scala]: dropInconsistentAssignments.scala.md
[test/scala/pick16SCandidates.scala]: pick16SCandidates.scala.md
[main/scala/package.scala]: ../../main/scala/package.scala.md
[main/scala/release.scala]: ../../main/scala/release.scala.md