/*
 * Copyright (c) 2021, DataSQRL. All rights reserved. Use is subject to license terms.
 */
package com.datasqrl.packager;

import static com.datasqrl.plan.local.generate.AbstractStatementResolver.toNamePath;

import com.datasqrl.error.ErrorCollector;
import com.datasqrl.canonicalizer.NameCanonicalizer;
import com.datasqrl.canonicalizer.NamePath;
import com.datasqrl.parse.SqrlParser;
import com.datasqrl.parse.SqrlParserImpl;
import java.nio.file.Path;
import java.util.LinkedHashSet;
import java.util.Set;
import lombok.Value;
import org.apache.calcite.sql.ExportDefinition;
import org.apache.calcite.sql.ImportDefinition;
import org.apache.calcite.sql.ScriptNode;
import org.apache.calcite.sql.SqlNode;
import org.apache.commons.collections4.SetUtils;


public class ImportExportAnalyzer {

  NameCanonicalizer canonicalizer = NameCanonicalizer.SYSTEM;
  SqrlParser parser = new SqrlParserImpl();

  public Result analyze(Path sqrlScript, ErrorCollector errors) {
    ScriptNode node;
    try {
      node = parser.parse(sqrlScript, errors);
    } catch (Exception e) {
      errors.warn("Could not compile SQRL script %s", sqrlScript);
      throw e;
    }

    return analyze(node);
  }

  private Result analyze(ScriptNode scriptNode) {
    Set<NamePath> importPkgs = new LinkedHashSet<>();
    Set<NamePath> exportPkgs = new LinkedHashSet<>();
    for (SqlNode statement : scriptNode.getStatements()) {
      if (statement instanceof ImportDefinition) {
        ImportDefinition impDef = (ImportDefinition) statement;
        importPkgs.add(toNamePath(canonicalizer, impDef.getImportPath()).popLast());
      }
      if (statement instanceof ExportDefinition) {
        ExportDefinition expDef = (ExportDefinition) statement;
        exportPkgs.add(toNamePath(canonicalizer, expDef.getSinkPath()).popLast());
      }
    }
    return new Result(importPkgs, exportPkgs);
  }

  @Value
  public static class Result {

    public static final Result EMPTY = new Result(new LinkedHashSet<>(), new LinkedHashSet<>());

    Set<NamePath> importPkgs;
    Set<NamePath> exportPkgs;

    public Result add(Result other) {
      return new Result(SetUtils.union(importPkgs,other.importPkgs),
          SetUtils.union(exportPkgs, other.exportPkgs));
    }

    public Set<NamePath> getPkgs() {
      return SetUtils.union(importPkgs, exportPkgs);
    }
  }


}
