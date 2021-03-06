package org.vitrivr.adampro.storage

import org.apache.spark.annotation.Experimental
import org.apache.spark.sql.SaveMode
import org.vitrivr.adampro.data.entity.Entity
import org.vitrivr.adampro.process.SharedComponentContext
import org.vitrivr.adampro.utils.Logging
import org.apache.spark.sql.functions.col

import scala.util.{Failure, Success, Try}

/**
  * ADAMpro
  *
  * Ivan Giangreco
  * December 2016
  */
object Transferer extends Logging {

  /**
    *
    * @param entity
    * @param attributes
    * @param newHandlerName
    */
  @Experimental def apply(entity: Entity, attributes: Seq[String], newHandlerName: String)(implicit ac: SharedComponentContext): Try[Void] = {
    log.trace("transfering attributes " + attributes.mkString(", ") + " of entity " + entity.entityname + " to " + newHandlerName)

    try {
      val schema = entity.schema()
      assert(attributes.forall(schema.map(_.name).contains(_)))

      assert(ac.storageManager.get(newHandlerName).isDefined)
      val storagehandler = ac.storageManager.get(newHandlerName).get

      val attributetypes = schema.filter(x => attributes.contains(x.name)).map(_.attributeType).distinct
      assert(attributetypes.forall(storagehandler.supports.contains(_)))

      //TODO: check if only pk is moved (and entity contains only pk)
      //TODO: don't move if situation stays same

      log.trace("transfering attributes " + attributes.mkString(",") + " to " + newHandlerName)

      //data transfer
      val schemaAttributes = attributes.map(attribute => schema.filter(_.name == attribute).head)
      val attributesWithoutPK = schemaAttributes.filterNot(_.pk)
      val attributesWithPK = attributesWithoutPK.+:(entity.pk)

      //all attributes that should be transfered + the ones that are already in place in this storagehandler
      val attributesForNewHandler = (attributesWithPK.map(_.name) ++ (schema.filter(_.storagehandlername == newHandlerName).map(_.name))).distinct
      val data = entity.getData(Some(attributesForNewHandler.distinct)).get.repartition(ac.config.defaultNumberOfPartitions)

      log.trace("new handler will store attributes " + attributesForNewHandler.mkString(","))

      //create first "file"/"table" in new handler
      if(schema.filter(_.storagehandlername == newHandlerName).isEmpty){
        storagehandler.create(entity.entityname, attributesWithPK)

        log.trace("create file/table with attributes " + attributesWithPK.map(_.name).mkString(",") + " for handler " + newHandlerName)
      }

      //TODO: check status
      storagehandler.write(entity.entityname, data, attributesWithPK, SaveMode.Overwrite)

      //what happens with the old data handler
      schema.filterNot(_.pk).filterNot(_.storagehandlername == newHandlerName).groupBy(_.storagehandlername).foreach{ case(handlername, handlerAttributes) =>
        val handler = ac.storageManager.get(handlername).get

        log.trace("handler " + handlername + " had still attributes " + handlerAttributes.map(_.name).mkString(", "))

        if((handlerAttributes.map(_.name) diff attributes).isEmpty){
          log.trace("for handler " + handlername + " no more data is available, therefore dropping")
          //TODO: check status, only then drop
          handler.drop(entity.entityname)
        } else {
          log.trace("for handler " + handlername + " data of attributes " + handlerAttributes.map(_.name).mkString(",") + " is re-written")

          val newHandlerAttributes = handlerAttributes.filterNot(x => attributes.contains(x.name)) ++ Seq(entity.pk)
          val handlerData = entity.getData(Some(newHandlerAttributes.map(_.name))).get

          //TODO: check status
          val status = handler.write(entity.entityname, handlerData, newHandlerAttributes, SaveMode.Overwrite, Map("allowRepartitioning" -> "true", "partitioningKey" -> entity.pk.name))

          if (status.isFailure) {
            log.error("failing while transferring: " + status.failed.get.getMessage, status.failed.get)
          }
        }
      }

      //adjust attribute storage handler
      attributesWithoutPK.foreach { attribute =>
        ac.catalogManager.updateAttributeStorageHandler(entity.entityname, attribute.name, newHandlerName)
      }

      entity.markSoftStale()

      data.unpersist(true)

      Success(null)
    } catch {
      case e: Exception => Failure(e)
    }
  }
}
