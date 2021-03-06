package org.vitrivr.adampro.storage.engine

import java.sql.Connection
import java.util.Properties

import com.mchange.v2.c3p0.ComboPooledDataSource
import org.vitrivr.adampro.data.datatypes.AttributeTypes
import org.vitrivr.adampro.data.datatypes.AttributeTypes.AttributeType
import org.vitrivr.adampro.data.entity.AttributeDefinition
import org.vitrivr.adampro.query.query.Predicate
import org.apache.spark.sql.types.{StructField, StructType}
import org.apache.spark.sql.{DataFrame, Row, SaveMode}
import org.vitrivr.adampro.process.SharedComponentContext

import scala.util.{Failure, Success, Try}

/**
  * ADAMpro
  *
  * Ivan Giangreco
  * June 2016
  */
class PostgresqlEngine(private val url: String, private val user: String, private val password: String, private val schema: String = "public")(@transient override implicit val ac: SharedComponentContext) extends Engine()(ac) with Serializable {
  //TODO: check if changing schema breaks tests!

  override val name = "postgresql"

  override def supports: Seq[AttributeType] = Seq(AttributeTypes.AUTOTYPE, AttributeTypes.INTTYPE, AttributeTypes.LONGTYPE, AttributeTypes.FLOATTYPE, AttributeTypes.DOUBLETYPE, AttributeTypes.STRINGTYPE, AttributeTypes.BOOLEANTYPE)

  override def specializes: Seq[AttributeType] = Seq(AttributeTypes.INTTYPE, AttributeTypes.LONGTYPE, AttributeTypes.FLOATTYPE, AttributeTypes.DOUBLETYPE, AttributeTypes.STRINGTYPE, AttributeTypes.BOOLEANTYPE)

  override val repartitionable = false

  /**
    *
    * @param props
    */
  def this(props: Map[String, String])(implicit ac: SharedComponentContext) {
    this(props.get("url").get, props.get("user").get, props.get("password").get, props.getOrElse("schema", "public"))(ac)
  }

  val props = {
    val props = new Properties()
    props.put("url", url)
    props.put("user", user)
    props.put("password", password)
    props.put("driver", "org.postgresql.Driver")
    props.put("currentSchema", schema)
    props
  }

  val propsMap = props.keySet().toArray.map(key => key.toString -> props.get(key).toString).toMap

  @transient private val ds = new ComboPooledDataSource()
  ds.setDriverClass("org.postgresql.Driver")
  ds.setJdbcUrl(url)
  ds.setProperties(props)

  init()

  /**
    * Opens a connection to the PostgreSQL database.
    *
    * @return
    */
  protected def openConnection(): Connection = {
    ds.getConnection
  }

  protected def init() {
    val connection = openConnection()

    //TODO: execute these commands on all partitions?
    try {
      val createSchemaStmt = s"""CREATE SCHEMA IF NOT EXISTS $schema;""".stripMargin

      connection.createStatement().executeUpdate(createSchemaStmt)
    } catch {
      case e: Exception =>
        log.error("fatal error when setting up relational engine", e)
    } finally {
      connection.close()
    }
  }

  /**
    * Create the entity.
    *
    * @param storename  adapted entityname to store feature to
    * @param attributes attributes of the entity (w.r.t. handler)
    * @param params     creation parameters
    * @return options to store
    */
  override def create(storename: String, attributes: Seq[AttributeDefinition], params: Map[String, String])(implicit ac: SharedComponentContext): Try[Map[String, String]] = {
    log.trace("postgresql create operation")
    val connection = openConnection()

    try {
      val structFields = attributes.map {
        attribute => StructField(attribute.name, attribute.attributeType.datatype)
      }

      val df = ac.sqlContext.createDataFrame(ac.sc.emptyRDD[Row], StructType(structFields))

      val tableStmt = write(storename, df, attributes, SaveMode.ErrorIfExists, params)

      if (tableStmt.isFailure) {
        return Failure(tableStmt.failed.get)
      }

      //make attributes unique
      //TODO: execute these commands on all partitions?
      val uniqueStmt = attributes.filter(_.params.getOrElse("unique", "false").toBoolean).map {
        attribute =>
          val attributename = attribute.name
          s"""ALTER TABLE $storename ADD UNIQUE ($attributename)""".stripMargin
      }.mkString("; ")

      //add index to table
      val indexedStmt = (attributes.filter(_.params.getOrElse("indexed", "false").toBoolean) ++ attributes.filter(_.pk)).distinct.map {
        attribute =>
          val attributename = attribute.name
          s"""CREATE INDEX ON $storename ($attributename)""".stripMargin
      }.mkString("; ")

      //add primary key
      val pkattribute = attributes.filter(_.pk)
      assert(pkattribute.size <= 1)
      val pkStmt = pkattribute.map {
        case attribute =>
          val attributename = attribute.name
          s"""ALTER TABLE $storename ADD PRIMARY KEY ($attributename)""".stripMargin
      }.mkString("; ")

      connection.createStatement().executeUpdate(uniqueStmt + "; " + indexedStmt + "; " + pkStmt)

      Success(Map())
    } catch {
      case e: Exception =>
        Failure(e)
    } finally {
      connection.close()
    }
  }


  /**
    * Check if entity exists.
    *
    * @param storename adapted entityname to store feature to
    * @return
    */
  override def exists(storename: String)(implicit ac: SharedComponentContext): Try[Boolean] = {
    log.trace("postgresql exists operation")
    val connection = openConnection()

    try {
      //TODO: execute these commands on all partitions?
      val existsSql = s"""SELECT EXISTS ( SELECT 1 FROM pg_catalog.pg_class c JOIN pg_catalog.pg_namespace n ON n.oid = c.relnamespace WHERE n.nspname = 'public' AND c.relname = '$storename')""".stripMargin
      val results = connection.createStatement().executeQuery(existsSql)
      results.next()
      Success(results.getBoolean(1))
    } catch {
      case e: Exception =>
        Failure(e)
    } finally {
      connection.close()
    }
  }

  /**
    * Read entity.
    *
    * @param storename  adapted entityname to store feature to
    * @param attributes the attributes to read
    * @param predicates filtering predicates (only applied if possible)
    * @param params     reading parameters
    * @return
    */
  override def read(storename: String, attributes: Seq[AttributeDefinition], predicates: Seq[Predicate], params: Map[String, String])(implicit ac: SharedComponentContext): Try[DataFrame] = {
    log.trace("postgresql read operation")

    try {
      //TODO: possibly adjust in here for partitioning
      var df = if (predicates.nonEmpty) {
        //TODO: remove equality in predicates?
        ac.sqlContext.read.jdbc(url, storename, predicates.map(_.sqlString).toArray, props)
      } else {
        ac.sqlContext.read.jdbc(url, storename, props)
      }

      attributes.foreach { attribute =>
        df = df.withColumn(attribute.name + "-cast", df.col(attribute.name).cast(attribute.attributeType.datatype)).drop(attribute.name).withColumnRenamed(attribute.name + "-cast", attribute.name)
      }

      Success(df)
    } catch {
      case e: Exception =>
        Failure(e)
    }
  }

  /**
    * Write entity.
    *
    * @param storename  adapted entityname to store feature to
    * @param df         data
    * @param attributes attributes to store
    * @param mode       save mode (append, overwrite, ...)
    * @param params     writing parameters
    * @return new options to store
    */
  override def write(storename: String, df: DataFrame, attributes: Seq[AttributeDefinition], mode: SaveMode = SaveMode.Append, params: Map[String, String])(implicit ac: SharedComponentContext): Try[Map[String, String]] = {
    log.trace("postgresql write operation")

    try {
      df.write.mode(mode).jdbc(url, storename, props)
      Success(Map())
    } catch {
      case e: Exception =>
        Failure(e)
    }
  }

  /**
    * Drop the entity.
    *
    * @param storename adapted entityname to store feature to
    * @return
    */
  override def drop(storename: String)(implicit ac: SharedComponentContext): Try[Void] = {
    log.trace("postgresql drop operation")
    val connection = openConnection()

    try {
      val dropTableSql = s"""DROP TABLE $storename""".stripMargin
      connection.createStatement().executeUpdate(dropTableSql)
      Success(null)
    } catch {
      case e: Exception =>
        log.error("error in dropping table in postgresql", e)
        Failure(e)
    } finally {
      connection.close()
    }
  }

  override def equals(other: Any): Boolean =
    other match {
      case that: PostgresqlEngine => that.url.equals(url) && that.user.equals(user) && that.password.equals(password)
      case _ => false
    }

  override def hashCode(): Int = {
    val prime = 31
    var result = 1
    result = prime * result + url.hashCode
    result = prime * result + user.hashCode
    result = prime * result + password.hashCode
    result
  }
}