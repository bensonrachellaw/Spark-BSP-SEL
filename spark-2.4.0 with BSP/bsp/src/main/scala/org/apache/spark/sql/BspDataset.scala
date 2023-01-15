package org.apache.spark.sql

import org.apache.spark.bsp.BspRDD
import org.apache.spark.sql.catalyst.plans.logical.CatalystSerde
import org.apache.spark.sql.execution.QueryExecution

import scala.reflect.ClassTag
/**
 * Created by luokaijing on 2021/10/22 16:24
 */
class BspDataset [T: ClassTag](prev: Dataset[T])
  extends Dataset[T](prev.sparkSession,prev.queryExecution,prev.exprEnc){

  @transient private lazy val rddQueryExecution: QueryExecution = {
    val deserialized = CatalystSerde.deserialize[T](logicalPlan)
    sparkSession.sessionState.executePlan(deserialized)
  }

  override lazy val rdd: BspRDD[T] = {
    val objectType = exprEnc.deserializer.dataType
    new BspRDD(rddQueryExecution.toRdd.mapPartitions { rows =>
      rows.map(_.get(0, objectType).asInstanceOf[T])
    })
  }
}
