package era7bio.db

import ohnosequences.awstools.s3._
import ohnosequences.statika._

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
