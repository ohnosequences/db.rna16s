/*
  # Drop redundant assignments

  The goal of this step is reducing the number of both sequences and assignments in the database without losing information. For this, an assignment of a sequence `s` to a taxon `A` is considered redundant if there is a sequence `S` with an assignment to `A` such that `s` is a **subsequence** of `S`. Why? because when using these sequences as a reference, every alignment with `s` can be seen as one with `S`.

  This step is run here on the output of of the pick 16S candidates step, but it would work exactly the same on any other set of sequences (and assignments).

  The output of this step represents around `70%` of the pick 16S candidates output; there is also a significant number of sequences with *less* assignments.
*/
package ohnosequences.db.rna16s.test

import ohnosequences.db._, collectionUtils._
import ohnosequences.fastarious.fasta._

case object dropRedundantAssignments extends FilterDataFrom(pick16SCandidates)() {

  type Eith[X]  = Either[X, X]

  /*
    ## Implementation

    The idea of this implementation is to transpose the input assignment mapping (`id2taxas`) to get all sequence IDs assigned to a given taxon.

     Then we consider actual sequences corresponding to those IDs and partition them on those that are contained in other ones and those that are not. First ones are marked as discarded and the latter as accepted.

     By transposing this map again, we get for each sequence ID a set of accepted and discarded assignments. If a sequence doesn't have any accepted assignments left, it gets completely discarded from the database.
  */
  def filterData(): Unit = {

    /* Mapping of sequence IDs to corresponding FASTA sequences */
    // id1 -> fasta1
    // id2 -> fasta2
    // ...
    val id2fasta: Map[ID, FASTA] = source.fasta.parsed
      .foldLeft(Map[ID, FASTA]()) { (acc, fasta) =>
        acc.updated(
          fasta.header.id,
          fasta
        )
      }

    /* Mapping of sequence IDs to the list of their taxonomic assignments */
    // id1 -> taxa1, taxa2, taxa3
    // id2 -> taxa2, taxa4
    // ...
    val id2taxas: Map[ID, Seq[Taxon]] = source.table.csvReader.iterator
      .foldLeft(Map[ID, Seq[Taxon]]()) { (acc, row) =>
        acc.updated(
          row(0),
          row(1).split(';').map(_.trim).toSeq
        )
      }

    /* Transposed mapping of taxas to the sequence IDs that have this assignment */
    // taxa1 -> id1
    // taxa2 -> id1, id2
    // taxa3 -> id1
    // taxa4 -> id2
    // ...
    val taxa2ids: Map[Taxon, Seq[ID]] = id2taxas.trans

    /* Now we arrange values of taxa2ids map to distinguish its ID _values_:
       we get corresponding fastas and _partition_ those that are contained in others.
       Lefts are contained in another ones, Rights are not contained */
    // taxa2 -> Left(id1), Right(id2), Left(id3), ...
    val taxa2partitionedIDs: Map[Taxon, Seq[Eith[ID]]] = taxa2ids.map { case (taxa, ids) =>

      val fastas: Seq[FASTA] = ids.map(id2fasta.apply)

      val (contained: Seq[FASTA], notContained: Seq[FASTA]) =
        partitionContained(fastas){ _.sequence.letters }

      // here we add Left/Right tag to the corresponding IDs and put them all together:
      taxa -> {
           contained.map { f =>  Left(f.header.id): Eith[ID] } ++
        notContained.map { f => Right(f.header.id): Eith[ID] }
      }
    }

    /* Now we transpose taxa2partitionedIDs map to have the opposite correspondence
       between sequence IDs and the taxonomic assignments:
       Lefts are discarded assignments; Rights are accepted. */
    // id1 -> Left(taxa1), Right(taxa2), ...
    val id2partitionedTaxas: Map[ID, Seq[Eith[Taxon]]] = taxa2partitionedIDs.trans {
      case (taxa, Left(id)) => (Left(taxa), id)
      case (taxa, Right(id)) => (Right(taxa), id)
    }

    /* And finally we just write the results */
    id2partitionedTaxas.foreach { case (id, partTaxas) =>

      val rejectedTaxas: Seq[Taxon] = partTaxas.collect { case Left(t) => t }
      val acceptedTaxas: Seq[Taxon] = partTaxas.collect { case Right(t) => t }

      writeOutput(id, acceptedTaxas, rejectedTaxas, id2fasta(id))
    }
  }

  /* Filters out those sequences that are contained in any other ones.
     Returns a pair: contained seq-s and not-contained. */
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
  ohnosequences.db.rna16s.test.dropRedundantAssignments
)
