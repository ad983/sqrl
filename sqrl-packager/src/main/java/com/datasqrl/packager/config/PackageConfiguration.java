/*
 * Copyright (c) 2021, DataSQRL. All rights reserved. Use is subject to license terms.
 */
package com.datasqrl.packager.config;

import com.datasqrl.error.ErrorCollector;
import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.google.common.base.Strings;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

@Builder
@AllArgsConstructor
@NoArgsConstructor
@Getter
@JsonIgnoreProperties(ignoreUnknown = true)
public class PackageConfiguration {

  public static final String DEFAULT_VARIANT = "default";

  String name;
  String version;
  String variant;
  @Builder.Default
  Boolean latest = true;

  String license;
  String repository;
  String homepage;
  String documentation;
  String readme;
  String description;

  public boolean initialize(ErrorCollector errors) {
    errors.checkFatal(!Strings.isNullOrEmpty(name),"Need to specify a package name");
    errors.checkFatal(!Strings.isNullOrEmpty(version),"Need to specify a package version");
    if (latest==null) latest = true;
    if (Strings.isNullOrEmpty(variant)) variant=DEFAULT_VARIANT;
    return true;
  }




}
