package org.vitrivr.adampro.utils.exception

/**
  * adampro
  *
  * Ivan Giangreco
  * April 2016
  */
case class QueryNotConformException(message : String = "Query does not correspond to entity.")  extends GeneralAdamException(message)
