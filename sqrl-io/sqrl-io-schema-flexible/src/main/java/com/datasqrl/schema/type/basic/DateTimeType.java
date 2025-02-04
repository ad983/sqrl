/*
 * Copyright (c) 2021, DataSQRL. All rights reserved. Use is subject to license terms.
 */
package com.datasqrl.schema.type.basic;

import com.datasqrl.schema.type.SqrlTypeVisitor;
import java.time.Instant;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeParseException;
import java.util.Optional;
import java.util.function.Function;

public class DateTimeType extends AbstractBasicType<Instant> {

  public static final DateTimeType INSTANCE = new DateTimeType();

  @Override
  public String getName() {
    return "DATETIME";
  }

  public <R, C> R accept(SqrlTypeVisitor<R, C> visitor, C context) {
    return visitor.visitDateTimeType(this, context);
  }

  @Override
  public Conversion conversion() {
    return Conversion.INSTANCE;
  }

  private static class StringParser implements Function<String,Instant> {

    DateTimeFormatter[] formatters = {
        DateTimeFormatter.ISO_INSTANT,
        DateTimeFormatter.ISO_DATE_TIME.withZone(ZoneId.systemDefault()),
        DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss[.SSS]").withZone(
            ZoneId.systemDefault())
    };

    @Override
    public Instant apply(String s) {
      for (DateTimeFormatter formatter : formatters) {
        try {
          return formatter.parse(s, Instant::from);
        } catch (DateTimeParseException e) {
          //try next formatter
        }
      }
      throw new IllegalArgumentException();
    }
  }

  public static class Conversion extends SimpleBasicType.Conversion<Instant> {

    private static final Conversion INSTANCE = new Conversion();

    public Conversion() {
      super(Instant.class, new StringParser());
    }

    @Override
    public Instant convert(Object o) {
      if (o instanceof Instant) {
        return (Instant) o;
      }
      if (o instanceof Number) {
        return Instant.ofEpochSecond(((Number) o).longValue());
      }
      throw new IllegalArgumentException("Invalid type to convert: " + o.getClass());
    }

    @Override
    public Optional<Integer> getTypeDistance(BasicType fromType) {
      if (fromType instanceof IntegerType) {
        return Optional.of(70);
      }
      return Optional.empty();
    }

  }
}
