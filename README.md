# Spark-BSP-SEL System
Bootstrap Sample Partition and Selected Ensemble Learning System: Distributed Ensemble Learning Bootstrap Samples Based using Spark as Backend.

本项目为深圳大学 · 大数据研究所 · 基于Spark + Bootstrap的分布式大数据模型（Bootstrap Sample Partition，BSP）以及基于BSP大数据模型的分布式选择性集成学习方法 · 的系统工程开源代码。

## Prerequisites

```
All experiments in this paper are conducted on a Spark distributed cluster. 
The specific cluster configuration is: Spark distributed cluster has 32 host nodes, of which 24 hosts have two 16Cores, 2.6GHZ Intel Xeon E5-2650 CPUs, 
and 128GB of memory, while its 8 hosts have two 12Cores, 2.6GHZ Intel Xeon E5-2630 CPUs, 
128GB of memory , and 128GB of RAM, and each host node has 30TB of hard drive capacity.

# Node OS：

OperatingSystem = CentOS 
OperatingSystem Version = 7.5.1804

# Spark：

SparkVersion = 2.4.0
HadoopVersion = 3.0
JavaVersion = 1.8.181
ScalaVersion = 2.11.12
HDFSReplicationFactor = 3
HDFSBlockSize = 128MB
```

## Quick Start
To set up Spark-BSP-SEL, all nodes of your distributed cluster should be have a right environment.

**Compilation and Packaging:**
```
Since Spark development is very active and java can be compiled on a variety of platforms, Spark is very easy to compile, but requires the following.
1. bash environment
2. JAVA_HOME environment variable

commands:

1 cd \Spark-BSP-SEL\spark-2.4.0 with BSP
2 ./build/mvn clean
3 ./dev/make-distribution.sh --name hadoop-3.2-hive-2.3 --tgz -Phadoop-3.2 -Phive-2.3 -Phive-thriftserver -Pyarn -DskipTests

This two commands can directly accomplish two things, as follows
1. Compile the code to form a dist directory, which will contain the compiled targets for each project
2. package the dist into a TGZ file to form a release version

if you want to just compile only one package, such as core:

add " -pl core "  in the command 3 to reduce the compilation time and avoid compiling the whole spark project multiple times.
```
**Deploying a new version in a cluster:**
```
1 cp /home/xxx/spark-2.4.0/conf/spark-env.sh.template /home/xxx/spark-2.4.0/conf/spark-env.sh

eg.

cp /home/luokaijing/spark-2.4.0-bin-hadoop-3.2-hive-2.3/conf/spark-env.sh.template /home/luokaijing/spark-2.4.0-bin-hadoop-3.2-hive-2.3/conf/spark-env.sh

add into spark-env.sh:

HIVE_CONF_DIR=/etc/hive/conf
HADOOP_CONF_DIR=/etc/hadoop/conf
YARN_CONF_DIR=/etc/hadoop/conf

2 vim ~/.bashrc

export SPARK_HOME=/where/your/spark/path
export HADOOP_CONF_DIR=/etc/hadoop/conf
export HADOOP_HOME=/opt/cloudera/parcels/CDH

eg.

export SPARK_HOME=~/spark-2.4.0-bin-hadoop-3.2-hive-2.3
export HADOOP_CONF_DIR=/etc/hadoop/conf
export HADOOP_HOME=/opt/cloudera/parcels/CDH
export JAVA_HOME=/usr/lib/jvm/java-8-oracle-cloudera/
export PATH=$JAVA_HOME/bin:$PATH
alias sudo = 'sudo env PATH=$PATH'

3 source ~/.bashrc

```
If you don't want to compile the Spark-BSP-SEL source code project , you can download the `release` package directly and deploy.

## BSP-Usage

Packaging method：

get into folder.

`cd ./spark-2.4.0 with BSP/bsp`

use mvn compile : will use 1 thread per available CPU core. (Speed up)

`mvn -T 1C clean package -Dmaven.test.skip=true`

get ./spark-2.4.0 with BSP/bsp/target/spark-bsp_2.11-2.4.0.jar

Start the Spark-BSP-SEL.

`spark-shell --bsp-mode true --master yarn --deploy-mode cluster --name NAME --jars spark-bsp_2.11-2.4.0.jar spark-bsp_2.11-2.4.0.jar`

**code example for bsp generated**

- Import Context Package:
```
scala> import org.apache.spark.sql.BspContext._
import org.apache.spark.sql.BspContext._
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
scala> val rdd4 = rdd2.getSubBspPartitions(5)
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
## SEL-Usage

Please Goto [Page-SEL](https://github.com/benson08230539/Spark-BSP-SEL/blob/main/SEL%20Apps%20using%20Spark-BSP-SEL%20System/README.md).

## Preview

BSP mode is already integrated into Apache-Spark2.4.0 now.
```
Launch effect-1: spark-shell --version
```
![image1](https://github.com/benson08230539/Spark-BSP-SEL/blob/main/images/BSP0.png)
```
Launch effect-2: spark-shell --bsp-mode true
```
`scala> sc.getConf.get("spark.bsp")`

![image2](https://github.com/benson08230539/Spark-BSP-SEL/blob/main/images/BSP1.png)
```
Launch effect-3: pyspark --bsp-mode true
```
`python>>> sc.getConf().get("spark.bsp")`

![image3](https://github.com/benson08230539/Spark-BSP-SEL/blob/main/images/BSP2.png)
```
Launch effect-4: spark-shell --help
```
![image4](https://github.com/benson08230539/Spark-BSP-SEL/blob/main/images/BSP3.png)
