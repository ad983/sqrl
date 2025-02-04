package com.datasqrl.function;

import com.datasqrl.module.FunctionNamespaceObject;
import com.datasqrl.canonicalizer.Name;
import java.net.URL;
import java.util.Optional;
import lombok.Value;
import org.apache.calcite.sql.SqlFunction;

@Value
public class CalciteFunctionNsObject implements FunctionNamespaceObject<SqlFunction> {
  Name name;
  SqlFunction function;

  String sqlName;

  @Override
  public Optional<URL> getJarUrl() {
    return Optional.empty();
  }
}
