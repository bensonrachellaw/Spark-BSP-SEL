package org.apache.spark.mllibCode

import org.apache.spark.ml.classification.{DecisionTreeClassifier, LinearSVC}
import org.apache.spark.ml.evaluation.MulticlassClassificationEvaluator
import org.apache.spark.ml.linalg.SQLDataTypes.VectorType
import org.apache.spark.ml.linalg.Vectors
import org.apache.spark.sql.{DataFrame, Row}
import org.apache.spark.sql.types.{IntegerType, StructField, StructType}
import org.apache.spark.utils.SparkUtils
import org.apache.spark.sql.RspContext._

import scala.collection.mutable
import scala.util.Random

object DecisionTreeSparkMlib {
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

    val DT = new DecisionTreeClassifier()
      .setSeed(Random.nextLong())
      .setLabelCol("label")
      .setFeaturesCol("features")

    val startTime = System.nanoTime
    val DTmodel = DT.fit(TrainingData)

    println(s"==============spend time ${-(startTime - System.nanoTime()) * 0.000000001}")

    val res: DataFrame = DTmodel.transform(TestingData)

    //val res1 = res.select(res.col("label").cast(DoubleType).as("label"),res.col("prediction")) 可以不用转为double。col("prediction")是double类型

    val evaluator = new MulticlassClassificationEvaluator().setLabelCol("label").setPredictionCol("prediction")

    val accuracy = evaluator.setMetricName("accuracy").evaluate(res)
    val recall = evaluator.setMetricName("weightedRecall").evaluate(res)
    val precision = evaluator.setMetricName("weightedPrecision").evaluate(res)
    val f1 = evaluator.setMetricName("f1").evaluate(res)

    println(s"DecisionTreeAccuracy = ${accuracy}")
    println(s"DecisionTreeRecall = ${recall}")
    println(s"DecisionTreePrecision = ${precision}")
    println(s"DecisionTreeF1 = ${f1}")
    spark.stop()
  }
}
