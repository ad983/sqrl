/*
 * Copyright (c) 2021, DataSQRL. All rights reserved. Use is subject to license terms.
 */
package com.datasqrl.io.tables;

import com.datasqrl.canonicalizer.Name;
import com.datasqrl.canonicalizer.NamePath;
import com.datasqrl.io.DataSystemConnectorSettings;
import com.datasqrl.io.formats.FormatFactory;

public class TableInput extends AbstractExternalTable {

  public TableInput(DataSystemConnectorSettings dataset, TableConfig configuration, NamePath path,
                    Name name) {
    super(dataset, configuration, path, name);
  }

  public DataSystemConnectorSettings getConnectorSettings() {
    return configuration.getConnectorSettings();
  }

  public FormatFactory.Parser getParser() {
    FormatFactory format = configuration.getFormat();
    return format.getParser(configuration.getFormatConfig());
  }

}