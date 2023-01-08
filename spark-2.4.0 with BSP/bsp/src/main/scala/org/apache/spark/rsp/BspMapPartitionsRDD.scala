package org.apache.spark.rsp

import org.apache.spark.{Partition, SPARK_BRANCH, SparkEnv, TaskContext}
import org.apache.spark.rdd.{DeterministicLevel, RDD}
import org.apache.spark.storage.{RDDBlockId, StorageLevel}

import scala.reflect.ClassTag

/**
 * Created by longhao on 2020/11/19
 */
class BspMapPartitionsRDD[U: ClassTag, T: ClassTag](
     var prev: RDD[T],
     f: (TaskContext, Int, Iterator[String]) => Iterator[U],  // (TaskContext, partition index, iterator)
     preservesPartitioning: Boolean = false,
     isFromBarrier: Boolean = false,
     isOrderSensitive: Boolean = false)
  extends RDD[U](prev)  {

  override val partitioner = if (preservesPartitioning) firstParent[T].partitioner else None

  override def getPartitions: Array[Partition] = firstParent[T].partitions

  override def compute(split: Partition, context: TaskContext): Iterator[U] =
    f(context, split.index, rspIterator(split, context))

  /**
   * 返回的是Partition对应blockID物理存储位置的迭代器
   * @param split
   * @param context
   * @return
   */
  private def rspIterator(split: Partition, context: TaskContext): Iterator[String] = {
    val blockId = RDDBlockId(id, split.index)
    val file=SparkEnv.get.blockManager.diskBlockManager.getFile(blockId)


    Iterator[String](file.getPath)
  }
  override def clearDependencies() {
    super.clearDependencies()
    prev = null
  }

  @transient protected lazy override val isBarrier_ : Boolean =
    isFromBarrier || dependencies.exists(_.rdd.isBarrier())

  override protected def getOutputDeterministicLevel = {
    if (isOrderSensitive && prev.outputDeterministicLevel == DeterministicLevel.UNORDERED) {
      DeterministicLevel.INDETERMINATE
    } else {
      super.getOutputDeterministicLevel
    }
  }

}
