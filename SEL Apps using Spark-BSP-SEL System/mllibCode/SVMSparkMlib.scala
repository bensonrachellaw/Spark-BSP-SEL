package org.apache.spark.mllibCode

import org.apache.spark.ml.classification.LinearSVC
import org.apache.spark.ml.evaluation.MulticlassClassificationEvaluator
import org.apache.spark.ml.linalg.SQLDataTypes.VectorType
import org.apache.spark.ml.linalg.Vectors
import org.apache.spark.sql.BspContext._
import org.apache.spark.sql.types._
import org.apache.spark.sql.{DataFrame, Row}
import org.apache.spark.utils.SparkUtils

import scala.collection.mutable

//spark-submit --jars spark-nlp.jar --class Main app.jar
//spark-submit --class org.apache.spark.Bspml.BspBinaryClassificationSVM --master yarn --deploy-mode cluster --name BspBinaryClassificationSVM --jars spark-bsp_2.11-2.4.0.jar spark-bsp_2.11-2.4.0.jar
//spark-submit --class org.apache.spark.Bspml.BspBinaryClassificationSVM --master yarn --deploy-mode cluster --jars spark-rsp_2.11-2.4.0.jar spark-rsp_2.11-2.4.0.jar
//spark-submit --class org.apache.spark.Bspml.BspBinaryClassificationSVM --master yarn --deploy-mode client --jars spark-rsp_2.11-2.4.0.jar spark-rsp_2.11-2.4.0.jar


//使用client部署模式的话 driver在提交任务的节点（本机）上启动，返回值也会返回到本机，所以能显示出最后的返回值，如果使用cluster就不会，因为cluster模式是在集群中任意选择一个worker来启动driver。
//本地编译打包命令：mvn -T 1C clean package -Dmaven.test.skip=true
//C:\Program Files\Git\bin\bash.exe 、"cmd.exe" /k "bash.exe"

/**
 * mvn clean package依次执行了clean、resources、compile、testResources、testCompile、test、jar(打包)等７个阶段。
 * mvn clean install依次执行了clean、resources、compile、testResources、testCompile、test、jar(打包)、install等8个阶段。
 * mvn clean deploy依次执行了clean、resources、compile、testResources、testCompile、test、jar(打包)、install、deploy等９个阶段。
 * 
 * 由上面的分析可知主要区别如下：

 * package命令完成了项目编译、单元测试、打包功能，但没有把打好的可执行jar包（war包或其它形式的包）布署到本地maven仓库和远程maven私服仓库
 * install命令完成了项目编译、单元测试、打包功能，同时把打好的可执行jar包（war包或其它形式的包）布署到本地maven仓库，但没有布署到远程maven私服仓库
 * deploy命令完成了项目编译、单元测试、打包功能，同时把打好的可执行jar包（war包或其它形式的包）布署到本地maven仓库和远程maven私服仓库
 * 
 * maven的package和install的区别：

 * 1、package就是打成可以运行的jar/war包；

 * 2、install包含package，并且把jar包放入本地maven仓库中。
 */


//svmAccuracy = 0.9186520196818858

/**
 * Created by luokaijing on 2021/10/28 14:47
 */


object SVMSparkMlib {
  def main(args: Array[String]): Unit = {
    val spark = SparkUtils.autoSettingEnvAndGetSession(SparkUtils.sparkConf(this.getClass.getSimpleName))

    val bspFilepath_clu_train = "/user/luokaijing/Original.parquet"
    val bspFilepath_clu_test = "/user/luokaijing/Original.parquet/part-00017-40f078f3-ca6c-4fec-ae47-4702ca752459-c000.snappy.parquet"

    val dataSchema = StructType(Seq(
      StructField("label", IntegerType),
      StructField("features", VectorType, true)
    ))

    val BspDFTrain = spark.bspRead.parquet(bspFilepath_clu_train)
    val BspDFTest = spark.bspRead.parquet(bspFilepath_clu_test)


    val BspDF_RDD_Train = BspDFTrain.rdd.getSubBspPartitions(9).map(row => Row(row(1).asInstanceOf[Int].intValue, Vectors.dense(row(0).asInstanceOf[mutable.WrappedArray[Double]].toArray)))
    val BspDF_RDD_Test = BspDFTest.rdd.map(row => Row(row(1).asInstanceOf[Int].intValue, Vectors.dense(row(0).asInstanceOf[mutable.WrappedArray[Double]].toArray)))

    val TrainingData: DataFrame = spark.createDataFrame(BspDF_RDD_Train, dataSchema)
    val TestingData: DataFrame = spark.createDataFrame(BspDF_RDD_Test, dataSchema)
    TrainingData.cache()

    val lsvc = new LinearSVC().setMaxIter(10).setRegParam(0.3)

    val startTime = System.nanoTime
    val lsvcmodel = lsvc.fit(TrainingData)
    println(s"spend time ${-(startTime - System.nanoTime()) * 0.000000001}")

    val res: DataFrame = lsvcmodel.transform(TestingData)

    //val res1 = res.select(res.col("label").cast(DoubleType).as("label"),res.col("prediction")) 可以不用转为double。col("prediction")是double类型

    val evaluator = new MulticlassClassificationEvaluator().setLabelCol("label").setPredictionCol("prediction")

    val accuracy = evaluator.setMetricName("accuracy").evaluate(res)
    val recall = evaluator.setMetricName("weightedRecall").evaluate(res)
    val precision = evaluator.setMetricName("weightedPrecision").evaluate(res)
    val f1 = evaluator.setMetricName("f1").evaluate(res)

    println(s"svmAccuracy = ${accuracy}")
    println(s"svmRecall = ${recall}")
    println(s"svmPrecision = ${precision}")
    println(s"svmF1 = ${f1}")
    spark.stop()
  }
}
