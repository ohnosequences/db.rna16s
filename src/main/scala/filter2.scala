package era7bio.db.rna16s

import era7bio.db._, csvUtils._, collectionUtils._

import ohnosequences.fastarious.fasta._
import ohnosequences.statika._

import com.github.tototoshi.csv._
import better.files._


case object filter2 extends FilterData(
  sourceTableS3 = filter1.acceptedS3Prefix / filter1.tableName,
  sourceFastaS3 = filter1.acceptedS3Prefix / filter1.fastaName,
  acceptedS3Prefix = rna16s.s3prefix / "filter2" / "accepted" /,
  rejectedS3Prefix = rna16s.s3prefix / "filter2" / "rejected" /
)(
  deps = filter1
) {

  type ID = String
  type Taxa = String
  type Fasta = FASTA.Value
  type Eith[X] = Either[X, X]


  def filterData(): Unit = {

    val leftWriter = CSVWriter.open(rejected.table.toJava, append = true)(tableFormat)
    val rightWriter = CSVWriter.open(accepted.table.toJava, append = true)(tableFormat)

    // id1 -> fasta1
    // id2 -> fasta2
    val id2fasta: Map[ID, Fasta] =
      parseFastaDropErrors(source.fasta.lines)
        .foldLeft(Map[ID, Fasta]()) { (acc, fasta) =>
          acc.updated( fasta.getV(header).id, fasta )
        }

    // id1 -> taxa1; taxa2; taxa3
    val id2taxas: Map[ID, Seq[Taxa]] = CSVReader.open(source.table.toJava)(tableFormat)
      .iterator.map { row =>
        row(0) ->
        row(1).split(';').map(_.trim).toSeq
      }.toMap

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

      val lefts:  Seq[Taxa] = partTaxas.collect { case Left(t) => t }
      val rights: Seq[Taxa] = partTaxas.collect { case Right(t) => t }

      if (lefts.nonEmpty)   leftWriter.writeRow( Seq(id,  lefts.mkString(";")) )
      if (rights.nonEmpty) rightWriter.writeRow( Seq(id, rights.mkString(";")) )

      if (rights.isEmpty) { // all taxas for this ID got discarded:
        rejected.fasta.appendLine( id2fasta(id).asString )
      } else {
        accepted.fasta.appendLine( id2fasta(id).asString )
      }
    }

    leftWriter.close()
    rightWriter.close()
  }

  /* Filters out those sequences that are contained in any other ones.
     Returns a pair: contained seq-s and not-contained.
  */
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
