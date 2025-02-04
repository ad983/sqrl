/*
 * Copyright (c) 2021, DataSQRL. All rights reserved. Use is subject to license terms.
 */
package com.datasqrl.engine.database.relational;

import com.datasqrl.plan.global.IndexDefinition;
import com.datasqrl.plan.global.IndexSelectorConfig;
import lombok.Builder;
import lombok.Getter;
import lombok.NonNull;
import lombok.Value;

import java.util.EnumSet;

import static com.datasqrl.plan.global.IndexDefinition.Type.BTREE;
import static com.datasqrl.plan.global.IndexDefinition.Type.HASH;

@Value
@Builder
public class IndexSelectorConfigByDialect implements IndexSelectorConfig {

  public static final double DEFAULT_COST_THRESHOLD = 0.95;
  public static final int MAX_INDEX_COLUMNS = 6;

  @Getter
  @Builder.Default
  double costImprovementThreshold = DEFAULT_COST_THRESHOLD;
  @NonNull
  String dialect;
  @Builder.Default
  int maxIndexColumns = MAX_INDEX_COLUMNS;

  @Override
  public int maxIndexColumnSets() {
    return 100;
  }

  @Override
  public EnumSet<IndexDefinition.Type> supportedIndexTypes() {
    switch (dialect.toUpperCase()) {
      case "POSTGRES":
      case "MYSQL":
      case "H2":
      case "SQLITE":
        return EnumSet.of(HASH, BTREE);
      default:
        throw new IllegalStateException(dialect);
    }
  }

  @Override
  public int maxIndexColumns(IndexDefinition.Type indexType) {
    switch (dialect.toUpperCase()) {
      case "POSTGRES":
        switch (indexType) {
          case HASH:
            return 1;
          default:
            return maxIndexColumns;
        }
      case "MYSQL":
      case "H2":
      case "SQLITE":
        return maxIndexColumns;
      default:
        throw new IllegalStateException(dialect);
    }
  }

  @Override
  public double relativeIndexCost(IndexDefinition index) {
    switch (index.getType()) {
      case HASH:
        return 1;
      case BTREE:
        return 1.5 + 0.1 * index.getColumns().size();
      default:
        throw new IllegalStateException(index.getType().name());
    }
  }

  public static IndexSelectorConfigByDialect of(String dialect) {
    return IndexSelectorConfigByDialect.builder().dialect(dialect).build();
  }

}
