package org.apache.spark.bsp

import org.apache.spark.rdd.RDD
import org.apache.spark.{Partition, TaskContext}

import scala.reflect.ClassTag

/**
 * Created by longhao on 2020/11/18
 */
private[spark] class SonRDD[T: ClassTag](prev: RDD[T], partitions: Array[Partition]) extends BspRDD[T](prev) {
  /**
   * :: DeveloperApi ::
   * Implemented by subclasses to compute a given partition.
   */
  override def compute(split: Partition, context: TaskContext): Iterator[T] = {
    prev.compute(split, context)
  }

  /**
   * Implemented by subclasses to return the set of partitions in this RDD. This method will only
   * be called once, so it is safe to implement a time-consuming computation in it.
   *
   * The partitions in this array must satisfy the following property:
   * `rdd.partitions.zipWithIndex.forall { case (partition, index) => partition.index == index }`
   */
  override protected def getPartitions: Array[Partition] = partitions
}