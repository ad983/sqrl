/*
 * Copyright (c) 2021, DataSQRL. All rights reserved. Use is subject to license terms.
 */
package com.datasqrl.schema.input;

import com.datasqrl.io.tables.TableSchema;
import com.datasqrl.canonicalizer.Name;
import com.datasqrl.canonicalizer.NamePath;
import com.datasqrl.schema.constraint.Cardinality;
import com.datasqrl.schema.constraint.ConstraintHelper;
import com.datasqrl.schema.type.Type;
import lombok.AllArgsConstructor;
import lombok.Value;

import java.util.Optional;

@Value
@AllArgsConstructor
public class FlexibleTableConverter {

  private final FlexibleTableSchema schema;
  private final boolean hasSourceTimestamp;
  private final Optional<Name> tableAlias;

  public <T> T apply(Visitor<T> visitor) {
    return visitRelation(NamePath.ROOT, tableAlias.orElse(schema.getName()),
        schema.getFields(),
        false, false, hasSourceTimestamp, visitor);
  }

  private <T> T visitRelation(NamePath path, Name name,
      RelationType<FlexibleFieldSchema.Field> relation,
      boolean isNested, boolean isSingleton, boolean hasSourceTime, Visitor<T> visitor) {
    visitor.beginTable(name, path, isNested, isSingleton, hasSourceTime);
    path = path.concat(name);

    for (FlexibleFieldSchema.Field field : relation.getFields()) {
      for (FlexibleFieldSchema.FieldType ftype : field.getTypes()) {
        Name fieldName = FlexibleSchemaHelper.getCombinedName(field, ftype);
        boolean isMixedType = field.getTypes().size() > 1;
        visitFieldType(path, fieldName, ftype, isMixedType, visitor);
      }
    }
    return visitor.endTable(name, path, isNested, isSingleton);
  }

  private <T> void visitFieldType(NamePath path, Name fieldName,
      FlexibleFieldSchema.FieldType ftype,
      boolean isMixedType, Visitor<T> visitor) {
    boolean nullable = isMixedType || !ConstraintHelper.isNonNull(ftype.getConstraints());
    boolean isSingleton = false;
    if (ftype.getType() instanceof RelationType) {
      isSingleton = isSingleton(ftype);
      T nestedTable = visitRelation(path, fieldName,
          (RelationType<FlexibleFieldSchema.Field>) ftype.getType(), true,
          isSingleton, false, visitor);
      nullable = isMixedType || hasZeroOneMultiplicity(ftype);
      visitor.addField(fieldName, nestedTable, nullable, isSingleton);
    } else {
      visitor.addField(fieldName, ftype.getType(), nullable);
    }
  }

  private static boolean isSingleton(FlexibleFieldSchema.FieldType ftype) {
    return ConstraintHelper.getCardinality(ftype.getConstraints()).isSingleton();
  }

  private static boolean hasZeroOneMultiplicity(FlexibleFieldSchema.FieldType ftype) {
    Cardinality card = ConstraintHelper.getCardinality(ftype.getConstraints());
    return card.isSingleton() && card.getMin() == 0;
  }

  public interface Visitor<T> {

    void beginTable(Name name, NamePath namePath, boolean isNested, boolean isSingleton,
        boolean hasSourceTimestamp);

    T endTable(Name name, NamePath namePath, boolean isNested, boolean isSingleton);

    void addField(Name name, Type type, boolean nullable);

    void addField(Name name, T nestedTable, boolean nullable, boolean isSingleTon);
  }

}
