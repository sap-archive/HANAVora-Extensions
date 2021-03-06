package org.apache.spark.sql.execution.datasources

import org.apache.spark.sql.catalyst.TableIdentifier
import org.apache.spark.sql.catalyst.TableIdentifierUtils._
import org.apache.spark.sql.catalyst.expressions.Attribute
import org.apache.spark.sql.catalyst.plans.logical.LogicalPlan
import org.apache.spark.sql.execution.RunnableCommand
import org.apache.spark.sql.sources.{DatasourceCatalog, RelationInfo}
import org.apache.spark.sql.types.{StringType, StructField, StructType}
import org.apache.spark.sql.{DatasourceResolver, Row, SQLContext}

/**
  * The execution of ''DESCRIBE TABLES ... USING '' in the data source.
  *
  * Example of the resulting relation:
  * =====================================
  * |TABLE_NAME|DDL_STMT                |
  * =====================================
  * |Table1    |CREATE TABLE Table1 ... |
  * -------------------------------------
  * |View1     |CREATE VIEW View1 AS ...|
  * -------------------------------------
  *
  * if the data source's catalog does not have the relation then an
  * empty table will be returned.
  *
  * @param name The (qualified) name of the relation.
  * @param provider The data source class identifier.
  * @param options The options map.
  */
private[sql]
case class DescribeTableUsingCommand(
    name: TableIdentifier,
    provider: String,
    options: Map[String, String])
  extends LogicalPlan
  with RunnableCommand {

  override def output: Seq[Attribute] = StructType(
    StructField("TABLE_NAME", StringType, nullable = false) ::
    StructField("DDL_STMT", StringType, nullable = false) ::
    Nil
  ).toAttributes

  override def run(sqlContext: SQLContext): Seq[Row] = {
    // Convert the table name according to the case-sensitivity settings
    val tableId = name.toSeq
    val resolver = DatasourceResolver.resolverFor(sqlContext)
    val catalog = resolver.newInstanceOfTyped[DatasourceCatalog](provider)

    Seq(catalog
      .getRelation(sqlContext, tableId, new CaseInsensitiveMap(options)) match {
        case None => Row("", "")
        case Some(RelationInfo(relName, _, _, ddl, _)) => Row(
          relName, ddl.getOrElse(""))
    })
  }
}
