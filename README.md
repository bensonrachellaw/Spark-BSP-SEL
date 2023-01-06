# Spark-BSP-SEL
Bootstrap Sample Partition and Selected Ensemble Learning System: Distributed Ensemble Learning Bootstrap Samples Based.

本项目为基于Spark + Bootstrap的分布式数据模型（Bootstrap Sample Partition，BSP）以及基于BSP数据模型的分布式集成学习方法的系统工程开源代码。

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
## Preview

BSP mode is already integrated into Apache-Spark2.4.0 now.

Launch effect-1: spark-shell --version

![image1](https://github.com/benson08230539/Spark-BSP-SEL/blob/main/images/BSP0.png)

Launch effect-2: spark-shell --bsp-mode true

![image2](https://github.com/benson08230539/Spark-BSP-SEL/blob/main/images/BSP1.png)

Launch effect-3: pyspark --bsp-mode true

![image3](https://github.com/benson08230539/Spark-BSP-SEL/blob/main/images/BSP2.png)

Launch effect-3: spark-shell --help

![image4](https://github.com/benson08230539/Spark-BSP-SEL/blob/main/images/BSP3.png)
