package org.apache.spark.sql.execution

import org.apache.spark.sql.Strategy
import org.apache.spark.sql.execution.datasources.{DataSourceStrategy, BspFileSourceStrategy}
import org.apache.spark.sql.execution.datasources.v2.DataSourceV2Strategy

/**
 * Created by longhao on 2020/11/27
 */
class BspSparkPlanner(prev: SparkPlanner)
  extends SparkPlanner(prev.sparkContext,prev.conf,prev.experimentalMethods){
  override def strategies: Seq[Strategy] =
    experimentalMethods.extraStrategies ++
      extraPlanningStrategies ++ (
      PythonEvals ::
        DataSourceV2Strategy ::
        BspFileSourceStrategy ::
        DataSourceStrategy(conf) ::
        SpecialLimits ::
        Aggregation ::
        Window ::
        JoinSelection ::
        InMemoryScans ::
        BasicOperators :: Nil)
}
