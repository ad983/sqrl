/*
 * Copyright (c) 2021, DataSQRL. All rights reserved. Use is subject to license terms.
 */
package com.datasqrl.graphql.inference;

import com.datasqrl.canonicalizer.NamePath;
import com.datasqrl.canonicalizer.ReservedName;
import com.datasqrl.config.SqrlConfig;
import com.datasqrl.graphql.generate.SchemaGeneratorUtil;
import com.datasqrl.graphql.inference.SchemaInferenceModel.*;
import com.datasqrl.graphql.inference.argument.ArgumentHandler;
import com.datasqrl.graphql.inference.argument.EqHandler;
import com.datasqrl.graphql.inference.argument.LimitOffsetHandler;
import com.datasqrl.graphql.server.Model.RootGraphqlModel;
import com.datasqrl.graphql.server.Model.StringSchema;
import com.datasqrl.canonicalizer.Name;
import com.datasqrl.loaders.ModuleLoader;
import com.datasqrl.loaders.TableSourceNamespaceObject;
import com.datasqrl.module.NamespaceObject;
import com.datasqrl.schema.Column;
import com.datasqrl.schema.Field;
import com.datasqrl.schema.Relationship;
import com.datasqrl.schema.SQRLTable;
import graphql.language.EnumTypeDefinition;
import graphql.language.FieldDefinition;
import graphql.language.InputObjectTypeDefinition;
import graphql.language.InputValueDefinition;
import graphql.language.ListType;
import graphql.language.NonNullType;
import graphql.language.ObjectTypeDefinition;
import graphql.language.ScalarTypeDefinition;
import graphql.language.Type;
import graphql.language.TypeDefinition;
import graphql.language.TypeName;
import graphql.schema.idl.SchemaParser;
import graphql.schema.idl.TypeDefinitionRegistry;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Collectors;
import lombok.Getter;
import org.apache.calcite.jdbc.SqrlSchema;
import org.apache.calcite.tools.RelBuilder;
import org.apache.commons.lang3.StringUtils;
import org.apache.flink.calcite.shaded.com.google.common.base.Preconditions;

@Getter
public class SchemaInference {

  private final ModuleLoader moduleLoader;
  private final String schemaName;
  private final TypeDefinitionRegistry registry;
  private final SqrlSchema schema;
  private final RootGraphqlModel.RootGraphqlModelBuilder root;
  private final List<ArgumentHandler> argumentHandlers = List.of(new EqHandler(), new LimitOffsetHandler());
  private final RelBuilder relBuilder;
  private final Set<FieldDefinition> visited = new HashSet<>();
  private final Map<ObjectTypeDefinition, SQRLTable> visitedObj = new HashMap<>();

  public SchemaInference(ModuleLoader moduleLoader, String schemaName, String gqlSchema, SqrlSchema schema,
      RelBuilder relBuilder) {
    this.moduleLoader = moduleLoader;
    this.schemaName = schemaName;
    this.registry = (new SchemaParser()).parse(gqlSchema);
    this.schema = schema;
    this.root = RootGraphqlModel.builder()
        .schema(StringSchema.builder().schema(gqlSchema).build());
    this.relBuilder = relBuilder;
  }
  //Handles walking the schema completely

  public InferredSchema accept() {

    //resolve additional types
    resolveTypes();

    InferredQuery query = registry.getType("Query")
        .map(q -> resolveQueries((ObjectTypeDefinition) q))
        .orElseThrow(() -> new RuntimeException("Must have a query type"));

    Optional<InferredMutations> mutation = registry.getType("Mutation")
        .map(m -> resolveMutations((ObjectTypeDefinition) m));

    Optional<InferredRootObject> subscription = registry.getType("subscription")
        .map(s -> resolveSubscriptions((ObjectTypeDefinition) s));

    InferredSchema inferredSchema = new InferredSchema(query, mutation, subscription);
    return inferredSchema;
  }

  private void resolveTypes() {
    //todo custom types: walk all defined types and import them
  }

  private InferredQuery resolveQueries(ObjectTypeDefinition query) {
    List<InferredField> fields = new ArrayList<>();
    for (FieldDefinition fieldDefinition : query.getFieldDefinitions()) {
      visited.add(fieldDefinition);
      fields.add(resolveQueryFromSchema(fieldDefinition, fields, query));
    }

    return new InferredQuery(query, fields);
  }

  private InferredField resolveQueryFromSchema(FieldDefinition fieldDefinition,
      List<InferredField> fields, ObjectTypeDefinition parent) {
    Optional<SQRLTable> sqrlTable = getTableOfType(fieldDefinition.getType(),
        fieldDefinition.getName());
    Preconditions.checkState(sqrlTable.isPresent(),
        "Could not find associated SQRL type for field %s on type %s",
        fieldDefinition.getName(), fieldDefinition.getType());
    SQRLTable table = sqrlTable.get();

    return inferObjectField(fieldDefinition, table, fields, parent);
  }

  private Optional<SQRLTable> getTableOfType(Type type, String name) {
    for (SQRLTable table : schema.getRootTables()) {
      if (isNameEqual(table.getName().getCanonical(), name) &&
          lookupType(type).filter(t -> structurallyEqual(t, table)).isPresent()) {
        return Optional.of(table);
      }
    }
    return Optional.empty();
  }

  private boolean isNameEqual(String canonical, String graphqlName) {
    String conform = SchemaGeneratorUtil.conformName(canonical);

    return conform.equalsIgnoreCase(Name.system(graphqlName).getCanonical()) ||
        conform.equalsIgnoreCase(Name.system(stripTrailing(graphqlName)).getCanonical());
  }

  //Graphql generator may add trailing _ for name collisions
  private String stripTrailing(String graphqlName) {
    return StringUtils.stripEnd(graphqlName, "_");
  }

  private Optional<ObjectTypeDefinition> lookupType(Type type) {
    Optional<TypeDefinition> t = registry.getType(type);
    return t.filter(o -> o instanceof ObjectTypeDefinition)
        .map(o -> (ObjectTypeDefinition) o);
  }

  private InferredField inferObjectField(FieldDefinition fieldDefinition, SQRLTable table,
      List<InferredField> fields, ObjectTypeDefinition parent) {
    TypeDefinition typeDef = unwrapObjectType(fieldDefinition.getType());

    if (typeDef instanceof ObjectTypeDefinition) {
      ObjectTypeDefinition obj = (ObjectTypeDefinition) typeDef;
      Preconditions.checkState(visitedObj.get(obj) == null || visitedObj.get(obj) == table,
          "Cannot redefine a type to point to a different SQRL table. Use an interface instead.");
      visitedObj.put(obj, table);
      InferredObjectField inferredObjectField = new InferredObjectField(parent, fieldDefinition,
          (ObjectTypeDefinition) typeDef, table);
      fields.addAll(walkChildren((ObjectTypeDefinition) typeDef, table, fields));
      return inferredObjectField;
    } else {
      throw new RuntimeException("Tbd");
    }
  }

  private List<InferredField> walkChildren(ObjectTypeDefinition typeDef, SQRLTable table,
      List<InferredField> fields) {
    Preconditions.checkState(structurallyEqual(typeDef, table), "Field(s) not allowed [%s]",
        getInvalidFields(typeDef, table), typeDef.getName());

    return typeDef.getFieldDefinitions().stream()
        .filter(f -> !visited.contains(f))
        .map(f -> walk(f, table.getField(Name.system(f.getName())).get(), fields, typeDef))
        .collect(Collectors.toList());
  }

  private InferredField walk(FieldDefinition fieldDefinition, Field field,
      List<InferredField> fields, ObjectTypeDefinition parent) {
    visited.add(fieldDefinition);
    if (field instanceof Relationship) {
      return walkRel(fieldDefinition, (Relationship) field, fields, parent);
    } else {
      return walkScalar(fieldDefinition, (Column) field, parent);
    }
  }

  private InferredField walkRel(FieldDefinition fieldDefinition, Relationship relationship,
      List<InferredField> fields, ObjectTypeDefinition parent) {
    return new NestedField(relationship,
        inferObjectField(fieldDefinition, relationship.getToTable(),
            fields, parent));
  }

  private InferredField walkScalar(FieldDefinition fieldDefinition, Column column,
      ObjectTypeDefinition parent) {
    /*
     * Check to see what types are compatible
     */
    return new InferredScalarField(fieldDefinition, column, parent);
  }

  private boolean structurallyEqual(ObjectTypeDefinition typeDef, SQRLTable table) {
    return typeDef.getFieldDefinitions().stream()
        .allMatch(f -> table.getField(Name.system(f.getName())).isPresent());
  }

  private List<String> getInvalidFields(ObjectTypeDefinition typeDef, SQRLTable table) {
    return typeDef.getFieldDefinitions().stream()
        .filter(f -> table.getField(Name.system(f.getName())).isEmpty())
        .map(FieldDefinition::getName)
        .collect(Collectors.toList());
  }

  private TypeDefinition unwrapObjectType(Type type) {
    //type can be in a single array with any non-nulls, e.g. [customer!]!
    type = unboxNonNull(type);
    if (type instanceof ListType) {
      type = ((ListType) type).getType();
    }
    type = unboxNonNull(type);

    Optional<TypeDefinition> typeDef = this.registry.getType(type);

    Preconditions.checkState(typeDef.isPresent(), "Could not find Object type");

    return typeDef.get();
  }

  private boolean isListType(Type type) {
    return unboxNonNull(type) instanceof ListType;
  }

  private Type unboxNonNull(Type type) {
    if (type instanceof NonNullType) {
      return unboxNonNull(((NonNullType) type).getType());
    }
    return type;
  }

  private InferredMutations resolveMutations(ObjectTypeDefinition m) {
    List<InferredMutation> mutations = new ArrayList<>();
    for(FieldDefinition def : m.getFieldDefinitions()) {
      validateStructurallyEqualMutation(def, getValidMutationReturnType(def), getValidMutationInput(def),
          List.of(ReservedName.SOURCE_TIME.getCanonical()));

      NamespaceObject ns = moduleLoader.getModule(NamePath.of(this.schemaName))
          .flatMap(mod -> mod.getNamespaceObject(Name.system(def.getName())))
          .orElseThrow(() ->
              new RuntimeException(String.format(
                  "Could not find mutation source: %s.%s", this.schemaName, def.getName())));
      TableSourceNamespaceObject tsNs = (TableSourceNamespaceObject) ns;

      SqrlConfig config = tsNs.getTable().getConfiguration().getConnectorConfig();
      Map<String, String> kafkaSink = new HashMap<>();
      for (String key : config.getKeys()) {
        kafkaSink.put(key, config.asString(key).get());
      }

      InferredMutation mutation = new InferredMutation(tsNs.getName().getDisplay(), kafkaSink);
      mutations.add(mutation);
    }

    return new InferredMutations(mutations);
  }

  private Object validateStructurallyEqualMutation(FieldDefinition def, ObjectTypeDefinition subType, InputObjectTypeDefinition type,
      List<String> allowedFieldNames) {


    //The return type can have _source_time
    for (FieldDefinition f : subType.getFieldDefinitions()) {
      if (allowedFieldNames.contains(f.getName())) {
        continue;
      }

      String name = f.getName();

      InputValueDefinition in = findExactlyOneInputValue(def, name, type.getInputValueDefinitions());

      //validate type structurally equal
      validateStructurallyEqualMutation(f, in);

    }

    return null;
  }

  private void validateStructurallyEqualMutation(FieldDefinition subType, InputValueDefinition type) {
    Preconditions.checkState(subType.getName().equals(type.getName()),
        "Name must be equal to the input name {} {}", subType.getName(), type.getName());
    Type subT = subType.getType();
    Type t = type.getType();

    validateStructurallyType(subType, subT, t);
  }

  private Object validateStructurallyType(FieldDefinition field, Type subT, Type t) {
    if (t instanceof NonNullType) {
      //subType may be nullable if type is non-null
      NonNullType nonNullType = (NonNullType) t;
      if (subT instanceof NonNullType) {
        NonNullType nonNullSubType = (NonNullType) subT;
        return validateStructurallyType(field, nonNullSubType.getType(), nonNullType.getType());
      } else {
        return validateStructurallyType(field, subT, nonNullType.getType());
      }
    } else if (t instanceof ListType) {
      //subType must be a list
      Preconditions.checkState(subT instanceof ListType, "List type mismatch for field. Must match the input type. " + field.getName());
      ListType listType = (ListType) t;
      ListType listSubType = (ListType) subT;
      return validateStructurallyType(field, listSubType, listType);
    } else if (t instanceof TypeName) {
      //If subtype nonnull then it could return errors
      Preconditions.checkState(!(subT instanceof NonNullType),
          "Non-null found on field %s, could result in errors if input type is null",
          field.getName());
      Preconditions.checkState(!(subT instanceof ListType),
          "List type found on field %s when the input is a scalar type",
          field.getName());

      //If typeName, resolve then
      TypeName typeName = (TypeName) t;
      TypeName subTypeName = (TypeName) subT;
      TypeDefinition typeDefinition = registry.getType(typeName)
          .orElseThrow(()->new RuntimeException("Could not find type: " + typeName.getName()));
      TypeDefinition subTypeDefinition = registry.getType(subTypeName)
          .orElseThrow(()->new RuntimeException("Could not find type: " + subTypeName.getName()));

      //If input or scalar
      if (typeDefinition instanceof ScalarTypeDefinition) {
        Preconditions.checkState(subTypeDefinition instanceof ScalarTypeDefinition &&
            typeDefinition.getName().equals(subTypeDefinition.getName()),
            "Scalar types not matching for field [%s]: %s %s", field.getName(),
            typeDefinition.getName(), subTypeDefinition.getName());
        return null;
      } else if (typeDefinition instanceof EnumTypeDefinition) {
        Preconditions.checkState(subTypeDefinition instanceof EnumTypeDefinition &&
                typeDefinition.getName().equals(subTypeDefinition.getName()),
            "Enum types not matching for field [%s]: %s %s", field.getName(),
            typeDefinition.getName(), subTypeDefinition.getName());
        return null;
      } else if (typeDefinition instanceof InputObjectTypeDefinition){
        Preconditions.checkState(subTypeDefinition instanceof ObjectTypeDefinition,
            "Return object type must match with an input object type not matching for field [%s]: %s %s", field.getName(),
            typeDefinition.getName(), subTypeDefinition.getName());
        ObjectTypeDefinition obj = (ObjectTypeDefinition) subTypeDefinition;
        InputObjectTypeDefinition in = (InputObjectTypeDefinition) typeDefinition;
        return validateStructurallyEqualMutation(field, obj, in, List.of());
      } else {
        throw new RuntimeException("Unknown type encountered: " + typeDefinition.getName());
      }
    }

    throw new RuntimeException("unexpected type");
  }

  private InputValueDefinition findExactlyOneInputValue(FieldDefinition def, String name,
      List<InputValueDefinition> inputValueDefinitions) {
    InputValueDefinition found = null;
    for (InputValueDefinition in : inputValueDefinitions) {
      if (in.getName().equals(name)) {
        if (found != null) {
          throw new RuntimeException("Too many fields found");
        }
        found = in;
      }
    }

    if (found == null) {
      throw new RuntimeException("Could not find field " + name + " in type " + def.getName());
    }

    return found;
  }
//
//  @Value
//  class StructuralTypeCheckerContext {
//    Type type;
//  }
//  @Value
//  class StructuralTypeChecker implements GraphqlTypeVisitor<Boolean, InputValueDefinition> {
//    FieldDefinition defRoot;
//
//    @Override
//    public Boolean visitListType(ListType node, InputValueDefinition context) {
//      context.getType()
//
//      GraphqlSchemaVisitor.accept(this, node.getType(), context);
//
////      if (!(context.type instanceof ListType && context.subType instanceof ListType)) {
////        throw new RuntimeException("Not structurally equal, found ");
////      }
//
//      return null;
//    }
//
//    @Override
//    public Boolean visitNonNullType(NonNullType node, InputValueDefinition context) {
//      return null;
//    }
//
//    @Override
//    public Boolean visitTypeName(TypeName node, InputValueDefinition context) {
//      return null;
//    }
//  }

  private ObjectTypeDefinition getValidMutationReturnType(FieldDefinition def) {
    Type type = def.getType();
    if (type instanceof NonNullType) {
      type = ((NonNullType)type).getType();
    }

    Preconditions.checkState(type instanceof TypeName, def.getName() + " must be a singular return value");
    TypeName name = (TypeName) type;

    TypeDefinition typeDef = registry.getType(name)
        .orElseThrow(()->new RuntimeException("Could not find return type:" + name.getName()));
    Preconditions.checkState(typeDef instanceof ObjectTypeDefinition, "Return must be an object type:" + def.getName());

    return (ObjectTypeDefinition) typeDef;
  }

  private InputObjectTypeDefinition getValidMutationInput(FieldDefinition def) {
    Preconditions.checkState(!(def.getInputValueDefinitions().size() == 0), def.getName() + " has too few arguments. Must have one non-null input type argument.");
    Preconditions.checkState(def.getInputValueDefinitions().size() == 1, def.getName() + " has too many arguments. Must have one non-null input type argument.");
    Preconditions.checkState(def.getInputValueDefinitions().get(0).getType() instanceof NonNullType,
        "[" + def.getName() + "] " + def.getInputValueDefinitions().get(0).getName() + "Must be non-null.");

    NonNullType nonNullType = (NonNullType)def.getInputValueDefinitions().get(0).getType();
    Preconditions.checkState(nonNullType.getType() instanceof TypeName, "Must be a singular value");
    TypeName name = (TypeName) nonNullType.getType();

    TypeDefinition typeDef = registry.getType(name)
        .orElseThrow(()->new RuntimeException("Could not find input type:" + name.getName()));
    Preconditions.checkState(typeDef instanceof InputObjectTypeDefinition, "Input must be an input object type:" + def.getName());

    return (InputObjectTypeDefinition) typeDef;
  }

  private InferredRootObject resolveSubscriptions(ObjectTypeDefinition s) {

    return null;
  }
}
