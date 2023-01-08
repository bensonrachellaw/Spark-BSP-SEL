## BSP-Usage

Packaging method：

get into folder

`cd ./spark-2.4.0 with BSP/bsp`

use mvn compile

`mvn -T 1C  clean package -Dmaven.test.skip=true`

get ./spark-2.4.0 with BSP/bsp/target/spark-bsp_2.11-2.4.0.jar

Start the Spark-BSP-SEL

`spark-shell --bsp-mode true --master yarn --deploy-mode cluster --name NAME --jars spark-bsp_2.11-2.4.0.jar spark-bsp_2.11-2.4.0.jar`

**code example for bsp generated**

- Import Context Package:
```
scala> import org.apache.spark.sql.BspContext._
import org.apache.spark.sql.RspContext._
```
- Use bspTextFile to read in data (there are 10 files in total, and the size of each file is about 292MB). You can see that it is a BspRDD type.
```
scala> val rdd2=spark.sparkContext.bspTextFile("/user/luokaijing/Bootstrap_RSP_blocks_in_jars/call_data.csv")
rdd2 = BspRDD[2] at RDD at BspRDD.scala:10
```
- Get the number of blocks
```
scala> rdd2.getNumPartitions
10
```
- Quality selection step, providing functions for removing some blocks
```
scala> val rdd4 = rdd2.getSubPartitions(5)
rdd4 = SonBspRDD[3] at RDD at BspRDD.scala:10
```
- Read data in native mode
```
val path = "/user/luokaijing/results.csv"
val data = spark.sparkContext.textFile(path,4)
```
- Convert 15 BSP blocks, Non Broadcast method for producing BSPs
```
val rdd_BSP_toBSP1 = data.toBSPCore(15)
```
- Get the block number of rdd_BSP_toBSP1
```
scala> rdd_BSP_toBSP1.getNumPartitions
15
```
- In dataframe format, bspRead supports most format imports
```
scala> spark.bspRead.   #后面按table键盘
csv   format   jdbc   json   load   option   options   orc   parquet   schema   table   text   textFile
```
- Make sure PROCESS_LOCAL.
```
trainRDD.cache()
trainRDD.count()
testRDD.cache()
testRDD.count()
```