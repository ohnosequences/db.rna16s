package era7bio.db

import ohnosequences.statika._, aws._
import ohnosequences.awstools._, regions.Region._, ec2._, InstanceType._, autoscaling._, s3._

case object rna16sCompats {

  class DefaultCompatible[B <: AnyBundle](bundle: B, javaHeap) extends Compatible(
    amznAMIEnv(AmazonLinuxAMI(Ireland, HVM, InstanceStore), javaHeap),
    bundle,
    generated.metadata.db.rna16s
  )

  case object filter1  extends DefaultCompatible(rna16s.filter1,  javaHeap = 40)
  case object filter2  extends DefaultCompatible(rna16s.filter2,  javaHeap = 40)
  case object generate extends DefaultCompatible(rna16s.generate, javaHeap = 10)
}
