package org.apache.spark.bsp

import org.apache.spark.rdd.RDD

import scala.reflect.ClassTag

/**
 * Created by longhao on 2020/11/19
 */
class BspHadoopRDD[T: ClassTag](prev: RDD[T]) extends BspRDD[T](prev) {

}
