
```scala
package era7bio.db.rna16s

import era7bio.db._, csvUtils._, collectionUtils._

import ohnosequences.fastarious.fasta._
import ohnosequences.statika._

import com.github.tototoshi.csv._
import better.files._

case object filter2 extends FilterDataFrom(filter1)() {

  type ID = String
  type Taxa = String
  type Fasta = FASTA.Value
  type Eith[X] = Either[X, X]


  def filterData(): Unit = {

    // id1 -> fasta1
    // id2 -> fasta2
    val id2fasta: Map[ID, Fasta] = source.fasta.stream
        .foldLeft(Map[ID, Fasta]()) { (acc, fasta) =>
          acc.updated(
            fasta.getV(header).id,
            fasta
          )
        }

    // id1 -> taxa1; taxa2; taxa3
    val id2taxas: Map[ID, Seq[Taxa]] = source.table.csvReader.iterator
      .foldLeft(Map[ID, Seq[Taxa]]()) { (acc, row) =>
        acc.updated(
          row(0),
          row(1).split(';').map(_.trim).toSeq
        )
      }

    // taxa1 -> id1; id2; id3
    // taxa2 -> id2
    val taxa2ids: Map[Taxa, Seq[ID]] = id2taxas.trans

    // taxa1 -> Left(id1); Right(id2); Left(id3)
    // Lefts are contained in another ones, Rights are not contained
    val taxa2partitionedIDs: Map[Taxa, Seq[Eith[ID]]] = taxa2ids.map { case (taxa, ids) =>

      // here we get fastas and filtering out those that are known to be contained in others
      val fastas: Seq[Fasta] = ids.map(id2fasta.apply)

      val (contained: Seq[Fasta], notContained: Seq[Fasta]) =
        partitionContained(fastas){ _.getV(sequence).value }

      // the result of this .map
      taxa -> (
           contained.map { f =>  Left(f.getV(header).id): Eith[ID] } ++
        notContained.map { f => Right(f.getV(header).id): Eith[ID] }
      )
    }

    // id1 -> Left(taxa1); Right(taxa2); ...
    // Lefts are discarded mappings; Rights are accepted
    val id2partitionedTaxas: Map[ID, Seq[Eith[Taxa]]] = taxa2partitionedIDs.trans {
      case (taxa, Left(id)) => (Left(taxa), id)
      case (taxa, Right(id)) => (Right(taxa), id)
    }

    id2partitionedTaxas.foreach { case (id, partTaxas) =>

      val rejectedTaxas: Seq[Taxa] = partTaxas.collect { case Left(t) => t }
      val acceptedTaxas: Seq[Taxa] = partTaxas.collect { case Right(t) => t }

      writeOutput(id, acceptedTaxas, rejectedTaxas, id2fasta(id))
    }
  }
```

Filters out those sequences that are contained in any other ones.
Returns a pair: contained seq-s and not-contained.


```scala
  def partitionContained[T](seq: Seq[T])(content: T => String): (Seq[T], Seq[T]) = {
    // from long to short:
    val sorted = seq.sortBy{ t => content(t).length }(Ordering.Int.reverse)

    // for each head filters out those ones in the tail that are contained in it
    @scala.annotation.tailrec
    def sieve_rec(acc: (Seq[T], Seq[T]), rest: Seq[T]): (Seq[T], Seq[T]) = rest match {
      case Nil => acc
      // note, this reverses the ordering:
      case head +: tail => {
        val  (accContained,  accNotContained) = acc
        val (tailContained, tailNotContained) = tail.partition { x =>
          content(head) contains content(x)
        }

        sieve_rec(
          (tailContained ++ accContained, head +: accNotContained),
          tailNotContained
        )
      }
    }

    sieve_rec((Seq(), Seq()), sorted)
  }
}


case object filter2AndGenerate extends FilterAndGenerateBlastDB(
  era7bio.db.rna16s.dbName,
  era7bio.db.rna16s.dbType,
  era7bio.db.rna16s.filter2
)

```




[main/scala/compats.scala]: compats.scala.md
[main/scala/filter1.scala]: filter1.scala.md
[main/scala/filter2.scala]: filter2.scala.md
[main/scala/filter3.scala]: filter3.scala.md
[main/scala/mg7pipeline.scala]: mg7pipeline.scala.md
[main/scala/package.scala]: package.scala.md
[main/scala/release.scala]: release.scala.md
[test/scala/runBundles.scala]: ../../test/scala/runBundles.scala.md