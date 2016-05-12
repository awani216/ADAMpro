package ch.unibas.dmi.dbis.adam.entity

import ch.unibas.dmi.dbis.adam.entity.Entity.EntityName

/**
  * adampro
  *
  * Ivan Giangreco
  * April 2016
  */
case class EntityNameHolder(originalName: String) {
  override def toString = cleanName(originalName)

  override def canEqual(a: Any) = a.isInstanceOf[EntityNameHolder] || a.isInstanceOf[String]

  override def equals(that: Any): Boolean =
    that match {
      case that: EntityNameHolder => that.canEqual(this) && toString.equals(that.toString)
      case that : String => that.canEqual(this) && originalName.equals(cleanName(that))
      case _ => false
    }

  override def hashCode: Int = originalName.hashCode

  /**
    *
    * @param str
    * @return
    */
  private def cleanName(str : String): String = str.replaceAll("[^A-Za-z_-]", "").toLowerCase()
}

object EntityNameHolder {
  implicit def EntityName2String(name: EntityName): String = name.toString

  implicit def EntityName2String(str: String): EntityName = EntityNameHolder(str)
}