package org.vitrivr.adampro.data.index.structures.va

import java.util.Comparator

import it.unimi.dsi.fastutil.doubles.{DoubleComparator, DoubleComparators, DoubleHeapPriorityQueue}
import org.apache.spark.sql.Row
import org.vitrivr.adampro.data.datatypes.TupleID._
import org.vitrivr.adampro.query.distance.Distance.Distance
import org.vitrivr.adampro.utils.Logging

import scala.collection.mutable.ListBuffer

/**
  * adamtwo
  *
  * Ivan Giangreco, improvements by Silvan Heller
  * August 2015
  */
private[va] class VAResultHandler(k: Int) extends Logging with Serializable {
  private var elementsLeft = k

  private val upperBoundQueue = new DoubleHeapPriorityQueue(2 * k, DoubleComparators.OPPOSITE_COMPARATOR)
  private val lowerBoundResultElementQueue = new ListBuffer[VAResultElement]()

  private class VAResultElementLowerBoundComparator(comparator: DoubleComparator) extends Comparator[VAResultElement] with Serializable {
    final def compare(a: VAResultElement, b: VAResultElement): Int = comparator.compare(a.ap_lower, b.ap_lower)
  }


  /**
    *
    * @param r
    */
  def offer(r: Row, pk: String): Boolean = {
    if (elementsLeft > 0) {
      //we have not yet inserted k elements, no checks therefore
      val lower = r.getAs[Distance]("ap_lbound")
      val upper = r.getAs[Distance]("ap_ubound")
      val tid = r.getAs[TupleID](pk)
      elementsLeft -= 1
      enqueueAndAddToCandidates(tid, lower, upper)
      return true
    } else {
      val lower = r.getAs[Distance]("ap_lbound")

      //we have already k elements, therefore check if new element is better
      //peek is the upper bound
      val peek = upperBoundQueue.first()
      if (peek >= lower) {
        //if peek is larger than lower, then dequeue worst element and insert new element
        upperBoundQueue.dequeue()
        val upper = r.getAs[Distance]("ap_ubound")
        val tid = r.getAs[TupleID](pk: String)
        enqueueAndAddToCandidates(tid, lower, upper)
        return true
      } else {
        return false
      }
    }
  }

  /**
    *
    * @param res
    * @param pk
    * @return
    */
  def offer(res: VAResultElement, pk: String): Boolean = {
    if (elementsLeft > 0) {
      //we have not yet inserted k elements, no checks therefore
      elementsLeft -= 1
      enqueueAndAddToCandidates(res.ap_id, res.ap_lower, res.ap_upper)
      return true
    } else {
      //we have already k elements, therefore check if new element is better
      //peek is the upper bound
      val peek = upperBoundQueue.first()
      if (peek >= res.ap_lower) {
        //if peek is larger than lower, then dequeue worst element and insert new element
        upperBoundQueue.dequeue()
        enqueueAndAddToCandidates(res.ap_id, res.ap_lower, res.ap_upper)
        return true
      } else {
        return false
      }
    }
  }

  /**
    *
    * @param tid
    * @param lower
    * @param upper
    */
  private def enqueueAndAddToCandidates(tid: TupleID, lower: Distance, upper: Distance): Unit = {
    enqueueAndAddToCandidates(VAResultElement(tid, lower, upper, (lower + upper) / 2.0))
  }

  /**
    *
    * @param res
    */
  private def enqueueAndAddToCandidates(res: VAResultElement): Unit = {
    upperBoundQueue.enqueue(res.ap_upper)
    lowerBoundResultElementQueue += res
  }


  /**
    *
    * @return
    */
  def results = {
    val maxUpperBound = if(upperBoundQueue.isEmpty){
      Double.PositiveInfinity
    } else {
      upperBoundQueue.first().toDouble
    }

    lowerBoundResultElementQueue.filterNot(_.ap_lower > maxUpperBound)
  }

}