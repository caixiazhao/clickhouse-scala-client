package com.crobox.clickhouse.dsl

import scala.util.Try

object OperationalQuery {

  def apply(query: InternalQuery): OperationalQuery = new OperationalQuery {
    override val internalQuery: InternalQuery = query
  }

}

trait OperationalQuery extends Query {

  def select(columns: Column*): OperationalQuery = {
    val newSelect = Some(SelectQuery(Seq(columns: _*)))
    OperationalQuery(internalQuery.copy(select = newSelect))
  }

  def distinct(columns: Column*): OperationalQuery = {
    val newSelect = Some(SelectQuery(Seq(columns: _*), "DISTINCT"))
    OperationalQuery(internalQuery.copy(select = newSelect))
  }

  def prewhere(condition: TableColumn[Boolean]): OperationalQuery = {
    val comparison = internalQuery.prewhere.map(_.and(condition)).getOrElse(condition)
    OperationalQuery(internalQuery.copy(prewhere = Some(comparison)))
  }

  def where(condition: TableColumn[Boolean]): OperationalQuery = {
    val comparison = internalQuery.where.map(_.and(condition)).getOrElse(condition)
    OperationalQuery(internalQuery.copy(where = Some(comparison)))
  }

  def from[T <: Table](table: T): OperationalQuery = {
    val from = TableFromQuery(table)
    OperationalQuery(internalQuery.copy(from = Some(from)))
  }

  def from(query: OperationalQuery): OperationalQuery = {
    val from = InnerFromQuery(query)
    OperationalQuery(internalQuery.copy(from = Some(from)))
  }

  def asFinal: OperationalQuery = {
    OperationalQuery(internalQuery.copy(asFinal = true))
  }

  def groupBy(columns: Column*): OperationalQuery = {
    val internalGroupBy = internalQuery.groupBy.getOrElse(GroupByQuery())
    val newGroupBy = Some(internalGroupBy.copy(usingColumns = internalGroupBy.usingColumns ++ columns))
    val newSelect = mergeOperationalColumns(columns)
    OperationalQuery(
      internalQuery.copy(select = newSelect, groupBy = newGroupBy)
    )
  }

  def withRollup: OperationalQuery = {
    val newGroupBy = internalQuery.groupBy.getOrElse(GroupByQuery()).copy(mode = Some(GroupByQuery.WithRollup))
    OperationalQuery(
      internalQuery.copy(groupBy = Some(newGroupBy))
    )
  }

  def withCube: OperationalQuery = {
    val newGroupBy = internalQuery.groupBy.getOrElse(GroupByQuery()).copy(mode = Some(GroupByQuery.WithCube))
    OperationalQuery(
      internalQuery.copy(groupBy = Some(newGroupBy))
    )
  }

  def withTotals: OperationalQuery = {
    val newGroupBy = internalQuery.groupBy.getOrElse(GroupByQuery()).copy(withTotals = true)
    OperationalQuery(
      internalQuery.copy(groupBy = Some(newGroupBy))
    )
  }

  def having(condition: TableColumn[Boolean]): OperationalQuery = {
    val comparison = internalQuery.having.map(_.and(condition)).getOrElse(condition)
    OperationalQuery(internalQuery.copy(having = Option(comparison)))
  }

  def orderBy(columns: Column*): OperationalQuery =
    orderByWithDirection(columns.map(c => (c, ASC)): _*)

  def orderByWithDirection(columns: (Column, OrderingDirection)*): OperationalQuery = {
    val newOrderingColumns: Seq[(Column, OrderingDirection)] =
      Seq(columns: _*)
    val newSelect = mergeOperationalColumns(columns.map(_._1))
    OperationalQuery(
      internalQuery.copy(select = newSelect, orderBy = internalQuery.orderBy ++ newOrderingColumns)
    )
  }

  def limit(limit: Option[Limit]): OperationalQuery =
    OperationalQuery(internalQuery.copy(limit = limit))

  def unionAll(otherQuery : OperationalQuery): OperationalQuery = {
    require(internalQuery.select.isDefined && otherQuery.internalQuery.select.isDefined, "Trying to apply UNION ALL on non SELECT queries.")
    require(otherQuery.internalQuery.select.get.columns.size == internalQuery.select.get.columns.size,
      "SELECT queries needs to have the same number of columns to perform UNION ALL."
    )

    OperationalQuery(internalQuery.copy(unionAll = internalQuery.unionAll :+ otherQuery))
  }

  private def mergeOperationalColumns(newOrderingColumns: Seq[Column]): Option[SelectQuery] = {
    val selectForGroup     = internalQuery.select

    val selectForGroupCols = selectForGroup.toSeq.flatMap(_.columns)

    val filteredSelectAll = if (selectForGroupCols.contains(all())) {
      //Only keep aliased, we already select all cols
      newOrderingColumns.collect{ case c:AliasedColumn[_] => c}
    }else{
      newOrderingColumns
    }

    val filteredDuplicates = filteredSelectAll.filterNot(column => {
      selectForGroupCols.exists {
        case c: Column => column.name == c.name
        case _                       => false
      }
    })

    val selectWithOrderColumns = selectForGroupCols ++ filteredDuplicates

    val newSelect = selectForGroup.map(sq => sq.copy(columns = selectWithOrderColumns))
    newSelect
  }

  def join[TargetTable <: Table](`type`: JoinQuery.JoinType, query: OperationalQuery): OperationalQuery = {
    OperationalQuery(internalQuery.copy(join = Some(JoinQuery(`type`, InnerFromQuery(query)))))
  }

  def join[TargetTable <: Table](`type`: JoinQuery.JoinType, table: TargetTable): OperationalQuery = {
    OperationalQuery(internalQuery.copy(join = Some(JoinQuery(`type`, TableFromQuery(table)))))
  }

  def globalJoin[TargetTable <: Table](`type`: JoinQuery.JoinType, query: OperationalQuery): OperationalQuery = {
    OperationalQuery(internalQuery.copy(join = Some(JoinQuery(`type`, InnerFromQuery(query), global = true))))
  }

  def globalJoin[TargetTable <: Table](`type`: JoinQuery.JoinType, table: TargetTable): OperationalQuery = {
    OperationalQuery(internalQuery.copy(join = Some(JoinQuery(`type`, TableFromQuery(table), global = true))))
  }

  @deprecated("Please use join(JoinQuery.AllInnerJoin)", "Clickhouse v20")
  def allInnerJoin(query: OperationalQuery): OperationalQuery = join(JoinQuery.AllInnerJoin, query)
  @deprecated("Please use join(JoinQuery.AllLeftJoin)", "Clickhouse v20")
  def allLeftJoin(query: OperationalQuery): OperationalQuery = join(JoinQuery.AllLeftJoin, query)
  @deprecated("Please use join(JoinQuery.AllRightJoin)", "Clickhouse v20")
  def allRightJoin(query: OperationalQuery): OperationalQuery = join(JoinQuery.AllRightJoin, query)
  @deprecated("Please use join(JoinQuery.AllInnerJoin)", "Clickhouse v20")
  def anyInnerJoin(query: OperationalQuery): OperationalQuery = join(JoinQuery.AnyInnerJoin, query)
  @deprecated("Please use join(JoinQuery.AnyLeftJoin)", "Clickhouse v20")
  def anyLeftJoin(query: OperationalQuery): OperationalQuery = join(JoinQuery.AnyLeftJoin, query)
  @deprecated("Please use join(JoinQuery.AllRightJoin)", "Clickhouse v20")
  def anyRightJoin(query: OperationalQuery): OperationalQuery = join(JoinQuery.AnyRightJoin, query)

  @deprecated("Please use globalJoin(JoinQuery.AllInnerJoin)")
  def globalAllInnerJoin(query: OperationalQuery): OperationalQuery = globalJoin(JoinQuery.AllInnerJoin, query)
  @deprecated("Please use globalJoin(JoinQuery.AllLeftJoin)")
  def globalAllLeftJoin(query: OperationalQuery): OperationalQuery = globalJoin(JoinQuery.AllLeftJoin, query)
  @deprecated("Please use globalJoin(JoinQuery.AllRightJoin)")
  def globalAllRightJoin(query: OperationalQuery): OperationalQuery = globalJoin(JoinQuery.AllRightJoin, query)
  @deprecated("Please use globalJoin(JoinQuery.AllInnerJoin)")
  def globalAnyInnerJoin(query: OperationalQuery): OperationalQuery = globalJoin(JoinQuery.AnyInnerJoin, query)
  @deprecated("Please use globalJoin(JoinQuery.AnyLeftJoin)")
  def globalAnyLeftJoin(query: OperationalQuery): OperationalQuery = globalJoin(JoinQuery.AnyLeftJoin, query)
  @deprecated("Please use globalJoin(JoinQuery.AllRightJoin)")
  def globalAnyRightJoin(query: OperationalQuery): OperationalQuery = globalJoin(JoinQuery.AnyRightJoin, query)

  def using(
    column: Column,
    columns: Column*
  ): OperationalQuery = {
    require(internalQuery.join.isDefined)

    val newUsing = (columns :+ column).distinct

    val newJoin = this.internalQuery.join.get.copy(usingColumns = newUsing)

    OperationalQuery(internalQuery.copy(join = Some(newJoin)))
  }

  /**
    * Merge with another OperationalQuery, any conflict on query parts between the 2 joins will be resolved by
    * preferring the left querypart over the right one.
    *
    * @param other The right part to merge with this OperationalQuery
    * @return A merge of this and other OperationalQuery
    */
  def :+>(other: OperationalQuery): OperationalQuery =
    OperationalQuery(this.internalQuery :+> other.internalQuery)

  /**
    * Right associative version of the merge (:+>) operator.
    *
    * @param other The left part to merge with this OperationalQuery
    * @return A merge of this and other OperationalQuery
    */

  def <+:(other: OperationalQuery): OperationalQuery =
    OperationalQuery(this.internalQuery :+> other.internalQuery)

  /**
    * Tries to merge this OperationalQuery with other
    *
    * @param other The Query parts to merge against
    * @return A Success on merge without conflict, or Failure of IllegalArgumentException otherwise.
    */
  def +(other: OperationalQuery): Try[OperationalQuery] =
    (this.internalQuery + other.internalQuery).map(OperationalQuery.apply)

  def +(other: Try[OperationalQuery]): Try[OperationalQuery] =
    other
      .flatMap(o => this.internalQuery + o.internalQuery)
      .map(OperationalQuery.apply)
}
