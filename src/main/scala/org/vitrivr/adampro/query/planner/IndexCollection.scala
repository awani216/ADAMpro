package org.vitrivr.adampro.query.planner

import org.vitrivr.adampro.communication.api.IndexOp
import org.vitrivr.adampro.data.entity.Entity
import org.vitrivr.adampro.data.entity.Entity._
import org.vitrivr.adampro.utils.exception.GeneralAdamException
import org.vitrivr.adampro.query.tracker.QueryTracker
import org.vitrivr.adampro.data.index.Index
import org.vitrivr.adampro.process.SharedComponentContext
import org.vitrivr.adampro.query.distance.EuclideanDistance
import org.vitrivr.adampro.query.query.QueryHints
import org.vitrivr.adampro.query.query.QueryHints.{IndexQueryHint, QueryHint}
import org.vitrivr.adampro.utils.Logging

/**
  * ADAMpro
  *
  * Ivan Giangreco
  * September 2016
  */
private[query] trait IndexCollection extends Logging with Serializable {
  def getIndexes: Seq[Index]
}

/**
  * Collects all existing indexes.
  *
  * @param entityname
  */
private[planner] case class ExistingIndexCollection(entityname: EntityName, attribute: String)(@transient implicit val ac: SharedComponentContext) extends IndexCollection {
  def this(entityname: EntityName, attribute: String, params: Map[String, String])(implicit ac: SharedComponentContext) {
    this(entityname, attribute)
  }

  override def getIndexes: Seq[Index] = Entity.load(entityname).get.indexes.filterNot(_.isFailure).map(_.get).filter(_.attribute == attribute)
}

/**
  * Generates new indexes based on hints.
  *
  * @param entityname
  */
private[planner] case class NewIndexCollection(entityname: EntityName, attribute: String, hints: Seq[QueryHint])(@transient implicit val ac: SharedComponentContext) extends IndexCollection {
  def this(entityname: EntityName, attribute: String, params: Map[String, String])(implicit ac: SharedComponentContext) {
    this(entityname, attribute, params.get("hints").get.split(",").map(QueryHints.withName(_)).filter(_.isDefined).map(_.get))
  }

  private def combine[A](xs: Traversable[Traversable[A]]): Seq[Seq[A]] = {
    xs.foldLeft(Seq(Seq.empty[A])) {
      (x, y) => for (a <- x.view; b <- y) yield a :+ b
    }
  }

  override def getIndexes: Seq[Index] = {
    val tracker = new QueryTracker()

    val indexes = hints.flatMap { hint =>
      if (hint.isInstanceOf[IndexQueryHint]) {
        val iqh = hint.asInstanceOf[IndexQueryHint]

        val factory = iqh.structureType.indexGeneratorFactoryClass.newInstance()

        val params: Seq[Map[String, String]] = combine(factory.parametersInfo.map(x => x.suggestedValues.map(v => x.name -> v))).map(_.toMap)

        params.map { param =>
          IndexOp.create(entityname, attribute, iqh.structureType, EuclideanDistance, param)(tracker)
        }
      } else {
        log.error("only index query hints accepted")
        null
      }

    }.filter(x => x != null && x.isSuccess).map(_.get)

    tracker.cleanAll()

    indexes
  }
}

/**
  * Collects a manual list of indexes
  *
  * @param indexes
  */
private[planner] case class ManualIndexCollection(indexes : Seq[Index])(@transient implicit val ac: SharedComponentContext) extends IndexCollection {
  override def getIndexes: Seq[Index] = indexes
}

object IndexCollectionFactory {

  def apply(entityname: EntityName, attribute: String, ico: IndexCollectionOption, params: Map[String, String])(implicit ac: SharedComponentContext): IndexCollection = {
    ico match {
      case NewIndexCollectionOption => new NewIndexCollection(entityname, attribute, params)
      case ExistingIndexCollectionOption => new ExistingIndexCollection(entityname, attribute, params)
      case _ => throw new GeneralAdamException("index collection option not known")
    }
  }

  sealed abstract class IndexCollectionOption

  case object ExistingIndexCollectionOption extends IndexCollectionOption

  case object NewIndexCollectionOption extends IndexCollectionOption

  case object ManualIndexCollectionOption extends IndexCollectionOption

}