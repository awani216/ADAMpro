package ch.unibas.dmi.dbis.adam.query

import ch.unibas.dmi.dbis.adam.config.FieldNames
import ch.unibas.dmi.dbis.adam.query.distance.Distance.Distance
import org.apache.spark.sql.types.{FloatType, LongType, StructField, StructType}

/**
  * adamtwo
  *
  * Ivan Giangreco
  * August 2015
  */
case class Result(distance: Distance, tid: Any) extends Ordered[Result] {
  override def compare(that: Result): Int = distance compare that.distance
}

/**
  *
  */
object Result {
  def resultSchema(pk : String) = StructType(Seq(
    StructField(pk, LongType, true),
    StructField(FieldNames.distanceColumnName, FloatType, true)
  ))
}

