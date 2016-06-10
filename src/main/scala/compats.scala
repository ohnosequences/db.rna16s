package era7bio.db.rna16s

import ohnosequences.statika._, aws._
import ohnosequences.awstools._, regions.Region._, ec2._, InstanceType._, autoscaling._, s3._

case object compats {

  class DefaultCompatible[B <: AnyBundle](bundle: B, javaHeap: Int) extends Compatible(
    amznAMIEnv(AmazonLinuxAMI(Ireland, HVM, InstanceStore), javaHeap),
    bundle,
    generated.metadata.db.rna16s
  )

  case object filter1   extends DefaultCompatible(era7bio.db.rna16s.filter1, javaHeap = 40)
  // case object filter2   extends DefaultCompatible(era7bio.db.rna16s.filter2, javaHeap = 40)
  case object generate2 extends DefaultCompatible(generateFrom(era7bio.db.rna16s.filter2), javaHeap = 10)
  // case object filter3   extends DefaultCompatible(era7bio.db.rna16s.filter3, javaHeap = 10)
  case object generate3 extends DefaultCompatible(generateFrom(era7bio.db.rna16s.filter3), javaHeap = 10)
}
