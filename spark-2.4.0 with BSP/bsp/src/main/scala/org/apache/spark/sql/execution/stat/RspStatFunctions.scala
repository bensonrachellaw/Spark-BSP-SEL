package org.apache.spark.sql.execution.stat

import java.util.Locale

import org.apache.spark.internal.Logging
import org.apache.spark.sql.catalyst.InternalRow
import org.apache.spark.sql.catalyst.expressions.{AttributeReference, Cast, Expression, GenericInternalRow, GetArrayItem, Literal}
import org.apache.spark.sql.catalyst.expressions.aggregate.{ApproximatePercentile, Average, Count, Max, Min, StddevSamp}
import org.apache.spark.sql.catalyst.plans.logical.LocalRelation
import org.apache.spark.sql.types.{NumericType, StringType}
import org.apache.spark.sql.{Column, DataFrame, Dataset}
import org.apache.spark.unsafe.types.UTF8String

/**
 * Created by longhao on 2020/11/27
 */
object RspStatFunctions  extends Logging{

  def summary(ds: Dataset[_], nums:Int, statistics: Seq[String]): DataFrame = {

    val defaultStatistics = Seq("count", "mean", "stddev", "min", "25%", "50%", "75%", "max")
    val selectedStatistics = if (statistics.nonEmpty) statistics else defaultStatistics

    val percentiles = selectedStatistics.filter(a => a.endsWith("%")).map { p =>
      try {
        p.stripSuffix("%").toDouble / 100.0
      } catch {
        case e: NumberFormatException =>
          throw new IllegalArgumentException(s"Unable to parse $p as a percentile", e)
      }
    }
    require(percentiles.forall(p => p >= 0 && p <= 1), "Percentiles must be in the range [0, 1]")

    var percentileIndex = 0
    val statisticFns = selectedStatistics.map { stats =>
      if (stats.endsWith("%")) {
        val index = percentileIndex
        percentileIndex += 1
        (child: Expression) =>
          GetArrayItem(
            new ApproximatePercentile(child, Literal.create(percentiles)).toAggregateExpression(),
            Literal(index))
      } else {
        stats.toLowerCase(Locale.ROOT) match {
          case "count" => (child: Expression) => Count(child).toAggregateExpression()
          case "mean" => (child: Expression) => Average(child).toAggregateExpression()
          case "stddev" => (child: Expression) => StddevSamp(child).toAggregateExpression()
          case "min" => (child: Expression) => Min(child).toAggregateExpression()
          case "max" => (child: Expression) => Max(child).toAggregateExpression()
          case _ => throw new IllegalArgumentException(s"$stats is not a recognised statistic")
        }
      }
    }

    val selectedCols = ds.logicalPlan.output
      .filter(a => a.dataType.isInstanceOf[NumericType] || a.dataType.isInstanceOf[StringType])

    val aggExprs = statisticFns.flatMap { func =>
      selectedCols.map(c => Column(Cast(func(c), StringType)).as(c.name))
    }

    // If there is no selected columns, we don't need to run this aggregate, so make it a lazy val.
    lazy val aggResult = ds.select(aggExprs: _*).queryExecution.toRdd.collect().head

    // We will have one row for each selected statistic in the result.
    val result = Array.fill[InternalRow](selectedStatistics.length) {
      // each row has the statistic name, and statistic values of each selected column.
      new GenericInternalRow(selectedCols.length + 1)
    }

    var rowIndex = 0
    while (rowIndex < result.length) {
      val statsName = selectedStatistics(rowIndex)
      result(rowIndex).update(0, UTF8String.fromString(statsName))
      for (colIndex <- selectedCols.indices) {
        val statsValue = aggResult.getUTF8String(rowIndex * selectedCols.length + colIndex)
        result(rowIndex).update(colIndex + 1, statsValue)
      }
      rowIndex += 1
    }

    // All columns are string type
    val output = AttributeReference("summary", StringType)() +:
      selectedCols.map(c => AttributeReference(c.name, StringType)())

    Dataset.ofRows(ds.sparkSession, LocalRelation(output, result))
  }

}
