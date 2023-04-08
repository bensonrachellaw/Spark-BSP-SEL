package org.apache.spark.utils
import org.apache.spark.sql.RspContext._
/**
 * Description: spark-rsp_2.11
 * Created by L6BD610_Luo on 2023/4/8
 *
 */
object PartoCSV {
  def main(args: Array[String]): Unit = {
    val spark = SparkUtils.autoSettingEnvAndGetSession(SparkUtils.sparkConf(this.getClass.getSimpleName))
    val path = "***"
    val df = spark.rspRead.parquet(path)
    val df1 = df.select(df.col("items").cast("String"),df.col("support"),df.col("frequency"))
    df1.repartition(1).write.csv("/user/luokaijing/FimExperiments/fpgSpark_275W_0.01.csv")
  }
}
