package ch.unibas.dmi.dbis.adam.storage.engine

import ch.unibas.dmi.dbis.adam.datatypes.FieldTypes
import ch.unibas.dmi.dbis.adam.datatypes.FieldTypes.FieldType
import ch.unibas.dmi.dbis.adam.entity.AttributeDefinition
import ch.unibas.dmi.dbis.adam.main.AdamContext
import ch.unibas.dmi.dbis.adam.utils.Logging
import org.apache.solr.client.solrj.SolrQuery
import org.apache.solr.client.solrj.impl.HttpSolrClient
import org.apache.solr.client.solrj.request.CoreAdminRequest
import org.apache.solr.common.SolrInputDocument
import org.apache.spark.sql.types.{StructField, StructType}
import org.apache.spark.sql.{DataFrame, Row, SaveMode}

import scala.collection.mutable.ListBuffer
import scala.util.{Failure, Success, Try}

/**
  * ADAMpro
  *
  * Ivan Giangreco
  * September 2016
  */
class SolrEngine(private val url: String) extends Engine with Logging with Serializable {
  override val name: String = "solr"

  override def supports = Seq(FieldTypes.AUTOTYPE, FieldTypes.INTTYPE, FieldTypes.LONGTYPE, FieldTypes.FLOATTYPE, FieldTypes.DOUBLETYPE, FieldTypes.STRINGTYPE, FieldTypes.TEXTTYPE, FieldTypes.BOOLEANTYPE)

  override def specializes: Seq[FieldType] = Seq(FieldTypes.TEXTTYPE)

  private val SOLR_OPTION_ENTITYNAME = "storing-solr-corename"
  private val SOLR_OPTION_FIELDNAME = "storing-solr-fieldname"

  /**
    *
    * @param props
    */
  def this(props: Map[String, String]) {
    this(props.get("url").get)
  }


  /**
    * Create the entity.
    *
    * @param storename  adapted entityname to store feature to
    * @param attributes attributes of the entity (w.r.t. handler)
    * @param params     creation parameters
    * @return
    */
  override def create(storename: String, attributes: Seq[AttributeDefinition], params: Map[String, String])(implicit ac: AdamContext): Try[Map[String, String]] = {
    val client = new HttpSolrClient(url)

    try {
      val createReq = new CoreAdminRequest.Create()
      createReq.setCoreName(storename)
      createReq.setInstanceDir(storename)
      createReq.setConfigSet("basic_configs")
      createReq.process(client)

      val lb = new ListBuffer[(String, String)]
      lb += "pk" -> attributes.filter(_.pk).head.name
      lb += "fields" -> attributes.map(attribute => attribute.name + getSuffix(attribute.fieldtype)).mkString(",")

      Success(lb.toMap)
    } catch {
      case e: Exception =>
        Failure(e)
    }
  }

  /**
    * Check if entity exists.
    *
    * @param storename adapted entityname to store feature to
    * @return
    */
  override def exists(storename: String)(implicit ac: AdamContext): Try[Boolean] = {
    log.debug("solr exists operation")

    try {
      Success(CoreAdminRequest.getStatus(storename, new HttpSolrClient(url)).getCoreStatus(storename).get("instanceDir") != null)
    } catch {
      case e: Exception =>
        Failure(e)
    }
  }

  /**
    * Returns the dynamic suffix for storing the fieldtype in solr.
    *
    * @param fieldtype
    * @return
    */
  private def getSuffix(fieldtype: FieldType) = fieldtype match {
    case FieldTypes.AUTOTYPE => "_l"
    case FieldTypes.INTTYPE => "_i"
    case FieldTypes.LONGTYPE => "_l"
    case FieldTypes.FLOATTYPE => "_f"
    case FieldTypes.DOUBLETYPE => "_d"
    case FieldTypes.STRINGTYPE => "_s"
    case FieldTypes.TEXTTYPE => "_txt"
    case FieldTypes.BOOLEANTYPE => "_b"
    case _ => "_s" //in case we do not know how to store the data, choose string
  }


  /**
    * Returns the fieldtype given a solr suffix.
    *
    * @param suffix
    * @return
    */
  private def getFieldType(suffix: String) = suffix match {
    case "_i" => FieldTypes.INTTYPE
    case "_l" => FieldTypes.LONGTYPE
    case "_f" => FieldTypes.FLOATTYPE
    case "_d" => FieldTypes.DOUBLETYPE
    case "_s" => FieldTypes.STRINGTYPE
    case "_txt" => FieldTypes.TEXTTYPE
    case "_b" => FieldTypes.BOOLEANTYPE
    case _ => FieldTypes.STRINGTYPE
  }

  /**
    * Read entity.
    *
    * @param storename adapted entityname to store feature to
    * @param params    reading parameters
    * @return
    */
  override def read(storename: String, params: Map[String, String])(implicit ac: AdamContext): Try[DataFrame] = {
    try {
      val client = new HttpSolrClient(url + "/" + storename)
      val nameDicAttributenameToSolrname = params.get("fields").get.split(",").map(field => field.substring(0, field.indexOf("_")) -> field).toMap
      val nameDicSolrnameToAttributename = nameDicAttributenameToSolrname.map(_.swap)

      //set query for retrieving data
      val solrQuery = new SolrQuery()
      val query = params.get("query").map(adjustAttributeName(_, nameDicAttributenameToSolrname)).getOrElse("*:*")
      solrQuery.setQuery(query)
      if (params.contains("filter")) {
        solrQuery.setFilterQueries(params.get("filter").get.split(",").toSeq: _*)
      }
      solrQuery.setRows(Integer.MAX_VALUE) //retrieve all rows

      val results = client.query(solrQuery).getResults
      val nresults = results.getNumFound.toInt

      val rdd = ac.sc.range(0, nresults).mapPartitions(it => {
        it.filter(i => i < results.getNumFound).map(i => results.get(i.toInt)).map(doc => {
          val data = nameDicSolrnameToAttributename.keys.toSeq.map(solrname => {
            val fieldData = doc.get(solrname)

            if (fieldData != null) {
              fieldData match {
                case list: java.util.ArrayList[_] => if (list.size() > 0) {
                  list.get(0)
                }
                case any => any
              }
            } else {
              null
            }
          }).filter(_ != null).toSeq
          Row(data: _*)
        })
      })

      val df = if (!results.isEmpty) {
        val tmpDoc = results.get(0)
        val schema = nameDicSolrnameToAttributename.keys.toSeq.map(solrname => {
          if (tmpDoc.get(solrname) != null) {
            val name = nameDicSolrnameToAttributename(solrname)
            val fieldtype = getFieldType(solrname.substring(solrname.lastIndexOf("_")))
            StructField(name, fieldtype.datatype)
          } else {
            null
          }
        }).filter(_ != null)
        ac.sqlContext.createDataFrame(rdd, StructType(schema))
      } else {
        ac.sqlContext.emptyDataFrame
      }

      Success(df)
    } catch {
      case e: Exception =>
        log.error("fatal error when reading from solr", e)
        Failure(e)
    }
  }

  /**
    * Adjusts the query, by replacing the names in front of the : with the dynamic name suffix
    *
    * @param originalQuery
    * @param nameDic
    * @return
    */
  private def adjustAttributeName(originalQuery: String, nameDic: Map[String, String]): String = {

    val pattern = "([^:\"']+)|(\"[^\"]*\")|('[^']*')".r
    pattern.findAllIn(originalQuery).zipWithIndex.map { case (fieldname, idx) =>
      if (idx % 2 == 0) {
        val solrname = nameDic.get(fieldname)

        if (solrname.isDefined) {
          solrname.get + ":"
        } else {
          log.error("field " + fieldname + " not stored in solr")
        }

      } else {
        fieldname
      }
    }.mkString
  }


  /**
    * Write entity.
    *
    * @param storename adapted entityname to store feature to
    * @param df        data
    * @param mode      save mode (append, overwrite, ...)
    * @param params    writing parameters
    * @return
    */
  override def write(storename: String, df: DataFrame, mode: SaveMode = SaveMode.Append, params: Map[String, String])(implicit ac: AdamContext): Try[Map[String, String]] = {
    val schema = df.schema

    val nameDicAttributenameToSolrname = params.get("fields").get.split(",").map(field => field.substring(0, field.indexOf("_")) -> field).toMap

    df.foreachPartition { it =>
      val partClient = new HttpSolrClient(url + "/" + storename)

      it.foreach { row =>
        val doc = new SolrInputDocument()
        doc.addField("id", row.getAs[Any](params.get("pk").get))

        schema.foreach {
          attribute =>
            val solrname = nameDicAttributenameToSolrname.get(attribute.name)

            if (solrname.isDefined) {
              doc.addField(solrname.get, row.getAs[Any](attribute.name).toString)
            } else {
              //possibly store as string and update catalog here rather than throwing error
              log.error("solr name not found; field not created?")
            }
        }

        partClient.add(doc)
      }

      partClient.commit()
    }

    Success(Map())
  }

  /**
    * Drop the entity.
    *
    * @param storename adapted entityname to store feature to
    * @return
    */
  override def drop(storename: String)(implicit ac: AdamContext): Try[Void] = {
    val client = new HttpSolrClient(url)

    client.deleteByQuery(storename.toString, "*:*")
    client.commit(storename)

    //deleting core is not easily possible, therefore we just delete the data

    Success(null)
  }
}