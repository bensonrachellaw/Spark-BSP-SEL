package org.apache.spark.utils
import org.apache.spark.sql.RspContext._
import org.apache.spark.sql.{Row, SparkSession}
/**
 * Description: spark-rsp_2.11
 * Created by L6BD610_Luo on 2023/3/27
 * 把字符串格式的一维数组数据转成 array[int]
 * "[1,2,3]" -> [1,2,3]
 */
object dataTran {
  def main(args: Array[String]): Unit = {
    val spark = SparkUtils.autoSettingEnvAndGetSession(SparkUtils.sparkConf(this.getClass.getSimpleName))
    // 加载数据，并进行类型转换
    val path = "D:\\download\\part-00000"
    val df1 = spark.rspRead.text(path)
    import spark.implicits._
    val a = df1.map(row => row.toString.replaceAll("\"","").replaceAll("\\[","").replaceAll("]","").replaceAll("\"","").split(", ").map(_.toInt))
//    a.show(60)
    a.printSchema()
//    a.write.parquet("D:\\download\\test1.parquet")
  }
}
