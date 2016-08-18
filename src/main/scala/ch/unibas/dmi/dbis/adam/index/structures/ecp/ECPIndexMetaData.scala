package ch.unibas.dmi.dbis.adam.index.structures.ecp

import ch.unibas.dmi.dbis.adam.datatypes.feature.Feature.FeatureVector
import ch.unibas.dmi.dbis.adam.query.distance.DistanceFunction


/**
 * adamtwo
 *
 * Ivan Giangreco
 * October 2015
 */
@SerialVersionUID(100L)
private[ecp] case class ECPIndexMetaData(leaders : Seq[ECPLeader], distance : DistanceFunction) {}

private[ecp] case class ECPLeader(id: Int, feature: FeatureVector, count : Long)