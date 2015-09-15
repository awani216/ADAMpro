package ch.unibas.dmi.dbis.adam.datatypes.bitString

import java.io.{ObjectInputStream, ByteArrayInputStream, ByteArrayOutputStream, ObjectOutputStream}

import com.zaxxer.sparsebits.SparseBitSet

import scala.collection.mutable.ListBuffer

/**
 * adamtwo
 *
 * Ivan Giangreco
 * September 2015
 */
class SparseBitSetBitString(private val values : SparseBitSet) extends BitString[SparseBitSetBitString] with Serializable {
  /**
   *
   * @param other
   * @return
   */
  override def intersectionCount(other : SparseBitSetBitString) : Int = {
    val cloned = values.clone()
    cloned.and(other.values)
    cloned.cardinality()
  }

  /**
   *
   * @param start
   * @param end
   * @return
   */
  override def get(start : Int, end : Int) : Int = {
    ???
  }

  /**
   *
   * @return
   */
  override def getIndexes : Seq[Int] = {
    val indexes = ListBuffer[Int]()
    var nextIndex : Int = -1

    do {
      nextIndex += 1
      nextIndex = values.nextSetBit(nextIndex)

      if(nextIndex != -1){
        indexes += nextIndex
      }

    } while(nextIndex != -1)

    indexes.toList
  }

  /**
   *
   * @return
   */
  override def toLong : Long = {
    getIndexes.map(x => math.pow(2, x).toLong).sum
  }

  /**
   *
   * @return
   */
  override def toByteArray : Array[Byte] = {
    val baos = new ByteArrayOutputStream()
    val o = new ObjectOutputStream(baos)
    o.writeObject(values)
    baos.toByteArray
  }
}


object SparseBitSetBitString extends BitStringFactory[SparseBitSetBitString] {
  /**
   *
   * @param values
   * @return
   */
  override def fromBitIndicesToSet(values: Seq[Int]): BitString[SparseBitSetBitString] = {
    val max = if(values.length == 0) 0 else values.max

    val bitSet = new SparseBitSet(max)
    values.foreach{bitSet.set(_)}
    new SparseBitSetBitString(bitSet)
  }

  /**
   *
   * @param values
   * @return
   */
  override def fromByteSeq(values: Seq[Byte]): BitString[SparseBitSetBitString] = {
    val bais = new ByteArrayInputStream(values.toArray)
    val o = new ObjectInputStream(bais)
    val bitSet = o.readObject().asInstanceOf[SparseBitSet]
    new SparseBitSetBitString(bitSet)
  }
}