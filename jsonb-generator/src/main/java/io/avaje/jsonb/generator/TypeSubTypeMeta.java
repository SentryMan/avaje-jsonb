package io.avaje.jsonb.generator;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import javax.lang.model.element.TypeElement;

class TypeSubTypeMeta {

  private String type;
  private String name;
  private TypeElement typeElement;
  private boolean defaultPublicConstructor;
  private final List<MethodReader> publicConstructors = new ArrayList<>();

  @Override
  public String toString() {
    return type;
  }

  void add(String key, String val) {
    if ("type()".equals(key)) {
      type = Util.trimClassSuffix(val);
    } else if ("name()".equals(key)) {
      name = Util.trimQuotes(val);
    }
  }

  void setElement(TypeElement element) {
    this.typeElement = element;
  }

  TypeElement element() {
    return typeElement;
  }

  String type() {
    return type;
  }

  String name() {
    if (name == null) {
      name = Util.shortName(type);
    }
    return name;
  }

  void addConstructor(MethodReader methodReader) {
    if (methodReader.getParams().isEmpty()) {
      defaultPublicConstructor = true;
    }
    publicConstructors.add(methodReader);
  }

  void writeFromJsonBuild(Append writer, String varName, BeanReader beanReader) {
    writer.append("    if (\"%s\".equals(type)) {", name()).eol();
    writeFromJsonConstructor(writer, varName, beanReader);
    writeFromJsonSetters(writer, varName, beanReader);
    writer.append("      return _$%s;", varName).eol();
    writer.append("    }").eol();
  }

  private void writeFromJsonSetters(Append writer, String varName, BeanReader beanReader) {
    for (final FieldReader field : beanReader.allFields()) {
      if (isIncludeSetter(field)) {
        field.writeFromJsonSetter(writer, varName, "  ");
      }
    }
  }

  private boolean isIncludeSetter(FieldReader field) {
    return field.includeFromJson()
        && !constructorFieldNames.contains(field.fieldName())
        && field.includeForType(this);
  }

  private final Set<String> constructorFieldNames = new LinkedHashSet<>();

  private void writeFromJsonConstructor(Append writer, String varName, BeanReader beanReader) {
    writer.append("      %s _$%s = new %s(", type, varName, type);
    final MethodReader constructor = findConstructor();
    if (constructor != null) {
      final List<MethodReader.MethodParam> params = constructor.getParams();
      for (int i = 0, size = params.size(); i < size; i++) {
        if (i > 0) {
          writer.append(", ");
        }
        final String paramName = params.get(i).name();
        constructorFieldNames.add(paramName);
        writer.append(
            beanReader.constructorParamName(paramName)); // assuming name matches field here?
      }
    }
    writer.append(");").eol();
  }

  private MethodReader findConstructor() {
    if (defaultPublicConstructor || publicConstructors.isEmpty()) {
      return null;
    }
    return publicConstructors.get(0);
  }
}
