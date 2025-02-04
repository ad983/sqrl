/*
 * Copyright (c) 2021, DataSQRL. All rights reserved. Use is subject to license terms.
 */
package com.datasqrl.engine;

import java.util.EnumSet;

public enum EngineCapability {

  //Engine supports denormalized (or nested) data
  DENORMALIZE,
  //Engine supports temporal joins of any sort
  TEMPORAL_JOIN,
  //Engine supports temporal joins against arbitrary state tables (not just deduplicated streams)
  TEMPORAL_JOIN_ON_STATE,
  //Engine supports converting state tables into change streams
  TO_STREAM,
  //Engine supports stream window aggregation
  STREAM_WINDOW_AGGREGATION,
  //Engine supports queries and filters based on query time
  NOW,
  //Engine supports global sort
  GLOBAL_SORT,
  //Engine supports window functions with multiple ranks
  MULTI_RANK,
  //Engine supports the extended SQRL function catalog
  EXTENDED_FUNCTIONS,
  //Engine supports user defined functions
  CUSTOM_FUNCTIONS,
  //Engine can export data to TableSink
  EXPORT,
  //Whether pulling up now-filters and topN leads to better performance
  PULLUP_OPTIMIZATION,
  //Writing/upserting data into engine by primary key will deduplicate data
  MATERIALIZE_ON_KEY,
  //Engine supports data monitoring
  DATA_MONITORING;

  public static EnumSet<EngineCapability> STANDARD_STREAM = EnumSet.of(DENORMALIZE,
      TEMPORAL_JOIN, TO_STREAM, STREAM_WINDOW_AGGREGATION, EXTENDED_FUNCTIONS, CUSTOM_FUNCTIONS,
      EXPORT, PULLUP_OPTIMIZATION, DATA_MONITORING);

  public static EnumSet<EngineCapability> STANDARD_DATABASE = EnumSet.of(NOW, GLOBAL_SORT, MATERIALIZE_ON_KEY,
      MULTI_RANK);

  public static EnumSet<EngineCapability> NO_CAPABILITIES = EnumSet.noneOf(EngineCapability.class);
}
