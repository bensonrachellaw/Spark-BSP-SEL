package org.apache.spark.utils

import org.apache.commons.lang3.{StringUtils, SystemUtils}
import org.apache.spark.SparkConf
import org.apache.spark.sql.SparkSession

/**
 * Created by zym;
 */

object SparkUtils {

  def sparkConf(appName:String) = {
    val sparkConf = new SparkConf()
      .setAppName(appName)
    sparkConf
  }

  def autoSettingEnvAndGetSession(sparkConf: SparkConf) = {
    if(SystemUtils.IS_OS_WINDOWS || SystemUtils.IS_OS_MAC){
      SparkSession.builder().config(sparkConf)
        .master("local[*]")  //设置本地模式
        .getOrCreate()
    }else{
      SparkSession.builder().config(sparkConf)
        .master("yarn")
        .config("spark.dynamicAllocation.enabled","true")
        .config("spark.dynamicAllocation.maxExecutors", "150")
        .config("spark.executor.cores","4")
        .config("spark.executor.memory","16g")
        .config("spark.driver.memory","16g")
        .config("spark.locality.wait","15")
        .config("spark.yarn.executor.memoryOverhead", String.valueOf((32*1024*0.07).toInt))
        .config("spark.serializer", "org.apache.spark.serializer.KryoSerializer")
        .config("spark.kryoserializer.buffer.max", "512m")
        .getOrCreate() //创建会话变量
    }
  }
}
