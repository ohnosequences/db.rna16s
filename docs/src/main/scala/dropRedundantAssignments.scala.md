
```scala
package ohnosequences.db.rna16s

import era7bio.db._, csvUtils._, collectionUtils._
import ohnosequences.fastarious.fasta._
import ohnosequences.statika._
import com.github.tototoshi.csv._
import better.files._
```

The general purpose of this filter is to drop redundant assignments. It will reduce the size of the database, while preserving the most informative assignments. By redundant we mean assignments `Seq1 -> Taxon0` such that there exists `Seq2 -> Taxon0` and `Seq1` is a subsequence of `Seq2`.

```scala
case object dropRedundantAssignments extends FilterDataFrom(pick16SCandidates)() {

  type ID       = String
  type Taxa     = String
  type Fasta    = FASTA.Value
  type Eith[X]  = Either[X, X]
```

The idea of this implementation is to transpose the input assignment mapping (`id2taxas`) to get all sequence IDs assigned to a given taxon. 

Then we consider actual sequences corresponding to those IDs and partition them on those that are contained in other ones and those that are not. First ones are marked as discarded and the latter as accepted.

By transposing this map again, we get for each sequence ID a set of accepted and discarded assignments. If a sequence doesn't have any accepted assignments left, it gets completely discarded from the database.


```scala
  def filterData(): Unit = {
```

Mapping of sequence IDs to corresponding FASTA sequences

```scala
    // id1 -> fasta1
    // id2 -> fasta2
    // ...
    val id2fasta: Map[ID, Fasta] = source.fasta.stream
      .foldLeft(Map[ID, Fasta]()) { (acc, fasta) =>
        acc.updated(
          fasta.getV(header).id,
          fasta
        )
      }
```

Mapping of sequence IDs to the list of their taxonomic assignments

```scala
    // id1 -> taxa1, taxa2, taxa3
    // id2 -> taxa2, taxa4
    // ...
    val id2taxas: Map[ID, Seq[Taxa]] = source.table.csvReader.iterator
      .foldLeft(Map[ID, Seq[Taxa]]()) { (acc, row) =>
        acc.updated(
          row(0),
          row(1).split(';').map(_.trim).toSeq
        )
      }
```

Transposed mapping of taxas to the sequence IDs that have this assignment

```scala
    // taxa1 -> id1
    // taxa2 -> id1, id2
    // taxa3 -> id1
    // taxa4 -> id2
    // ...
    val taxa2ids: Map[Taxa, Seq[ID]] = id2taxas.trans
```

Now we arrange values of taxa2ids map to distinguish its ID _values_:
we get corresponding fastas and _partition_ those that are contained in others.
Lefts are contained in another ones, Rights are not contained

```scala
    // taxa2 -> Left(id1), Right(id2), Left(id3), ...
    val taxa2partitionedIDs: Map[Taxa, Seq[Eith[ID]]] = taxa2ids.map { case (taxa, ids) =>

      val fastas: Seq[Fasta] = ids.map(id2fasta.apply)

      val (contained: Seq[Fasta], notContained: Seq[Fasta]) =
        partitionContained(fastas){ _.getV(sequence).value }

      // here we add Left/Right tag to the corresponding IDs and put them all together:
      taxa -> {
           contained.map { f =>  Left(f.getV(header).id): Eith[ID] } ++
        notContained.map { f => Right(f.getV(header).id): Eith[ID] }
      }
    }
```

Now we transpose taxa2partitionedIDs map to have the opposite correspondence
between sequence IDs and the taxonomic assignments:
Lefts are discarded assignments; Rights are accepted.

```scala
    // id1 -> Left(taxa1), Right(taxa2), ...
    val id2partitionedTaxas: Map[ID, Seq[Eith[Taxa]]] = taxa2partitionedIDs.trans {
      case (taxa, Left(id)) => (Left(taxa), id)
      case (taxa, Right(id)) => (Right(taxa), id)
    }
```

And finally we just write the results

```scala
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
    @annotation.tailrec
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

case object dropRedundantAssignmentsAndGenerate extends FilterAndGenerateBlastDB(
  ohnosequences.db.rna16s.dbName,
  ohnosequences.db.rna16s.dbType,
  ohnosequences.db.rna16s.dropRedundantAssignments
)

```




[test/scala/runBundles.scala]: ../../test/scala/runBundles.scala.md
[main/scala/dropRedundantAssignments.scala]: dropRedundantAssignments.scala.md
[main/scala/mg7pipeline.scala]: mg7pipeline.scala.md
[main/scala/package.scala]: package.scala.md
[main/scala/compats.scala]: compats.scala.md
[main/scala/release.scala]: release.scala.md
[main/scala/dropInconsistentAssignments.scala]: dropInconsistentAssignments.scala.md
[main/scala/pick16SCandidates.scala]: pick16SCandidates.scala.md