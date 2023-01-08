package org.apache.spark.sql


import org.apache.spark.annotation.InterfaceStability
import org.apache.spark.sql.execution.datasources.LogicalRelation
import org.apache.spark.sql.internal.{BaseSessionStateBuilder, BspSessionStateBuilder, SessionState}
import org.apache.spark.sql.sources.BaseRelation
import org.apache.spark.util.Utils
import scala.util.control.NonFatal


/**
 * Created by longhao on 2020/11/27
 */
class BspSparkSession(sparkSession: SparkSession) extends SparkSession(sparkSession.sparkContext) {
  self =>

  /**
   * Helper method to create an instance of `SessionState` based on `className` from conf.
   * The result is either `SessionState` or a Hive based `SessionState`.
   */
  private def instantiateSessionState(className: String, sparkSession: SparkSession): SessionState = {
    try {
      val clazz = Utils.classForName(className)
      val ctor = clazz.getConstructors.head
      ctor.newInstance(sparkSession, None).asInstanceOf[BaseSessionStateBuilder].build()
    } catch {
      case NonFatal(e) =>
        throw new IllegalArgumentException(s"Error while instantiating '$className':", e)
    }
  }

  /**
   *
   */
  @InterfaceStability.Unstable
  @transient
  override lazy val sessionState: SessionState = {
        val state = instantiateSessionState(classOf[BspSessionStateBuilder].getCanonicalName,self)
        initialSessionOptions.foreach { case (k, v) => state.conf.setConfString(k, v) }
        state
  }

  override def baseRelationToDataFrame(baseRelation: BaseRelation): DataFrame = {
    Dataset.ofRows(self, LogicalRelation(baseRelation))
  }
}
