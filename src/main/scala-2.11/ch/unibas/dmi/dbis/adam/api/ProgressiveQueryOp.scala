package ch.unibas.dmi.dbis.adam.api

import ch.unibas.dmi.dbis.adam.datatypes.feature.Feature
import Feature.FeatureVector
import ch.unibas.dmi.dbis.adam.query.progressive.ProgressiveQueryStatus
import ch.unibas.dmi.dbis.adam.query.query.NearestNeighbourQuery
import ch.unibas.dmi.dbis.adam.query.{QueryHandler, Result}
import ch.unibas.dmi.dbis.adam.query.distance.DistanceFunction
import ch.unibas.dmi.dbis.adam.entity.Entity._

/**
 * adamtwo
 *
 * Ivan Giangreco
 * September 2015
 */
object ProgressiveQueryOp {
  /**
   *
   * @param entityname
   * @param qv
   * @param k
   * @param distance
   */
  def apply(entityname: EntityName, qv : FeatureVector, k : Int, distance : DistanceFunction, onComplete : (ProgressiveQueryStatus.Value, Seq[Result], Float, Map[String, String]) => Unit) : Int = {
    val query = NearestNeighbourQuery(qv, distance, k)
    QueryHandler.progressiveQuery(entityname, query, None, onComplete)
  }
}
