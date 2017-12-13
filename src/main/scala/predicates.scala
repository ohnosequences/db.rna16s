package ohnosequences.db.rna16s

import ohnosequences.api.rnacentral._
import ohnosequences.fastarious._, fasta._

case object predicates {

  val joinDescriptions: Entry => String =
    _.sequenceAnnotations.map(_.description).mkString(" | ")

  // TODO add DB ID and version
  val entryToFASTA: Entry => FASTA =
    entry =>
      FASTA(
        Header(s"${entry.rnaID} ${joinDescriptions(entry)}"),
        Sequence(entry.sequence)
      )

  type +[A,B] = Either[A,B]

  sealed abstract class Dropped {

    def entry: Entry
  }
  case object Dropped {

    case class FromExcludedDBs(entry: Entry)        extends Dropped
    case class isNotrRNA(entry: Entry)              extends Dropped
    case class No16sAnnotation(entry: Entry)        extends Dropped
    case class InvalidSequenceLength(length: Int, entry: Entry)  extends Dropped
    case class LowQualitySequence(entry: Entry)     extends Dropped 
  }

  lazy val is16s: Entry => Dropped + Entry =
    onlyValidDatabaseInfo(_)
      .right.flatMap(has16sLength)
      .right.flatMap(hasRNAType16s)
      .right.flatMap(someDescriptionContains16s)
      .right.flatMap(sequenceQualityOK)

  lazy val onlyValidDatabaseInfo: Entry => Dropped + Entry =
    entry => {

      val validEntryAnnotations =
        entry.entryAnnotations
          .filter { a => validDatabases contains a.databaseEntry.database }

      val validSequenceAnnotations =
        entry.sequenceAnnotations
          .filter { sa => 
            validEntryAnnotations exists { 
              _.ncbiTaxonomyID == sa.ncbiTaxonomyID 
            } 
          }
      if(validEntryAnnotations.isEmpty || validSequenceAnnotations.isEmpty)
        Left(Dropped.FromExcludedDBs(entry))
      else
        Right(
          entry.copy(
            entryAnnotations    = validEntryAnnotations,
            sequenceAnnotations = validSequenceAnnotations
          )
        ) 
    }

  lazy val has16sLength: Entry => Dropped + Entry =
    entry =>
      if(hasLength16s(entry))
        Right(entry)
      else
        Left(Dropped.InvalidSequenceLength(entry.sequence.length, entry))

  lazy val length16s: Int => Boolean =
    len => len >= 1300 && len <= 1700

  lazy val hasLength16s: Entry => Boolean =
    entry => length16s(entry.sequence.length)

  lazy val rnaType16s: RNAType =
    RNAType.rRNA

  lazy val hasRNAType16s: Entry => Dropped + Entry =
    entry =>
      if(entry.entryAnnotations exists { _.rnaType == rnaType16s })
        Right(entry)
      else
        Left(Dropped.isNotrRNA(entry))

  lazy val someDescriptionContains16s: Entry => Dropped + Entry =
    entry =>
      if(entry.sequenceAnnotations exists { a => contains16sStr(a.description) })
        Right(entry)
      else
        Left(Dropped.No16sAnnotation(entry))
    
  lazy val contains16sStr: String => Boolean =
    _.toUpperCase containsSlice "16S"

  lazy val validDatabases: Set[Database] = {
    
    import Database._
  
    Set(
      ENA         ,
      RefSeq      ,
      GreenGenes  ,
      RDP         , 
      Rfam        
    )
  }

  lazy val comesFromAValidDatabase: Entry => Boolean =
    entry =>
      overlap(
        validDatabases, 
        entry.entryAnnotations.map(_.databaseEntry.database)
      )

  lazy val sequenceQualityOK: Entry => Dropped + Entry =
    entry =>
      if(
        ratioOfNs(entry.sequence) <= 0.01       &&
        ! containsSliceOfNs(5)(entry.sequence)
      )
        Right(entry)
      else
        Left(Dropped.LowQualitySequence(entry))


  lazy val containsSliceOfNs: Int => String => Boolean =
    number => {

      val Ns = 
        Seq.fill(number)('N').mkString
      
      { seq => seq.map(_.toUpper) containsSlice Ns }
    }
      

  lazy val ratioOfNs: String => Float =
    seq =>
      if(seq.isEmpty) 0 else seq.count(_.toUpper == 'N') / seq.length

  //////////////////////////////////////////////////////////////////////////////

  private def overlap[X]: (Set[X], Set[X]) => Boolean =
    (xs1, xs2) =>
      xs1.foldLeft(false) { 
        (flag, elem) => 
          if(flag) flag else xs2 contains elem
      }

  implicit class PredicateOps[X](val p: X => Boolean) {

    def &&(other: X => Boolean): X => Boolean =
      x => p(x) && other(x)
  }  
}