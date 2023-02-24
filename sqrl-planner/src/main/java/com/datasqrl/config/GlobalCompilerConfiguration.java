/*
 * Copyright (c) 2021, DataSQRL. All rights reserved. Use is subject to license terms.
 */
package com.datasqrl.config;


import com.datasqrl.error.ErrorCollector;
import com.datasqrl.util.ConfigurationUtil;
import com.datasqrl.spi.ScriptConfiguration;
import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.NonNull;
import lombok.experimental.SuperBuilder;

import javax.validation.Valid;

@SuperBuilder
@NoArgsConstructor
@Getter
public class GlobalCompilerConfiguration extends GlobalEngineConfiguration {

  @Builder.Default
  @NonNull @Valid
  CompilerConfiguration compiler = new CompilerConfiguration();

  @Valid
  @JsonProperty(ScriptConfiguration.PROPERTY)
  ScriptConfiguration script;

  public CompilerConfiguration initializeCompiler(ErrorCollector errors) {
    if (!ConfigurationUtil.javaxValidate(this, errors)) {
      return null;
    }
    return compiler;
  }

}
