/*
 * Copyright (c) 2021, DataSQRL. All rights reserved. Use is subject to license terms.
 */
package com.datasqrl.engine.database.relational.ddl.statements;

import com.datasqrl.engine.database.relational.ddl.SqlDDLStatement;
import lombok.AllArgsConstructor;

@AllArgsConstructor
public class DropIndexDDL implements SqlDDLStatement {

  String indexName;
  String tableName;

  @Override
  public String toSql() {
    return "DROP INDEX IF EXISTS " + indexName + ";";
  }
}
