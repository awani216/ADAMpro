package org.vitrivr.adampro.data.index.structures.va

import org.apache.spark.broadcast.Broadcast
import org.apache.spark.sql.DataFrame
import org.apache.spark.sql.functions._
import org.vitrivr.adampro.config.AttributeNames
import org.vitrivr.adampro.data.datatypes.bitstring.BitString
import org.vitrivr.adampro.data.datatypes.vector.Vector._
import org.vitrivr.adampro.data.index.Index
import org.vitrivr.adampro.data.index.Index.{IndexName, IndexTypeName}
import org.vitrivr.adampro.data.index.structures.IndexTypes
import org.vitrivr.adampro.data.index.structures.va.VAIndex._
import org.vitrivr.adampro.data.index.structures.va.signature.{FixedSignatureGenerator, VariableSignatureGenerator}
import org.vitrivr.adampro.process.SharedComponentContext
import org.vitrivr.adampro.query.distance.Distance._
import org.vitrivr.adampro.query.distance.{DistanceFunction, MinkowskiDistance}
import org.vitrivr.adampro.query.query.RankingQuery
import org.vitrivr.adampro.query.tracker.QueryTracker


/**
  * adamtwo
  *
  * Ivan Giangreco
  * August 2015
  */
class VAIndex(override val indexname: IndexName)(@transient override implicit val ac: SharedComponentContext)
  extends Index(indexname)(ac) {

  val meta = metadata.get.asInstanceOf[VAIndexMetaData]

  override lazy val indextypename: IndexTypeName = meta.signatureGenerator match {
    case fsg: FixedSignatureGenerator => IndexTypes.VAFINDEX
    case vsg: VariableSignatureGenerator => IndexTypes.VAVINDEX
  }

  override lazy val lossy: Boolean = false
  override lazy val confidence = 1.toFloat
  override lazy val score: Float = if (indextypename.equals(IndexTypes.VAFINDEX)) {
    0.9.toFloat //slightly less weight if fixed variable
  } else {
    1.toFloat
  }

  /**
    *
    * @param data     rdd to scan
    * @param q        query vector
    * @param distance distance funciton
    * @param options  options to be passed to the index reader
    * @param k        number of elements to retrieve (of the k nearest neighbor search), possibly more than k elements are returned
    * @return a set of candidate tuple ids, possibly together with a tentative score (the number of tuples will be greater than k)
    */
  override def scan(data: DataFrame, q: MathVector, distance: DistanceFunction, options: Map[String, String], k: Int)(tracker: QueryTracker): DataFrame = {
    log.trace("scanning VA-File index")

    //compute bounds
    val bounds = computeBounds(q, meta.marks, distance.asInstanceOf[MinkowskiDistance])

    //compress and broadcast bounds
    val (lbIndex, lbBounds) = compressBounds(bounds._1)
    val lbIndexBc = ac.sc.broadcast(lbIndex)
    tracker.addBroadcast(lbIndexBc)
    val lbBoundsBc = ac.sc.broadcast(lbBounds)
    tracker.addBroadcast(lbBoundsBc)

    val (ubIndex, ubBounds) = compressBounds(bounds._2)
    val ubIndexBc = ac.sc.broadcast(ubIndex)
    tracker.addBroadcast(ubIndexBc)
    val ubBoundsBc = ac.sc.broadcast(ubBounds)
    tracker.addBroadcast(ubBoundsBc)

    log.trace("computing compressed VA bounds done")

    val cellsDistUDF = (boundsIndexBc: Broadcast[CompressedBoundIndex], boundsBoundsBc: Broadcast[CompressedBoundBounds]) => udf((cells : Seq[Short]) => {
      var bound: Distance = 0
      var idx = 0
      while (idx < cells.length) {
        val cellsIdx = if(cells(idx) < 0) {(Short.MaxValue + 1) * 2 + cells(idx)} else {cells(idx)}
        bound += boundsBoundsBc.value(boundsIndexBc.value(idx) + cellsIdx)
        idx += 1
      }

      bound
    })

    import ac.spark.implicits._

    val boundedData = data
      .withColumn("ap_lbound", cellsDistUDF(lbIndexBc, lbBoundsBc)(col(AttributeNames.featureIndexColumnName)))
      .withColumn("ap_ubound", cellsDistUDF(ubIndexBc, ubBoundsBc)(col(AttributeNames.featureIndexColumnName))) //note that this is computed lazy!

    val pk = this.pk.name.toString

    //local filtering
    val localRes = boundedData.coalesce(ac.config.defaultNumberOfPartitionsIndex)
      .mapPartitions(pIt => {
        //in here  we compute for each partition the k nearest neighbours and collect the results
        val localRh = new VAResultHandler(k)

        while (pIt.hasNext) {
          val current = pIt.next()
          localRh.offer(current, pk)
        }

        localRh.results.iterator
      })

    log.trace("local VA scan done")

    /*import ac.spark.implicits._
    val minUpperPart = localRes
      .mapPartitions(pIt => Seq(pIt.maxBy(_.ap_upper)).iterator).agg(min("ap_upper")).collect()(0).getDouble(0)

    val res = localRes.filter(_.ap_lower <= minUpperPart).toDF()*/

    val res = if (ac.config.vaGlobalRefinement || options.get("vaGlobal").map(_.toBoolean).getOrElse(false)) {
      // global refinement
      val globalRh = new VAResultHandler(k)
      val gIt = localRes.collect.iterator

      while (gIt.hasNext) {
        val current = gIt.next()
        globalRh.offer(current, pk)
      }

      ac.sqlContext.createDataset(globalRh.results).toDF()
    } else {
      localRes.toDF()
    }

    log.trace("global VA scan done")

    res
  }

  override def isQueryConform(nnq: RankingQuery): Boolean = {
    if (nnq.distance.isInstanceOf[MinkowskiDistance]) {
      return true
    }

    false
  }

  /**
    * Computes the distances to all bounds.
    *
    * @param q        query vector
    * @param marks    marks
    * @param distance distance function
    * @return
    */
  private[this] def computeBounds(q: MathVector, marks: => Marks, @inline distance: MinkowskiDistance): (Bounds, Bounds) = {
    val lbounds, ubounds = Array.tabulate(marks.length)(i => Array.ofDim[Distance](math.max(0, marks(i).length - 1)))

    var i = 0
    while (i < marks.length) {
      val dimMarks = marks(i)
      val fvi = q(i)

      var j = 0
      val it = dimMarks.iterator.sliding(2).withPartial(false)

      while (it.hasNext) {
        val dimMark = it.next()

        val d0fv1 = distance.element(dimMark(0), fvi)
        val d1fv1 = distance.element(dimMark(1), fvi)

        if (fvi < dimMark(0)) {
          lbounds(i)(j) = d0fv1
        } else if (fvi > dimMark(1)) {
          lbounds(i)(j) = d1fv1
        }

        if (fvi <= (dimMark(0) + dimMark(1)) / 2.toFloat) {
          ubounds(i)(j) = d1fv1
        } else {
          ubounds(i)(j) = d0fv1
        }

        j += 1
      }

      i += 1
    }

    (lbounds, ubounds)
  }


  /**
    * Compresses the bounds to a one-dim float array
    *
    * @param bounds
    * @return
    */
  private[this] def compressBounds(bounds: Bounds): CompressedBound = {
    val lengths = bounds.map(_.length)
    val totalLength = lengths.sum

    val cumLengths = {
      //compute cumulative sum of lengths

      val _cumLengths = new Array[Int](lengths.length)

      var i: Int = 0
      var cumSum: Int = 0
      while (i < lengths.length - 1) {
        cumSum += lengths(i)
        _cumLengths(i + 1) = cumSum

        i += 1
      }

      _cumLengths
    }


    val newBounds = {
      val _newBounds = new Array[Float](totalLength)


      var i, j: Int = 0

      while (i < lengths.length) {
        j = 0
        while (j < lengths(i)) {
          _newBounds(cumLengths(i) + j) = bounds(i)(j).toFloat
          j += 1
        }
        i += 1
      }

      _newBounds
    }


    (cumLengths, newBounds)
  }
}


object VAIndex {
  type Marks = Seq[Seq[VectorBase]]
  type Bounds = Array[Array[Distance]]
  type CompressedBound = (CompressedBoundIndex, CompressedBoundBounds)
  type CompressedBoundIndex = Array[Int]
  type CompressedBoundBounds = Array[Float]
}