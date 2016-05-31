package era7bio.db

import ohnosequences.blast.api._
import ohnosequences.fastarious.fasta._
import ohnosequences.awstools._, ec2._, InstanceType._, s3._, regions._
import ohnosequences.statika._, aws._

import com.github.tototoshi.csv._
import era7bio.db.RNACentral5._
import era7bio.db.csvUtils._
import era7bio.db.collectionUtils._

import ohnosequencesBundles.statika._

import com.thinkaurelius.titan.core._, schema._
import com.bio4j.model.ncbiTaxonomy.NCBITaxonomyGraph._
import com.bio4j.titan.model.ncbiTaxonomy._
import com.bio4j.titan.util.DefaultTitanGraph

import better.files._


case object bio4jTaxonomyBundle extends AnyBio4jDist {

  lazy val s3folder: S3Folder = S3Folder("resources.ohnosequences.com", "16s/bio4j-taxonomy/")

  lazy val configuration = DefaultBio4jTitanConfig(dbLocation)

  // the graph; its only (direct) use is for indexes
  // FIXME: this works but still with errors, should be fixed (something about transactions)
  lazy val graph: TitanNCBITaxonomyGraph =
    new TitanNCBITaxonomyGraph(
      new DefaultTitanGraph(TitanFactory.open(configuration))
    )


  type TaxonNode = com.bio4j.model.ncbiTaxonomy.vertices.NCBITaxon[
    DefaultTitanGraph,
    TitanVertex, VertexLabelMaker,
    TitanEdge, EdgeLabelMaker
  ]

  /* **NOTE** all methods here work with a non-reflexive definition of ancestor/descendant */
  implicit class IdOps(val id: String) extends AnyVal {

    // Java to Scala
    private def optional[T](jopt: java.util.Optional[T]): Option[T] =
      if (jopt.isPresent) Some(jopt.get) else None

    def hasEnvironmentalSamplesAncestor: Boolean = {

      def hasEnvironmentalSamplesAncestor_rec(node: TaxonNode): Boolean =
        optional(node.ncbiTaxonParent_inV) match {
          case None => false
          case Some(parent) =>
            if (parent.name == "environmental samples") true
            else hasEnvironmentalSamplesAncestor_rec(parent)
        }

      optional(graph.nCBITaxonIdIndex.getVertex(id))
        .fold(false)(hasEnvironmentalSamplesAncestor_rec)
    }

    def isDescendantOfOneIn(ancestors: Set[String]): Boolean = {

      @annotation.tailrec
      def isDescendantOfOneIn_rec(node: TaxonNode): Boolean =
        optional(node.ncbiTaxonParent_inV) match {
          case None => false
          case Some(parent) =>
            if (ancestors.contains( parent.id() )) true
            else isDescendantOfOneIn_rec(parent)
        }

      optional(graph.nCBITaxonIdIndex.getVertex(id))
        .map(isDescendantOfOneIn_rec)
        .getOrElse(false)
    }

    private def unclassifiedBacteriaID = 2323

    def isDescendantOfUnclassifiedBacteria: Boolean = isDescendantOfOneIn( Set(unclassifiedBacteriaID.toString) )

    def hasDescendantOrItselfUnclassified: Boolean = {

      def hasDescendantOrItselfUnclassified_rec(node: TaxonNode): Boolean =
        optional(node.ncbiTaxonParent_inV) match {
          case None => false
          case Some(parent) =>
            if ( parent.name.contains("unclassified") ) true
            else hasDescendantOrItselfUnclassified_rec(parent)
        }

      optional(graph.nCBITaxonIdIndex.getVertex(id))
        .fold(false)(hasDescendantOrItselfUnclassified_rec)
    }
  }
}

/*
  ## 16S RNA BLAST database

  This contains the specification of our 16S BLAST database. All sequences are obtained from RNACentral, with sequences satisfying `predicate` being those included.
*/
case object rna16s extends AnyBlastDB {

  val name = "era7bio.db.rna16s"

  val dbType = BlastDBType.nucl

  val rnaCentralRelease: RNACentral5.type = RNACentral5

  val s3location: S3Folder = S3Folder("resources.ohnosequences.com", generated.metadata.db.rna16s.organization) /
    generated.metadata.db.rna16s.artifact /
    generated.metadata.db.rna16s.version.stripSuffix("-SNAPSHOT") /

  /* We are using the ribosomal RNA type annotation on RNACentral as a first catch-all filter. We are aware of the existence of a gene annotation corresponding to 16S, that we are **not using** due to a significant amount of 16S sequences lacking the corresponding annotation. */
  val ribosomalRNAType = "rRNA"

  /* The sequence length threshold for a sequence to be admitted as 16S. */
  val minimum16SLength: Int = 1300

  /* Taxa IDs for archaea and bacteria */
  val archaeaTaxonID  = 2157
  val bacteriaTaxonID = 2

  /* These are NCBI taxonomy IDs corresponding to taxa which are at best uniformative. The `String` value is the name of the corresponding taxon, for documentation purposes. */
  val uninformativeTaxIDsMap = Map(
    32644   -> "unclassified",
    2323    -> "unclassified Bacteria",
    4992    -> "unclassified Bacteria (miscellaneous)", // LOL
    118884  -> "unclassified Gammaproteobacteria",
    358574  -> "uncultured microorganism",
    155900  -> "uncultured organism",
    415540  -> "uncultured marine microorganism",
    198431  -> "uncultured prokaryote",
    77133   -> "uncultured bacterium",
    115547  -> "uncultured archaeon",
    56763   -> "uncultured marine archaeon",
    152507  -> "uncultured actinobacterium",
    1211    -> "uncultured cyanobacterium",
    153809  -> "uncultured proteobacterium",
    91750   -> "uncultured alpha proteobacterium",
    86027   -> "uncultured beta proteobacterium",
    86473   -> "uncultured gamma proteobacterium",
    34034   -> "uncultured delta proteobacterium",
    56765   -> "uncultured marine bacterium",
    115414  -> "uncultured marine alpha proteobacterium"
  )

  val uninformativeTaxIDs: Set[String] = uninformativeTaxIDsMap.keys.map(_.toString).toSet

  /* and here we have RNACentral entries which we think are poorly assigned> This is list is by no means exhaustive, though its value can hardly be understimated. */
  val blacklistedRNACentralIDs = Set(
    "URS00008CD63B",  // claims to be Lactobacilus plantarum, it is an Enterococcus
    "URS00008CCF2E",  // claims to be Candidatus Hepatobacter penaei, it is a Pseudomonas
    "URS00007EE21F",  // claims to be Pseudomonas sp. NT 6-08, it is a Staph aureus
    "URS00008C61AD",  // claims to be Yersinia pestis biovar Orientalis str. AS200901509, it is a Staph aureus
    "URS00008E71FD",  // claims to be Staphylococcus sciuri, it is a Pseudomonas
    "URS000089CEEE",  // claims to be Bacillus sp. W4(2008), it is a Pseudomonas
    "URS0000974DB8",  // claims to be Pseudomonas sp. CL3.1, it is a Bacillus
    "URS00008E9E3B",  // claims to be Pantoea sp. CR30, it is a Bacillus
    "URS00008DEF63",  // claims to be Microbacterium oxydans, it is a (fragment of) Bacillus
    "URS000082C8CF",  // claims to be Streptococcus pneumoniae, it is a Bacillus plus some chimeric sequence
    "URS0000874571",  // claims to be Bordetella, it is a Pseudomonas aeruginosa
    "URS00008A3994",  // claims to be Rhodococcus, it is a Pseudomonas aeruginosa
    "URS00008898AD",  // claims to be Rhodococcus, it is a Pseudomonas aeruginosa
    "URS0000215B45",  // claims to be Vibrio cholerae HC-02A1, it is an Enterococcus faecalis
    "URS00008239BE",  // claims to be Mycobacterium abscessus, it is an Acinetobacter
    "URS000074A9F2",  // claims to be Prolinoborus fasciculus, it is an Acinetobacter
    "URS0000735DC4",  // claims to be Prolinoborus fasciculus, it is an Acinetobacter
    "URS00005BB216",  // claims to be Prolinoborus fasciculus, it is an Acinetobacter
    "URS0000590E49",  // claims to be Prolinoborus fasciculus, it is an Acinetobacter
    "URS0000865688",  // claims to be Prolinoborus fasciculus, it is an Acinetobacter
    "URS000085F838",  // claims to be Prolinoborus fasciculus, it is an Acinetobacter
    "URS000074A9F2"   // claims to be Prolinoborus fasciculus, it is an Acinetobacter
  )

  /*
    #### Database defining predicate

    Sequences that satisfy this predicate (on themselves together with their annotation) are included in this database.
  */
  import bio4jTaxonomyBundle._
  val predicate: (Row, FASTA.Value) => Boolean = { (row, fasta) =>
    /* is not blacklisted */
    ( ! blacklistedRNACentralIDs.contains(row.select(id)) )                                             &&
    /* - are annotated as rRNA */
     row.select(rna_type).contains(ribosomalRNAType)                                                    &&
    /* - their taxonomy association is *not* one of those in `uninformativeTaxIDs` */
    ( ! uninformativeTaxIDs.contains(row.select(tax_id)) )                                              &&
    /* - the corresponding sequence is not shorter than the threshold */
    (fasta.getV(sequence).value.length >= minimum16SLength)                                             &&
    /* - is a descendant of either Archaea or Bacteria */
    row.select(tax_id).isDescendantOfOneIn( Set( archaeaTaxonID.toString, bacteriaTaxonID.toString ) )  &&
    /* - is not a descendant of an "environmental samples" or unclassified taxon */
    ( ! row.select(tax_id).hasEnvironmentalSamplesAncestor )                                            &&
    ( ! row.select(tax_id).isDescendantOfUnclassifiedBacteria )                                         &&
    ( ! row.select(tax_id).hasDescendantOrItselfUnclassified )
  }

  // bundle to generate the DB (see the runBundles file in tests)
  case object generate extends GenerateBlastDB(this)(bio4jTaxonomyBundle) {

    def processSources(
      tableInFile: File,
      tableOutFile: File,
      tableDiscardedFile: File
    )(fastaInFile: File,
      fastaOutFile: File,
      fastaDiscardedFile: File
    ) {

      val tableReader = CSVReader.open(tableInFile.toJava)(tableFormat)
      val tableOutWriter = CSVWriter.open(tableOutFile.toJava, append = true)(tableFormat)
      val tableDiscardedWriter = CSVWriter.open(tableDiscardedFile.toJava, append = true)(tableFormat)

      println("Reading FASTA...")
      val fastas: Stream[FASTA.Value] = parseFastaDropErrors(fastaInFile.lines).toStream

      println("Reading table...")
      val groupedRows: Stream[(String, Stream[Row])] = tableReader.iterator.toStream.group{ _.select(id) }

      (groupedRows zip fastas) foreach { case ((commonID, rows), fasta) =>

        if (commonID != fasta.getV(header).id)
          sys.error(s"ID [${commonID}] is not found in the FASTA. Check RNACentral filtering.")

        val extID = s"${commonID}|lcl|${db.name}"

        val (goodRows, badRows) = rows.partition{ db.predicate(_, fasta) }

        // println(s"${goodRows.length}\t${badRows.length}")

        if (badRows.nonEmpty) {
          badRows.foreach( tableDiscardedWriter.writeRow )
          fastaDiscardedFile.appendLine( fasta.asString )
        }

        val taxas: Set[String] = goodRows.map{ _.select(tax_id) }.toSet

        if (taxas.nonEmpty) {
          tableOutWriter.writeRow( Seq(extID, taxas.mkString(";")) )

          fastaOutFile.appendLine(
            fasta.update(
              header := FastaHeader(s"${extID} ${fasta.getV(header).description}")
            ).asString
          )
        }
      }

      tableReader.close()
      tableOutWriter.close()
      tableDiscardedWriter.close()
    }

  }

  // bundle to obtain and use the generated release
  case object release extends BlastDBRelease(generate) {

    val blastDBS3 = era7bio.db.rna16s.s3location / "blastdb" /
    val id2taxasS3 = era7bio.db.rna16s.s3location / "data" / "id2taxa.tsv"
  }

  case object filterCovered extends GenerateBlastDB(this)() {
    type ID = String
    type Taxa = String
    type Fasta = FASTA.Value
    type Eith[X] = Either[X, X]

    def processSources(
      tableInFile: File,
      tableOutFile: File,
      tableDiscardedFile: File
    )(fastaInFile: File,
      fastaOutFile: File,
      fastaDiscardedFile: File
    ) {
      val leftWriter = CSVWriter.open(tableDiscardedFile.toJava, append = true)(tableFormat)
      val rightWriter = CSVWriter.open(tableOutFile.toJava, append = true)(tableFormat)

      // id1 -> fasta1
      // id2 -> fasta2
      val id2fasta: Map[ID, Fasta] =
        parseFastaDropErrors(fastaInFile.lines)
          .foldLeft(Map[ID, Fasta]()) { (acc, fasta) =>
            acc.updated( fasta.getV(header).id, fasta )
          }

      // id1 -> taxa1; taxa2; taxa3
      val id2taxas: Map[ID, Seq[Taxa]] = CSVReader.open(tableInFile.toJava)(tableFormat)
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
          fastaDiscardedFile.appendLine( id2fasta(id).asString )
        } else {
          fastaOutFile.appendLine( id2fasta(id).asString )
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
}
