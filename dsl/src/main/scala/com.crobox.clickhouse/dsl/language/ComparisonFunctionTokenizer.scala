package com.crobox.clickhouse.dsl.language

import com.crobox.clickhouse.dsl._

trait ComparisonFunctionTokenizer { this: ClickhouseTokenizerModule =>

  def tokenizeComparisonColumn(col: ComparisonColumn)(implicit ctx: TokenizeContext): String =
    tokenizeColumn(col.left.column) + " " + col.operator + " " + tokenizeColumn(col.right.column)
}
