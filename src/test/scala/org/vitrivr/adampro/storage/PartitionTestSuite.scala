package org.vitrivr.adampro.storage

import org.vitrivr.adampro.AdamTestBase
import org.vitrivr.adampro.api.{IndexOp, QueryOp}
import org.vitrivr.adampro.config.FieldNames
import org.vitrivr.adampro.helpers.partition.PartitionMode
import org.vitrivr.adampro.index.structures.IndexTypes
import org.vitrivr.adampro.index.{Index, IndexLRUCache}
import org.vitrivr.adampro.query.distance.EuclideanDistance
import org.vitrivr.adampro.query.query.NearestNeighbourQuery

/**
  * adampro
  *
  * Ivan Giangreco
  * April 2016
  */
class PartitionTestSuite extends AdamTestBase {
  val nPartitions = 8


  feature("repartitioning index") {
    /**
      *
      */
    scenario("repartition index replacing existing") {
      withQueryEvaluationSet { es =>
        val index = IndexOp(es.entity.entityname, "featurefield", IndexTypes.ECPINDEX, EuclideanDistance)
        assert(index.isSuccess)

        When("performing a repartitioning with replacement")
        IndexOp.partition(index.get.indexname, nPartitions, None, Some(Seq("tid")), PartitionMode.REPLACE_EXISTING)

        val partnnq = NearestNeighbourQuery("featurefield", es.feature.vector, None, es.distance, es.k, false, Map[String, String](), Some(Set(0)))

        val partresults = QueryOp.index(index.get.indexname, partnnq, None).get.get
          .map(r => (r.getAs[Float](FieldNames.distanceColumnName), r.getAs[Long]("tid"))).collect()
          .sortBy(_._1).toSeq

        Then("we should retrieve the k nearest neighbors of one partition only")
        assert(partresults.map(x => x._2 % nPartitions).distinct.length == 1)
      }
    }


    /**
      *
      */
    scenario("repartition index creating temporary new") {
      withQueryEvaluationSet { es =>
        val index = IndexOp(es.entity.entityname, "featurefield", IndexTypes.ECPINDEX, EuclideanDistance)
        assert(index.isSuccess)

        When("performing a repartitioning with replacement")
        val partindex = IndexOp.partition(index.get.indexname, nPartitions, None, Some(Seq("tid")), PartitionMode.CREATE_TEMP)
        val partnnq = NearestNeighbourQuery("featurefield", es.feature.vector, None, es.distance, es.k, false, Map[String, String](), Some(Set(0)))

        val partresults = QueryOp.index(partindex.get.indexname, partnnq, None).get.get
          .map(r => (r.getAs[Float](FieldNames.distanceColumnName), r.getAs[Long]("tid"))).collect()
          .sortBy(_._1).toSeq

        Then("we should retrieve the k nearest neighbors of one partition only")
        assert(partresults.map(x => x._2 % nPartitions).distinct.length == 1)

        When("clearing the cache")
        IndexLRUCache.empty()

        Then("the index should be gone")
        val loadedIndex = Index.load(partindex.get.indexname)
        assert(loadedIndex.isFailure)
      }
    }

    /**
      *
      */
    scenario("repartition index creating new") {
      withQueryEvaluationSet { es =>
        val index = IndexOp(es.entity.entityname, "featurefield", IndexTypes.ECPINDEX, EuclideanDistance)
        assert(index.isSuccess)

        When("performing a repartitioning, creating new")
        val partindex = IndexOp.partition(index.get.indexname, nPartitions, None, Some(Seq("tid")), PartitionMode.CREATE_NEW)
        val partnnq = NearestNeighbourQuery("featurefield", es.feature.vector, None, es.distance, es.k, false, Map[String, String](), Some(Set(0)))

        IndexLRUCache.empty()

        Then("we should be able to load the index")
        val loadedIndex = Index.load(partindex.get.indexname)
        assert(loadedIndex.isSuccess)

        val partresults = QueryOp.index(partindex.get.indexname, partnnq, None).get.get
          .map(r => (r.getAs[Float](FieldNames.distanceColumnName), r.getAs[Long]("tid"))).collect()
          .sortBy(_._1).toSeq

        And("we should retrieve the k nearest neighbors of one partition only")
        assert(partresults.map(x => x._2 % nPartitions).distinct.length == 1)
      }
    }

    /**
      *
      */
    scenario("repartition index based on metadata") {
      withQueryEvaluationSet { es =>
        val index = IndexOp(es.entity.entityname, "featurefield", IndexTypes.ECPINDEX, EuclideanDistance)
        assert(index.isSuccess)

        When("performing a repartitioning, creating new")
        val partindex = IndexOp.partition(index.get.indexname, nPartitions, None, Some(Seq("tid")), PartitionMode.CREATE_NEW)
        val partnnq = NearestNeighbourQuery("featurefield", es.feature.vector, None, es.distance, es.k, false, Map[String, String](), Some(Set(0)))

        IndexLRUCache.empty()

        Then("we should be able to load the index")
        val loadedIndex = Index.load(partindex.get.indexname)
        assert(loadedIndex.isSuccess)

        val partresults = QueryOp.index(partindex.get.indexname, partnnq, None).get.get
          .map(r => (r.getAs[Float](FieldNames.distanceColumnName), r.getAs[Long]("tid"))).collect() //get here TID of metadata
          .sortBy(_._1).toSeq

        And("we should retrieve the k nearest neighbors of one partition only")
        assert(partresults.map(x => x._2 % nPartitions).distinct.length == 1)
      }
    }
  }
}