package org.apache.spark.bsp
import org.apache.spark.rdd.RDD
import org.apache.spark.{Partition, TaskContext}

import scala.reflect.ClassTag
/**
 * Created by luokaijing on 2021/10/19 14:42
 */
private[spark] class SonBspRDD[T: ClassTag](prev: RDD[T], partitions: Array[Partition]) extends BspRDD[T](prev) {
  override def compute(split: Partition, context: TaskContext): Iterator[T] = {
    prev.compute(split, context)
  }
  override protected def getPartitions: Array[Partition] = partitions
}
