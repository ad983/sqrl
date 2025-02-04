/*
 * Copyright (c) 2021, DataSQRL. All rights reserved. Use is subject to license terms.
 */
package com.datasqrl.plan.table;

import com.datasqrl.canonicalizer.Name;
import com.datasqrl.canonicalizer.ReservedName;
import lombok.NonNull;

import java.util.List;

public abstract class SourceRelationalTableImpl extends AbstractRelationalTable implements
    SourceRelationalTable {

  protected SourceRelationalTableImpl(@NonNull Name nameId) {
    super(nameId);
  }

  @Override
  public List<String> getPrimaryKeyNames() {
    return List.of(ReservedName.UUID.getCanonical());
  }

}
