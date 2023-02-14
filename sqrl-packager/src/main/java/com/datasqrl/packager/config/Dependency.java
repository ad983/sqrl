/*
 * Copyright (c) 2021, DataSQRL. All rights reserved. Use is subject to license terms.
 */
package com.datasqrl.packager.config;

import com.datasqrl.error.ErrorCollector;
import com.google.common.base.Preconditions;
import com.google.common.base.Strings;
import lombok.AllArgsConstructor;
import lombok.EqualsAndHashCode;
import lombok.Getter;
import lombok.NoArgsConstructor;

@AllArgsConstructor
@Getter
@EqualsAndHashCode
@NoArgsConstructor
public class Dependency {

  String name;
  String version;
  String variant;

  @Override
  public String toString() {
    return name + "@" + version + "/" + variant;
  }

  /**
   * Normalizes a dependency and uses the dependency package name as the name unless it is explicitly specified.
   * @param defaultName
   * @return
   */
  public Dependency normalize(String defaultName, ErrorCollector errors) {
    errors.checkFatal(!Strings.isNullOrEmpty(defaultName),"Invalid dependency name: %s", defaultName);
    errors.checkFatal(!Strings.isNullOrEmpty(this.version),"Need to specify a version for dependency [%s]", defaultName);
    if (Strings.isNullOrEmpty(this.name)) {
      this.name = defaultName;
    }
    if (Strings.isNullOrEmpty(this.variant)) {
      this.variant = PackageConfiguration.DEFAULT_VARIANT;
    }
    return this;
  }

  public static Dependency of(PackageConfiguration pkgConfig) {
    Preconditions.checkArgument(!Strings.isNullOrEmpty(pkgConfig.getName()));
    Preconditions.checkArgument(!Strings.isNullOrEmpty(pkgConfig.getVersion()));
    Preconditions.checkArgument(!Strings.isNullOrEmpty(pkgConfig.getVariant()));
    return new Dependency(pkgConfig.getName(), pkgConfig.getVersion(), pkgConfig.getVariant());
  }

}
