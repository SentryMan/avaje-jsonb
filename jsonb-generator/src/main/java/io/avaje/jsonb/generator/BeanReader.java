package io.avaje.jsonb.generator;

import io.avaje.jsonb.Json;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.TreeSet;
import javax.lang.model.element.Element;
import javax.lang.model.element.TypeElement;

class BeanReader {

  private final TypeElement beanType;
  private final String shortName;
  private final String type;

  private final MethodReader constructor;
  private final List<FieldReader> allFields;
  private final Set<String> importTypes = new TreeSet<>();
  private final NamingConvention namingConvention;
  private final boolean hasSubTypes;
  private final TypeReader typeReader;
  private final String typeProperty;
  private final boolean nonAccessibleField;
  private FieldReader unmappedField;
  private boolean hasRaw;

  BeanReader(TypeElement beanType, ProcessingContext context) {
    this.beanType = beanType;
    this.type = beanType.getQualifiedName().toString();
    this.shortName = shortName(beanType);
    final NamingConventionReader ncReader = new NamingConventionReader(beanType);
    this.namingConvention = ncReader.get();
    this.typeProperty = ncReader.typeProperty();

    this.typeReader = new TypeReader(beanType, context, namingConvention);
    typeReader.process();
    this.nonAccessibleField = typeReader.nonAccessibleField();
    this.hasSubTypes = typeReader.hasSubTypes();
    this.allFields = typeReader.allFields();
    this.constructor = typeReader.constructor();
  }

  @Override
  public String toString() {
    return beanType.toString();
  }

  String shortName() {
    return shortName;
  }

  TypeElement getBeanType() {
    return beanType;
  }

  List<FieldReader> allFields() {
    return allFields;
  }

  boolean hasSubtypes() {
    return hasSubTypes;
  }

  boolean nonAccessibleField() {
    return nonAccessibleField;
  }

  boolean hasJsonAnnotation() {
    return beanType.getAnnotation(Json.class) != null;
  }

  void read() {
    for (final FieldReader field : allFields) {
      field.addImports(importTypes);
      if (field.isRaw()) {
        hasRaw = true;
      }
      if (field.isUnmapped()) {
        unmappedField = field;
      }
    }
  }

  /** Return the short name of the element. */
  private String shortName(Element element) {
    return element.getSimpleName().toString();
  }

  private Set<String> importTypes() {
    importTypes.add(Constants.JSONB_WILD);
    importTypes.add(Constants.IOEXCEPTION);
    importTypes.add(Constants.JSONB_SPI);
    if (!hasSubTypes) {
      importTypes.add(Constants.METHODHANDLE);
    }
    if (Util.validImportType(type)) {
      importTypes.add(type);
    }
    for (final FieldReader allField : allFields) {
      allField.addImports(importTypes);
    }
    return importTypes;
  }

  void writeImports(Append writer) {
    for (final String importType : importTypes()) {
      if (Util.validImportType(importType)) {
        writer.append("import %s;", importType).eol();
      }
    }
    writer.eol();
  }

  void cascadeTypes(Set<String> types) {
    for (final FieldReader allField : allFields) {
      if (allField.include()) {
        allField.cascadeTypes(types);
      }
    }
  }

  void writeFields(Append writer) {
    writer.append("  // naming convention %s", namingConvention).eol();
    for (final FieldReader allField : allFields) {
      allField.writeDebug(writer);
    }
    writer.eol();
    if (hasRaw) {
      writer.append("  private final JsonAdapter<String> rawAdapter;").eol();
    }
    final Set<String> uniqueTypes = new HashSet<>();
    for (final FieldReader allField : allFields) {
      if (allField.include() && !allField.isRaw() && uniqueTypes.add(allField.adapterShortType())) {
        allField.writeField(writer);
      }
    }
    writer.append("  private final PropertyNames names;").eol();
    writer.eol();
  }

  void writeConstructor(Append writer) {
    if (hasRaw) {
      writer.append("    this.rawAdapter = jsonb.rawAdapter();").eol();
    }
    final Set<String> uniqueTypes = new HashSet<>();
    for (final FieldReader allField : allFields) {
      if ((allField.include() || !allField.isRaw())
          && uniqueTypes.add(allField.adapterShortType())) {
        allField.writeConstructor(writer);
      }
    }
    writer.append("    this.names = jsonb.properties(");
    if (hasSubTypes) {
      writer.append("\"").append(typeProperty).append("\", ");
    }
    for (int i = 0, size = allFields.size(); i < size; i++) {
      final FieldReader fieldReader = allFields.get(i);
      if (i > 0) {
        writer.append(", ");
      }
      writer.append("\"").append(fieldReader.propertyName()).append("\"");
    }
    writer.append(");").eol();
  }

  void writeViewSupport(Append writer) {
    if (!hasSubTypes) {
      writeView(writer);
      writeViewBuild(writer);
    }
  }

  private void writeView(Append writer) {
    writer.eol();
    writer.append("  @Override").eol();
    writer.append("  public boolean isViewBuilderAware() {").eol();
    writer.append("    return true;").eol();
    writer.append("  }").eol().eol();
    writer.append("  @Override").eol();
    writer.append("  public ViewBuilderAware viewBuild() {").eol();
    writer.append("    return this;").eol();
    writer.append("  }").eol().eol();
  }

  private void writeViewBuild(Append writer) {
    writer.append("  @Override").eol();
    writer
        .append("  public void build(ViewBuilder builder, String name, MethodHandle handle) {")
        .eol();
    writer.append("    builder.beginObject(name, handle);").eol();
    if (!hasSubTypes) {
      for (final FieldReader allField : allFields) {
        if (allField.includeToJson(null)) {
          allField.writeViewBuilder(writer, shortName);
        }
      }
    }
    writer.append("    builder.endObject();").eol();
    writer.append("  }").eol();
  }

  void writeToJson(Append writer) {
    final String varName = Util.initLower(shortName);
    writer.eol();
    writer.append("  @Override").eol();
    writer.append("  public void toJson(JsonWriter writer, %s %s) {", shortName, varName).eol();
    writer.append("    writer.beginObject();").eol();
    writer.append("    writer.names(names);").eol();
    if (hasSubTypes) {
      writeToJsonForSubtypes(writer, varName);
    } else {
      writeToJsonForType(writer, varName, "    ", null);
    }
    writer.append("    writer.endObject();").eol();
    writer.append("  }").eol();
  }

  private void writeToJsonForSubtypes(Append writer, String varName) {
    if (hasSubTypes) {
      final List<TypeSubTypeMeta> subTypes = typeReader.subTypes();
      for (int i = 0; i < subTypes.size(); i++) {
        final TypeSubTypeMeta subTypeMeta = subTypes.get(i);
        final String subType = subTypeMeta.type();
        final String subTypeName = subTypeMeta.name();
        final String elseIf = i == 0 ? "if" : "else if";
        writer.append("    %s (%s instanceof %s) {", elseIf, varName, subType).eol();
        writer.append("      %s sub = (%s)%s;", subType, subType, varName).eol();
        writer.append("      writer.name(0);").eol();
        writer.append("      stringJsonAdapter.toJson(writer, \"%s\");", subTypeName).eol();
        writeToJsonForType(writer, "sub", "      ", subType);
        writer.append("    }").eol();
      }
    }
  }

  private void writeToJsonForType(Append writer, String varName, String prefix, String type) {
    for (final FieldReader allField : allFields) {
      if (allField.includeToJson(type)) {
        allField.writeToJson(writer, varName, prefix);
      }
    }
  }

  void writeFromJson(Append writer) {
    final String varName = Util.initLower(shortName);
    writer.eol();
    writer.append("  @Override").eol();
    writer.append("  public %s fromJson(JsonReader reader) {", shortName, varName).eol();
    final boolean directLoad = constructor == null && !hasSubTypes;
    if (directLoad) {
      // default public constructor
      writer.append("    %s _$%s = new %s();", shortName, varName, shortName).eol();
    } else {
      writer
          .append(
              "    // variables to read json values into, constructor params don't need _set$ flags")
          .eol();
      for (final FieldReader allField : allFields) {
        if (allField.includeFromJson()) {
          allField.writeFromJsonVariables(writer);
        }
      }
    }
    if (hasSubTypes) {
      writer.eol().append("    String type = null;").eol();
    }
    if (unmappedField != null) {
      writer.append("    Map<String, Object> unmapped = new LinkedHashMap<>();").eol();
    }
    writeFromJsonSwitch(writer, directLoad, varName);
    writer.eol();
    if (hasSubTypes) {
      writeFromJsonWithSubTypes(writer, varName);
      return;
    }
    if (!directLoad) {
      writeJsonBuildResult(writer, varName);
    } else if (unmappedField != null) {
      writer.append("   // unmappedField... ", varName).eol();
      unmappedField.writeFromJsonUnmapped(writer, varName);
    }
    writer.append("    return _$%s;", varName).eol();
    writer.append("  }").eol();
  }

  private void writeJsonBuildResult(Append writer, String varName) {
    writer.append("    // build and return %s", shortName).eol();
    writer.append("    %s _$%s = new %s(", shortName, varName, shortName);
    final List<MethodReader.MethodParam> params = constructor.getParams();
    for (int i = 0, size = params.size(); i < size; i++) {
      if (i > 0) {
        writer.append(", ");
      }
      writer.append(
          constructorParamName(params.get(i).name())); // assuming name matches field here?
    }
    writer.append(");").eol();
    for (final FieldReader allField : allFields) {
      if (allField.includeFromJson()) {
        allField.writeFromJsonSetter(writer, varName, "");
      }
    }
  }

  private void writeFromJsonWithSubTypes(Append writer, String varName) {
    writer.append("    if (type == null) {").eol();
    writer
        .append(
            "      throw new IllegalStateException(\"Missing %s property which is required?\");",
            typeProperty)
        .eol();
    writer.append("    }").eol();
    for (final TypeSubTypeMeta subTypeMeta : typeReader.subTypes()) {
      subTypeMeta.writeFromJsonBuild(writer, varName, this);
    }
    writer
        .append(
            "    throw new IllegalStateException(\"Unknown value for %s property \" + type);",
            typeProperty)
        .eol();
    writer.append("  }").eol();
  }

  String constructorParamName(String name) {
    if (unmappedField != null && unmappedField.fieldName().equals(name)) {
      return "unmapped";
    }
    return "_val$" + name;
  }

  private void writeFromJsonSwitch(Append writer, boolean defaultConstructor, String varName) {
    writer.eol();
    writer.append("    // read json").eol();
    writer.append("    reader.beginObject();").eol();
    writer.append("    reader.names(names);").eol();
    writer.append("    while (reader.hasNextField()) {").eol();
    writer.append("      String fieldName = reader.nextField();").eol();
    writer.append("      switch (fieldName) {").eol();
    if (hasSubTypes) {
      writer.append("        case \"%s\": {", typeProperty).eol();
      writer.append("          type = stringJsonAdapter.fromJson(reader); break;").eol();
      writer.append("        }").eol();
    }
    for (final FieldReader allField : allFields) {
      allField.writeFromJsonSwitch(writer, defaultConstructor, varName);
    }
    writer.append("        default: {").eol();
    if (unmappedField != null) {
      writer.append("          Object value = objectJsonAdapter.fromJson(reader);").eol();
      writer.append("          unmapped.put(fieldName, value);").eol();
    } else {
      writer.append("          reader.unmappedField(fieldName);").eol();
      writer.append("          reader.skipValue();").eol();
    }
    writer.append("        }").eol();
    writer.append("      }").eol();
    writer.append("    }").eol();
    writer.append("    reader.endObject();").eol();
  }
}
