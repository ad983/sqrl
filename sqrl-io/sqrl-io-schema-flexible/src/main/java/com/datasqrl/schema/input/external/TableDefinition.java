/*
 * Copyright (c) 2021, DataSQRL. All rights reserved. Use is subject to license terms.
 */
package com.datasqrl.schema.input.external;

import java.util.List;

public class TableDefinition extends AbstractElementDefinition {

  public TableDefinition() {

  }

  public TableDefinition(String name, String description, Object default_value,
      String schema_version,
      Boolean partial_schema, List<FieldDefinition> columns, List<String> tests) {
    super(name, description, default_value);
    this.schema_version = schema_version;
    this.partial_schema = partial_schema;
    this.columns = columns;
    this.tests = tests;
  }

  public static boolean PARTIAL_SCHEMA_DEFAULT = true;

  public String schema_version;

  public Boolean partial_schema;

  public List<FieldDefinition> columns;
  public List<String> tests;

}
