package org.apache.spark.utils

import org.apache.spark.rdd.RDD
import org.apache.spark.sql.Row

import scala.collection.mutable

/**
 * Created by zym;
 */

object DataUtils {

  def trainTestSplit(bsp_rdd:RDD[Row])  = {
    val splitBspRDD = bsp_rdd.randomSplit(Array(0.8, 0.2))

    val trainFeature = splitBspRDD(0).map(row=>row(0).asInstanceOf[mutable.WrappedArray[Double]].toArray)
    val trainLabel = splitBspRDD(0).map(row=>row(1).asInstanceOf[Int].intValue)
    val testFeature = splitBspRDD(1).map(row=>row(0).asInstanceOf[mutable.WrappedArray[Double]].toArray)
    val testLabel = splitBspRDD(1).map(row=>row(1).asInstanceOf[Int].intValue)

    val train = trainFeature.glom().zip(trainLabel.glom())
    val test = testFeature.glom().zip(testLabel.glom())

    (train, test)
  }
  def trainTestHandleFormat(splitBspRDD:RDD[Row])  = {

    val trainFeature = splitBspRDD.map(row=>row(0).asInstanceOf[mutable.WrappedArray[Double]].toArray)
    val trainLabel = splitBspRDD.map(row=>row(1).asInstanceOf[Int].intValue)

    val train = trainFeature.glom().zip(trainLabel.glom())

    train
  }

  def trainTestHandleFormat_SVM(splitBspRDD:RDD[Row])  = {

    val trainFeature = splitBspRDD.map(row=>row(0).asInstanceOf[mutable.WrappedArray[Double]].toArray)
    val trainLabel = splitBspRDD.map(row=>row(1).asInstanceOf[Int].intValue).map(l => {if(l==0) -1 else l})

    val train = trainFeature.glom().zip(trainLabel.glom())

    train
  }

  def trainTestHandleFormat_Double(splitBspRDD:RDD[Row])  = {

    val trainFeature = splitBspRDD.map(row=>row(0).asInstanceOf[mutable.WrappedArray[Double]].toArray)
    val trainLabel = splitBspRDD.map(row=>row(1).asInstanceOf[Double].doubleValue)

    val train = trainFeature.glom().zip(trainLabel.glom())

    train
  }
  def trainTestSplit_regression(bsp_rdd:RDD[Row])  = {
    val splitBspRDD = bsp_rdd.randomSplit(Array(0.8, 0.2))
    val trainFeature = splitBspRDD(0).map(row=>row(0).asInstanceOf[mutable.WrappedArray[Double]].toArray)
    val trainLabel = splitBspRDD(0).map(row=>row(1).asInstanceOf[Double].doubleValue())
    val testFeature = splitBspRDD(1).map(row=>row(0).asInstanceOf[mutable.WrappedArray[Double]].toArray)
    val testLabel = splitBspRDD(1).map(row=>row(1).asInstanceOf[Double].doubleValue())

    val train = trainFeature.glom().zip(trainLabel.glom())
    val test = testFeature.glom().zip(testLabel.glom())

    (train, test)
  }

}
