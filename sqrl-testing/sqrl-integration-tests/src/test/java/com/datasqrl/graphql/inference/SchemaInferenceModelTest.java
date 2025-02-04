/*
 * Copyright (c) 2021, DataSQRL. All rights reserved. Use is subject to license terms.
 */
package com.datasqrl.graphql.inference;

import com.datasqrl.IntegrationTestSettings;
import com.datasqrl.graphql.inference.SchemaInferenceModel.InferredSchema;
import com.datasqrl.plan.queries.APIQuery;
import com.datasqrl.util.data.Retail;
import com.datasqrl.util.data.Retail.RetailScriptNames;
import org.apache.commons.lang3.tuple.Pair;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.file.Path;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;

class SchemaInferenceModelTest extends AbstractSchemaInferenceModelTest {

  private Retail example = Retail.INSTANCE;

  @BeforeEach
  public void setup() throws IOException {
    initialize(IntegrationTestSettings.getInMemory(), example.getRootPackageDirectory());
  }

  @Test
  public void testC360Inference() {
    Pair<InferredSchema, List<APIQuery>> result = inferSchemaAndQueries(
        example.getScript(RetailScriptNames.FULL),
        Path.of("src/test/resources/c360bundle/schema.full.graphqls"));
    assertEquals(60, result.getKey().getQuery().getFields().size());
    assertEquals(336, result.getValue().size());
  }
}