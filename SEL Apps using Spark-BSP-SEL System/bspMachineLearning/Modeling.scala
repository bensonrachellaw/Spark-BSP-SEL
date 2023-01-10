package org.apache.spark.bspMachineLearning
import org.apache.spark.rdd.RDD

import scala.collection.mutable.Map

object Modeling {

  def vote1(param: (Long, (Iterable[(Long, Array[Int])], Int))) = {
    val labels: Array[Array[Int]] = param._2._1.map(_._2).toArray // 获得预测结果
    // 每一块对应每个模型预测结果 label : Array[Array(Int)]
    val result = new Array[Int](param._2._2) //数据大小
    val numOfModel = labels.length
    val map = Map[Int, Int]() // 可变map集合 类似java map
    for (i <- 0 until param._2._2) {
      for (m <- 0 until numOfModel) {
        // 第m个预测器预测第i个标签的结果
        map.put(labels(m)(i), map.getOrElse(labels(m)(i), 0) + 1)
      }
      // 统计结果 eg. [1:2,0:4]
      result(i) = map.maxBy(_._2)._1 // 取出最大的标签 赋予第i条数据结果
      map.clear()
    }
    val partId: Long = param._1
    (partId, result)
  }

  def vote_regression(param: (Long, (Iterable[(Long, Array[Double])], Int))) = {
    val labels: Array[Array[Double]] = param._2._1.map(_._2).toArray // 获得预测结果
    // 每一块对应每个模型预测结果 label : Array[Array(Int)]
    val result = new Array[Double](param._2._2) //数据大小
    val numOfModel = labels.length
    for (i <- 0 until param._2._2) {
      var iSum: Double = 0.0
      for (m <- 0 until numOfModel) {
        iSum += labels(m)(i)
      }
      result(i) = iSum / numOfModel
    }
    val partId: Long = param._1
    (partId, result)
  }

  //质量选择：

  //Bootstrap 百分位法
  def selectPartitions_percent(train: RDD[(Array[Array[Double]], Array[Int])], rowIndex:Int) = {
    val avgAndSum: RDD[(Double, Double)] = train.map(
      f => {
        var sum = 0.0
        f._1.foreach(feature => sum = sum + feature(rowIndex))
        (sum / f._2.length, sum)
      }
    )
    val trainWithSumAndAvg: RDD[((Array[Array[Double]], Array[Int]), (Double, Double))] = train.zip(avgAndSum)
    val avgAndSumArr: Array[(Double, Double)] = avgAndSum.collect()
    var sum = 0.0
    import scala.collection.mutable.ListBuffer
    val list = new ListBuffer[Double]

    for(i <- avgAndSumArr.indices){
      sum += avgAndSumArr(i)._2 // sum
      list += avgAndSumArr(i)._1 // avg
    }
    val sortedList: List[Double] = list.toList.sorted
    val length: Int = sortedList.length
    //    val avg = (sum / train.count())
    //    println(s"所有数据第${}特征的平均值为${}",rowIndex,avg)
    val bottom = (length * 0.0).toInt
    val top = (length - length * 0.0 - 1).toInt
    (trainWithSumAndAvg.filter(item => item._2._1 > sortedList(bottom) && item._2._1 < sortedList(top))
      .map(item => item._1), sum)
  }
  //Bootstrap 经验法
  def selectPartitions_em(train: RDD[(Array[Array[Double]], Array[Int])], rowIndex:Int, avgOfSelectedFeature:Double = 1.1795422512419822E-4) = {
    val avgAndSum: RDD[(Double, Double)] = train.map(
      f => {
        var sum = 0.0
        f._1.foreach(feature => sum = sum + feature(rowIndex))
        (Math.abs(sum / f._2.length - avgOfSelectedFeature), sum)
      }
    )
    val trainWithSumAndAvg: RDD[((Array[Array[Double]], Array[Int]), (Double, Double))] = train.zip(avgAndSum)
    val avgAndSumArr: Array[(Double, Double)] = avgAndSum.collect()
    var sum = 0.0
    import scala.collection.mutable.ListBuffer
    val list = new ListBuffer[Double]

    for(i <- avgAndSumArr.indices){
      sum += avgAndSumArr(i)._2 // sum
      list += avgAndSumArr(i)._1 // avgPart - avgAll
    }
    val sortedList: List[Double] = list.toList.sorted
    val length: Int = sortedList.length
    //只去大不去小
    val bottom = (length * 0.0).toInt
    val top = (length - length * 0.15).toInt
    (trainWithSumAndAvg.filter(item => item._2._1 > sortedList(bottom) && item._2._1 < sortedList(top))
      .map(item => item._1), sum)
  }

  // 分类：参数类型不同
  def selectPartitions_classification(train: RDD[(Array[Array[Double]], Array[Int])], rowIndex:Int, avgOfSelectedFeature:Double) = {
    val avgAndSum: RDD[(Double, Double)] = train.map(
      f => {
        var sum = 0.0
        f._1.foreach(feature => sum = sum + feature(rowIndex))
        (Math.abs(sum / f._2.length - avgOfSelectedFeature), sum)
      }
    )
    val trainWithSumAndAvg: RDD[((Array[Array[Double]], Array[Int]), (Double, Double))] = train.zip(avgAndSum)
    val avgAndSumArr: Array[(Double, Double)] = avgAndSum.collect()
    var sum = 0.0
    import scala.collection.mutable.ListBuffer
    val list = new ListBuffer[Double]

    for(i <- avgAndSumArr.indices){
      sum += avgAndSumArr(i)._2 // sum
      list += avgAndSumArr(i)._1 // avgPart - avgAll
    }
    val sortedList: List[Double] = list.toList.sorted
    val length: Int = sortedList.length
    //    val avg = (sum / train.count())
    //    println(s"所有数据第${}特征的平均值为${}",rowIndex,avg)
    val bottom = (length * 0.15).toInt
    val top = (length - length * 0.15).toInt
    (trainWithSumAndAvg.filter(item => item._2._1 > sortedList(bottom) && item._2._1 < sortedList(top))
      .map(item => item._1), sum)
  }

  // 回归：参数类型不同
  def selectPartitions_regression(train: RDD[(Array[Array[Double]], Array[Double])], rowIndex:Int, avgOfSelectedFeature:Double) = {
    val avgAndSum: RDD[(Double, Double)] = train.map(
      f => {
        var sum = 0.0
        f._1.foreach(feature => sum = sum + feature(rowIndex))
        (Math.abs(sum / f._2.length - avgOfSelectedFeature), sum)
      }
    )
    val trainWithSumAndAvg: RDD[((Array[Array[Double]], Array[Double]), (Double, Double))] = train.zip(avgAndSum)
    val avgAndSumArr: Array[(Double, Double)] = avgAndSum.collect()
    var sum = 0.0
    import scala.collection.mutable.ListBuffer
    val list = new ListBuffer[Double]

    for(i <- avgAndSumArr.indices){
      sum += avgAndSumArr(i)._2 // sum
      list += avgAndSumArr(i)._1 // avgPart - avgAll
    }
    val sortedList: List[Double] = list.toList.sorted
    val length: Int = sortedList.length
    //    val avg = (sum / train.count())
    //    println(s"所有数据第${}特征的平均值为${}",rowIndex,avg)
    val bottom = (length * 0.0).toInt
    val top = (length - 1).toInt
    (trainWithSumAndAvg.filter(item => item._2._1 > sortedList(bottom) && item._2._1 < sortedList(top))
      .map(item => item._1), sum)
  }

}
