package org.apache.spark.sql.internal

import org.apache.spark.annotation.{Experimental, InterfaceStability}
import org.apache.spark.sql.execution.datasources.{DataSourceStrategy, BspFileSourceStrategy}
import org.apache.spark.sql.execution.datasources.v2.DataSourceV2Strategy
import org.apache.spark.sql.{SparkSession, Strategy}
import org.apache.spark.sql.execution.SparkPlanner

/**
 * Created by longhao on 2020/11/27
 */
@Experimental
@InterfaceStability.Unstable
class BspSessionStateBuilder(
      session: SparkSession,
      parentState: Option[SessionState] = None)
  extends BaseSessionStateBuilder(session, parentState) {
  override protected def newBuilder: NewBuilder = new BspSessionStateBuilder(_, _)

  override protected def planner: SparkPlanner = {
    new SparkPlanner(session.sparkContext, conf, experimentalMethods) {
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

      override def extraPlanningStrategies: Seq[Strategy] =
        super.extraPlanningStrategies ++ customPlanningStrategies
    }
  }
}