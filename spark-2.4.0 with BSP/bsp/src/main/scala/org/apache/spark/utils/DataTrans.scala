package org.apache.spark.utils

import org.apache.spark.sql.functions.column
import org.apache.spark.ml.feature.{LabeledPoint, VectorAssembler}
import org.apache.spark.ml.linalg.{Vector, Vectors}
import org.apache.spark.ml.stat.Summarizer
import org.apache.spark.sql.Row
import org.apache.spark.sql.types._

/**
 * Description: spark-rsp_2.11
 * Created by L6BD610_Luo on 2022/12/6
 * an example
 */
object DataTrans {
  def main(args: Array[String]): Unit = {
    val spark = SparkUtils.autoSettingEnvAndGetSession(SparkUtils.sparkConf(this.getClass.getSimpleName))
    // 加载数据，并进行类型转换

    val dataFrame = spark
      .read.format("csv")
      .option("header", "true")
      .load("D:\\课程资料\\WeChat Files\\wxid_vhq0r8oj7xm111\\FileStorage\\File\\2022-12\\winequality-red.csv")
      .toDF("fixed acidity","volatile acidity","citric acid","residual sugar","chlorides","free sulfur dioxide","total sulfur dioxide","density","pH","sulphates","alcohol","label")
      .withColumn("fixed acidity",column("fixed acidity").cast("Double"))
      .withColumn("volatile acidity",column("volatile acidity").cast("Double"))
      .withColumn("citric acid",column("citric acid").cast("Double"))
      .withColumn("residual sugar",column("residual sugar").cast("Double"))
      .withColumn("chlorides",column("chlorides").cast("Double"))
      .withColumn("free sulfur dioxide",column("free sulfur dioxide").cast("Double"))
      .withColumn("total sulfur dioxide",column("total sulfur dioxide").cast("Double"))
      .withColumn("density",column("density").cast("Double"))
      .withColumn("pH",column("pH").cast("Double"))
      .withColumn("sulphates",column("sulphates").cast("Double"))
      .withColumn("alcohol",column("alcohol").cast("Double"))
      .withColumn("label",column("label").cast("Double"))


    // 封装dataFrame成(feature,label)形式
    val dataFrameModify = new VectorAssembler()
      .setInputCols(Array("fixed acidity","volatile acidity","citric acid","residual sugar","chlorides","free sulfur dioxide","total sulfur dioxide","density","pH","sulphates","alcohol"))
      .setOutputCol("feature")
      .transform(dataFrame)
      .select("feature", "label")

    dataFrameModify.printSchema()
    val x = StructType(Seq(
      StructField("features", ArrayType(DoubleType,true),true),
      StructField("label", DoubleType,true)
    ))
    val BspDF_RDD1 = dataFrameModify.rdd.map(row => Row(row(0).asInstanceOf[Vector].toArray, row(1)))

    val BspDF_input = spark.createDataFrame(BspDF_RDD1, x)
    BspDF_input.printSchema()
    BspDF_input.head(1)
    println(BspDF_input.head(1).mkString("Array(", ", ", ")"))
    BspDF_input.write.parquet("D:\\论文工作_每周报告\\数据\\wine.parquet")
    spark.stop()
  }
}
