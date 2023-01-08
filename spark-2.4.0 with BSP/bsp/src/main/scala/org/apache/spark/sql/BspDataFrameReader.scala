package org.apache.spark.sql

/**
 * Created by luokaijing on 2021/10/22 16:30
 */
class BspDataFrameReader(bspSparkSession: BspSparkSession) extends DataFrameReader(bspSparkSession){

  override def csv(path: String): BspDataset[Row]= {
    csv(Seq(path): _*)
  }

  override def csv(paths: String*): BspDataset[Row] = {
    new BspDataset[Row](format("csv").load(paths : _*))
  }

  override def csv(csvDataset: Dataset[String]): BspDataset[Row] = {
    new BspDataset[Row](super.csv(csvDataset))
  }

  override def json(paths: String*): BspDataset[Row] = {
    new BspDataset[Row](format("json").load(paths : _*))
  }

  override def json(path: String): BspDataset[Row] = {
    json(Seq(path): _*)
  }

  override def json(jsonDataset: Dataset[String]): BspDataset[Row] = {
    new BspDataset[Row](super.json(jsonDataset))
  }

  override def parquet(path: String): BspDataset[Row] = {
    parquet(Seq(path): _*)
  }

  override def parquet(paths: String*): BspDataset[Row] = {
    new BspDataset[Row](format("parquet").load(paths: _*))
  }

  override def orc(path: String): BspDataset[Row] = {
    orc(Seq(path): _*)
  }

  override def orc(paths: String*): BspDataset[Row] = {
    new BspDataset[Row](format("orc").load(paths : _*))
  }

  override def text(path: String): BspDataset[Row] = {
    text(Seq(path): _*)
  }

  override def text(paths: String*): BspDataset[Row] = {
    new BspDataset[Row](format("text").load(paths : _*))
  }
}
