package org.apache.spark.bsp

import org.apache.spark.{Partition, TaskContext}
import org.apache.spark.rdd.RDD

import scala.reflect.ClassTag
/**
 * Created by luokaijing on 2021/10/15 21:31
 */
class BspRDD[T:ClassTag](prev:RDD[T]) extends RDD[T](prev){
  override def compute(split: Partition, context: TaskContext): Iterator[T] = {
    prev.compute(split, context)
  }


  override protected def getPartitions: Array[Partition] = prev.partitions
}
