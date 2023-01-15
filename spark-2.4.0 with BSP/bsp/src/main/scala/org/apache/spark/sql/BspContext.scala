package org.apache.spark.sql

import cn.edu.szu.bigdata.BspInputFormat
import org.apache.spark.api.java.JavaRDD
import org.apache.hadoop.io.{LongWritable, Text}
import org.apache.spark.rdd.RDD
import org.apache.spark.bsp.{BspRDD, SonBspRDD}
import org.apache.spark.{Partition, Partitioner, SparkContext}
import scala.collection.mutable.ArrayBuffer
import scala.reflect.ClassTag
import scala.util.Random

/**
 * Created by longhao ; Updated BSP by LKJ on 2021/10/1
 */

class DefaultPartitioner(partitions: Int) extends Partitioner(){

  var numParitions = partitions

  override def getPartition(key: Any): Int ={
    return key.toString().toInt
  }

  override def numPartitions: Int = {
    return numParitions
  }

}

/**
 * key code: BspContext
 */

object BspContext {

  implicit val bsp_real:Boolean = false //所有的隐式值和隐式方法必须放到object中
  implicit val bsp_arrCount:Int = 0

  implicit class SparkContextFunc(context: SparkContext) extends Serializable {

    /**
     * 读取Bsp数据，该读取方法假定路径下每个文件是一个BSP数据块，有多少个HDFS文件就会有多少个Partition.
     * 需要自定义BspInputFormat
     */
    def bspTextFile(
                     path: String,
                     minPartitions: Int = context.defaultMinPartitions): BspRDD[String] = context.withScope {
      context.assertNotStopped()
      val rdd=context.hadoopFile(path, classOf[BspInputFormat], classOf[LongWritable], classOf[Text],
        minPartitions).map(pair => pair._2.toString).setName(path)
      new BspRDD[String](rdd)
    }
  }

  implicit class SparkSessionFunc(sparkSession: SparkSession) extends Serializable {

    private val bspSparkSession=new BspSparkSession(sparkSession)

    /**
     * 用法类似read，比如 spark.bspRead.csv("/xxx/xxx") , bspRead.parquet("/xxx/xxx") 等
     * @return
     */
    def bspRead: BspDataFrameReader = new BspDataFrameReader(bspSparkSession)
  }

  implicit class BspRDDFunc[T:ClassTag](rdd:BspRDD[T]) extends Serializable{
    /**
     * 取下任意数量的BSP块,无重复的块,使用SonBspRDD，私有类。
     */
    def getSubBspPartitions(nums: Int): BspRDD[T] = {
      var newPart: Array[Partition] = new Array[Partition](nums)
      if (rdd.partitions.length >= nums) {
        for (i <- 0 until nums) {
          newPart(i) = rdd.partitions(i)
        }
      }else{
        newPart=Array()
      }
      new SonBspRDD(rdd, newPart)
    }
  }

  implicit class NewRDDFunc[T: ClassTag](rdd: RDD[T]) extends Serializable {

    /**
     *  解决数据复制次数呈递减趋势的问题，但是仍然有时间瓶颈，大于6位数的循环就会变得非常慢。时间久是这里的for的问题，并不是toBSP函数中的for的问题
     *  @param num 块数；arrCount，原样本容量的数目
     */
    def randomCopyCount(num:Int,arrCount:Int): Array[Int]={
      var sumRes = arrCount*num + 1
      var arr : Array[Int] = Array()
      val threshold = num * 2.5
      for (i <- 0 until arrCount){
        var nums = Random.nextInt(sumRes)
        while(nums>=threshold){
          nums = nums/threshold.toInt
        }
        arr = arr:+(nums)
        sumRes = sumRes - nums
        if(i==arrCount-1 && sumRes != 1){
          val tmp = Random.nextInt(arrCount)
          arr(tmp) = arr(tmp)+sumRes-1
        }
      }
      arr
    }
    /**
     * 解决repartiton后的每个分区中条数是否绝对一致的问题，此方法可保证一致，但是时间复杂度可能非常高，因为涉及遍历总容量（原样本容量乘以总块数）
     * 对块数乘以原样本容量的数目，比如最后要生成十个块，就要0-9十个块号分别乘以原样本容量的数目，再对数组进行随机打乱，
     * 这样后面直接对每条数据进行块号赋值，这样即保证随机打乱 也保证repartiton后的每个分区中条数绝对一致
     * Fisher and Yates 提出的时间复杂度O(N)，辅助空间1，的随机打乱算法
     * @param num
     * @return
     */
    def samePartitionItem(num:Int,arrCount:Int):Array[Int]={
      var a = Array[Int]()
      for (i <- 0 until num){
        val x = new Array[Int](arrCount)
        val y = x.map(_ + i)
        a = Array.concat(a,y)
      }
      for (index <- a.indices.reverse) {
        val RandomIndex: Int = Random.nextInt(index+1)
        val tmp = a(index)
        a(index) = a(RandomIndex)
        a(RandomIndex) = tmp
      }
      a
    }
    /**
     * bootstrap core function on each partition
     * @param iter
     * @return Iterator
     * 有重复采样
     */
    def bsp_core(iter:Iterator[T]):Iterator[T]={
      var arr:ArrayBuffer[T] = ArrayBuffer[T]()
      var re = iter.toArray
      var count:Int=re.length
      for (i <- 1 until count+1) {
        val rr = Random.nextInt(count)
        arr += re(rr)
      }
      arr.toIterator
    }

    /**
     * 把rdd转换为包含指定bsp数据块的 RspRDD，复制多次再打乱的方法。使用real前需要在外面count一下。且因为随机数的产生的问题导致效果不好。
     * toBSP:version1。有一点逻辑bug。(近似计算的思想，出来的BSP块不是真正的Bootstrap块，因为数量不完完全全等于原样本数量，有一点点偏差，但是可以代表Bootstrap块，因为跟无放回是对立的
     * @param nums 结果块数 也就是复制次数。repartitionAndSortWithinPartitions该算子可以一边进行重分区的shuffle操作，一边进行排序，KEY相同的情况下按照其他键进行排序
     * @return
     */
    def toBSP(nums:Int)(implicit real:Boolean,arrCount:Int):BspRDD[T]={
      val bsp_partitioner = new DefaultPartitioner(nums)
      if(real){
        val arrCounts = randomCopyCount(nums,arrCount)
        new BspRDD(rdd.zipWithIndex().flatMap(row => {for ( i<- 0 until arrCounts(row._2.toInt) ) yield (Random.nextInt(nums), row._1)}).repartitionAndSortWithinPartitions(bsp_partitioner).map(row => row._2))
      }
      else {
        new BspRDD(rdd.flatMap(row => {for ( i<- 0 until nums ) yield (Random.nextInt(nums), row)}).repartitionAndSortWithinPartitions(bsp_partitioner).map(row => row._2))
      }
    }
    /**
     * toBSP_test函数，带key的输出。
     * @param nums 结果块数 也就是复制次数。
     * @return
     */
    def toBSP_test(nums:Int):RDD[(Int,T)]={
      val bsp_partitioner = new DefaultPartitioner(nums)
      rdd.flatMap(row => {for ( i<- 0 until nums ) yield (Random.nextInt(nums), row)}).repartition(nums)
    }
    /**
     * use samePartitionItem
     * @param nums 结果块数 也就是复制次数。
     * @return
     */
    def toBSPSameItem(nums:Int)(implicit real:Boolean,arrCount:Int):BspRDD[T]={
      val bsp_partitioner = new DefaultPartitioner(nums)
      val itemCounts = samePartitionItem(nums,arrCount)
      if(real){
        val arrCounts = randomCopyCount(nums,arrCount)
        val rDD = new BspRDD(rdd.zipWithIndex().flatMap(row => {for ( i<- 0 until arrCounts(row._2.toInt) ) yield (Random.nextInt(nums), row._1)}).repartitionAndSortWithinPartitions(bsp_partitioner).map(row => row._2))
        new BspRDD(rDD.zipWithIndex().map(row => (itemCounts(row._2.toInt),row._1)).repartitionAndSortWithinPartitions(bsp_partitioner).map(row => row._2))
      }
      else {
        val rDD = new BspRDD(rdd.flatMap(row => {for ( i<- 0 until nums ) yield (Random.nextInt(nums), row)}).repartitionAndSortWithinPartitions(bsp_partitioner).map(row => row._2))
        new BspRDD(rDD.zipWithIndex().map(row => (itemCounts(row._2.toInt),row._1)).repartitionAndSortWithinPartitions(bsp_partitioner).map(row => row._2))
      }
    }

    /**
     * Non Broadcast method for producing BSPs
     * @param nums
     * @return
     */

    def toBSPCore(nums:Int):BspRDD[T]={
      val bsp_1 = rdd.coalesce(1,shuffle = true)
      var bsp_total = bsp_1
      for (i <- 1 until nums){
        bsp_total = bsp_total.union(bsp_1)
      }
      new BspRDD(bsp_total.mapPartitions(bsp_core))
    }
    /**
     * 2022-12-07
     * Non Broadcast method for producing BSPs
     * @param nums
     * @return
     */

    def toBSPCoreFit(nums:Int) : BspRDD[T] = {
      val bsp_1 = rdd.coalesce(1,shuffle = true)
      var bsp_total1 = bsp_1
      var bsp_total2 = bsp_1
      var bsp_total3 = bsp_1
      var bsp_total4 = bsp_1
      var bsp_total5 = bsp_1
      var bsp_total6 = bsp_1
      var bsp_total7 = bsp_1
      var bsp_total8 = bsp_1
      var bsp_total9 = bsp_1
      var bsp_total10 = bsp_1
      for (i <- 1 until 100){
        bsp_total1 = bsp_total1.union(bsp_1)
      }
      for (i <- 1 until 100){
        bsp_total2 = bsp_total2.union(bsp_1)
      }
      for (i <- 1 until 100){
        bsp_total3 = bsp_total3.union(bsp_1)
      }
      for (i <- 1 until 100){
        bsp_total4 = bsp_total4.union(bsp_1)
      }
//      for (i <- 1 until 100){
//        bsp_total5 = bsp_total5.union(bsp_1)
//      }
//      for (i <- 1 until 100){
//        bsp_total6 = bsp_total6.union(bsp_1)
//      }
//      for (i <- 1 until 100){
//        bsp_total7 = bsp_total7.union(bsp_1)
//      }
//      for (i <- 1 until 100){
//        bsp_total8 = bsp_total8.union(bsp_1)
//      }
//      for (i <- 1 until 100){
//        bsp_total9 = bsp_total9.union(bsp_1)
//      }
      for (i <- 1 until nums - 400){
        bsp_total10 = bsp_total10.union(bsp_1)
      }
      val bsp_total = bsp_total1.union(bsp_total2).union(bsp_total3).union(bsp_total4).union(bsp_total10)
      new BspRDD(bsp_total.mapPartitions(bsp_core))
    }
  }

  implicit class NewJavaRDDFunc[T: ClassTag](javaRDD: JavaRDD[T]) extends Serializable{

    def getSubPartitions(nums: Int): JavaRDD[T] = {
      JavaRDD.fromRDD(
        javaRDD.rdd.asInstanceOf[BspRDD[T]].getSubBspPartitions(nums)
      )
    }
  }

}
