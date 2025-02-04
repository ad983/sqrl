/*
 * Copyright (c) 2021, DataSQRL. All rights reserved. Use is subject to license terms.
 */
package com.datasqrl.error;

import com.fasterxml.jackson.databind.annotation.JsonSerialize;
import com.google.common.base.Preconditions;
import com.google.common.base.Strings;
import java.util.Arrays;
import lombok.NonNull;
import lombok.Value;

@Value
@JsonSerialize(as = ErrorLocation.class)
class ErrorLocationImpl implements ErrorLocation {

  private final String prefix;
  @NonNull
  private final String[] names;
  private final SourceMap sourceMap;
  private final FileRange file;

  private ErrorLocationImpl(String prefix, SourceMap sourceMap, FileRange file, @NonNull String... names) {
    Preconditions.checkArgument(file==null || sourceMap!=null);
    this.prefix = prefix;
    this.names = names;
    this.sourceMap = sourceMap;
    this.file = file;
  }

  /*
   -- These methods are used by ErrorPrefix --
   */

  public static ErrorLocation of(String prefix, @NonNull String loc) {
    Preconditions.checkArgument(!Strings.isNullOrEmpty(loc), "Invalid location provided");
    return new ErrorLocationImpl(prefix, null, null, loc);
  }

  public static ErrorLocation of(String prefix, @NonNull ErrorLocation other) {
    Preconditions.checkArgument(!other.hasPrefix());
    return new ErrorLocationImpl(prefix, other.getSourceMap(), other.getFile(), other.getPathArray());
  }

  public static ErrorLocation of(String prefix, @NonNull SourceMap sourceMap) {
    return new ErrorLocationImpl(prefix, sourceMap, null);
  }

  @Override
  public ErrorLocation append(@NonNull ErrorLocation other) {
    Preconditions.checkArgument(!other.hasPrefix() && !this.hasFile());
    String[] othNames = other.getPathArray();
    String[] newnames = Arrays.copyOf(names, names.length + othNames.length);
    System.arraycopy(othNames, 0, newnames, names.length, othNames.length);
    return new ErrorLocationImpl(this.prefix, other.getSourceMap(), other.getFile(), newnames);
  }

  @Override
  public @NonNull String[] getPathArray() {
    return names;
  }


  public ErrorLocation resolve(@NonNull String loc) {
    Preconditions.checkArgument(!Strings.isNullOrEmpty(loc), "Invalid location provided");
    String[] newnames = Arrays.copyOf(names, names.length + 1);
    newnames[names.length] = loc;
    return new ErrorLocationImpl(prefix, sourceMap, file, newnames);
  }

  @Override
  public ErrorLocation atFile(@NonNull ErrorLocation.FileRange file) {
    Preconditions.checkArgument(sourceMap!=null);
    return new ErrorLocationImpl(prefix, sourceMap, file, names);
  }

  @Override
  public String getPath() {
    if (names == null || names.length == 0) {
      return "";
    }
    return String.join("/", names);
  }

  @Override
  public ErrorLocation withSourceMap(SourceMap map) {
    Preconditions.checkArgument(sourceMap==null);
    Preconditions.checkArgument(!hasFile());
    return new ErrorLocationImpl(prefix, map, null, names);
  }

  @Override
  public String toString() {
    String result = getPath();
    if (prefix != null) {
      if (Strings.isNullOrEmpty(result)) {
        result = prefix;
      } else {
        result = prefix + "/" + result;
      }
    }
    if (file != null) {
      result += "@" + file.toString();
    }
    return result;
  }

}
