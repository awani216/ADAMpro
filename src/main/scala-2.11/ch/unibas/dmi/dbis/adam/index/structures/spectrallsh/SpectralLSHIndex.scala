package ch.unibas.dmi.dbis.adam.index.structures.spectrallsh

import breeze.linalg._
import ch.unibas.dmi.dbis.adam.datatypes.{MovableFeature, Feature}
import ch.unibas.dmi.dbis.adam.datatypes.bitString.BitString
import ch.unibas.dmi.dbis.adam.table.Tuple
import Tuple._
import Feature._
import ch.unibas.dmi.dbis.adam.index.Index._
import ch.unibas.dmi.dbis.adam.index.{Index, IndexMetaStorage, IndexMetaStorageBuilder, IndexTuple}
import ch.unibas.dmi.dbis.adam.table.Table._
import org.apache.spark.FutureAction
import org.apache.spark.sql.DataFrame

/**
 * adamtwo
 *
 * Ivan Giangreco
 * August 2015
 */
class SpectralLSHIndex(val indexname: IndexName, val tablename: TableName, protected val indexdata: DataFrame, private val indexMetaData: SpectralLSHIndexMetaData)
  extends Index {
  /**
   *
   * @param q
   * @param options
   * @return
   */
  override def scan(q: WorkingVector, options: Map[String, String]): FutureAction[Seq[TupleID]] = {
    val k = options("k").toInt

    import MovableFeature.conv_feature2MovableFeature
    val queries = List.fill(5)(SpectralLSHUtils.hashFeature(q.move(indexMetaData.radius), indexMetaData))

    indexdata
      .map{ tuple =>
        IndexTuple(tuple.getLong(0), tuple.getAs[BitString[_]](1)) }
      .map { tuple =>
      val score: Int = queries.view.map{tuple.bits.intersectionCount(_)}.sum
      (tuple.tid, score)
    }.sortBy(_._2, false).map(_._1).takeAsync(k * 5)
  }

  /**
   *
   * @param metaBuilder
   */
  override private[index] def prepareMeta(metaBuilder : IndexMetaStorageBuilder) : Unit = {
    //metaBuilder.put("pca", indexMetaData.pca.toDenseMatrix)
    metaBuilder.put("pca_mat", indexMetaData.pca.toDenseMatrix.toArray)
    metaBuilder.put("pca_cols", indexMetaData.pca.cols)
    metaBuilder.put("pca_rows", indexMetaData.pca.rows)

    metaBuilder.put("min_vec", indexMetaData.min.toArray)
    //metaBuilder.put("min", indexMetaData.min.toDenseVector)
    metaBuilder.put("max_vec", indexMetaData.max.toArray)
    //metaBuilder.put("max", indexMetaData.min.toDenseVector)

    //metaBuilder.put("modes", indexMetaData.modes.toDenseMatrix)
    metaBuilder.put("modes_mat", indexMetaData.modes.toDenseMatrix.toArray)
    metaBuilder.put("modes_cols", indexMetaData.modes.cols)
    metaBuilder.put("modes_rows", indexMetaData.modes.rows)

    //metaBuilder.put("radius", indexMetaData.radius.toDenseVector)
    metaBuilder.put("radius_vec", indexMetaData.radius.toArray)
  }
}


object SpectralLSHIndex {
  /**
   *
   * @param indexname
   * @param tablename
   * @param data
   * @param meta
   * @return
   */
  def apply(indexname: IndexName, tablename: TableName, data: DataFrame, meta: IndexMetaStorage) : Index =  {
    val pca_rows = meta.get("pca_rows").toString.toInt
    val pca_cols = meta.get("pca_cols").toString.toInt
    val pca_array = meta.get[List[Double]]("pca_mat").map(_.toFloat).toArray
    //val pca = meta.get("pca").asInstanceOf[DenseMatrix[VectorBase]]
    val pca = new DenseMatrix(pca_rows, pca_cols, pca_array)

    //val minProj = meta.get("min").asInstanceOf[DenseVector[VectorBase]]
    val minProj = new DenseVector(meta.get[List[Double]]("min_vec").toArray)
    //val maxProj = meta.get("max").asInstanceOf[DenseVector[VectorBase]]
    val maxProj = new DenseVector(meta.get[List[Double]]("max_vec").toArray)

    val modes_rows = meta.get("modes_rows").toString.toInt
    val modes_cols = meta.get("modes_cols").toString.toInt
    val modes_array = meta.get[List[Double]]("modes_mat").map(_.toFloat).toArray
    //val modes = meta.get("modes").asInstanceOf[DenseMatrix[VectorBase]]
    val modes = new DenseMatrix(modes_rows, modes_cols, modes_array)

    //val radius = meta.get("radius").asInstanceOf[DenseVector[VectorBase]]
    val radius = new DenseVector(meta.get[List[Double]]("radius_vec").toArray)

    val indexMetaData = SpectralLSHIndexMetaData(pca, minProj, maxProj, modes, radius)

    new SpectralLSHIndex(indexname, tablename, data, indexMetaData)
  }
}