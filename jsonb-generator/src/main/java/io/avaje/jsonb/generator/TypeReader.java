package io.avaje.jsonb.generator;

import io.avaje.jsonb.Json;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import javax.lang.model.element.Element;
import javax.lang.model.element.ExecutableElement;
import javax.lang.model.element.Modifier;
import javax.lang.model.element.TypeElement;
import javax.lang.model.element.VariableElement;

/**
 * Read points for field injection and method injection on baseType plus inherited injection points.
 */
class TypeReader {

  private static final String JAVA_LANG_OBJECT = "java.lang.Object";

  private final List<MethodReader> publicConstructors = new ArrayList<>();
  private final List<FieldReader> allFields = new ArrayList<>();
  private final Map<String, FieldReader> allFieldMap = new HashMap<>();
  private final Map<String, MethodReader> maybeSetterMethods = new LinkedHashMap<>();
  private final Map<String, MethodReader> maybeGetterMethods = new LinkedHashMap<>();
  private final Map<String, MethodReader> allGetterMethods = new LinkedHashMap<>();
  private final Map<String, MethodReader> allSetterMethods = new LinkedHashMap<>();

  private final TypeSubTypeReader subTypes;
  private final TypeElement baseType;
  private final ProcessingContext context;
  private final NamingConvention namingConvention;
  private final boolean hasJsonAnnotation;
  private MethodReader constructor;
  private boolean defaultPublicConstructor;
  private final Map<String, MethodReader.MethodParam> constructorParamMap = new LinkedHashMap<>();
  private TypeSubTypeMeta currentSubType;
  private boolean nonAccessibleField;

  TypeReader(TypeElement baseType, ProcessingContext context, NamingConvention namingConvention) {
    this.baseType = baseType;
    this.context = context;
    this.namingConvention = namingConvention;
    this.hasJsonAnnotation = baseType.getAnnotation(Json.class) != null;
    this.subTypes = new TypeSubTypeReader(baseType, context);
  }

  void read(TypeElement type) {
    final List<FieldReader> localFields = new ArrayList<>();
    for (final Element element : type.getEnclosedElements()) {
      switch (element.getKind()) {
        case CONSTRUCTOR:
          readConstructor(element, type);
          break;
        case FIELD:
          readField(element, localFields);
          break;
        case METHOD:
          readMethod(element, type);
          break;
      }
    }
    if (currentSubType == null && type != baseType) {
      allFields.addAll(0, localFields);
      for (final FieldReader localField : localFields) {
        allFieldMap.put(localField.fieldName(), localField);
      }
    } else {
      for (final FieldReader localField : localFields) {
        final FieldReader commonField = allFieldMap.get(localField.fieldName());
        if (commonField == null) {
          allFields.add(localField);
          allFieldMap.put(localField.fieldName(), localField);
        } else {
          commonField.addSubType(currentSubType);
        }
      }
    }
  }

  private void readField(Element element, List<FieldReader> localFields) {
    if (includeField(element)) {
      localFields.add(new FieldReader(element, namingConvention, currentSubType));
    }
  }

  private boolean includeField(Element element) {
    return !element.getModifiers().contains(Modifier.TRANSIENT)
        && !element.getModifiers().contains(Modifier.STATIC);
  }

  private void readConstructor(Element element, TypeElement type) {
    if (currentSubType != null) {
      if (currentSubType.element() != type) {
        // context.logError("subType " + currentSubType.element() + " ignore constructor " +
        // element);
        return;
      }
    } else if (type != baseType) {
      // context.logError("baseType " + baseType + " ignore constructor: " + element);
      return;
    }
    final ExecutableElement ex = (ExecutableElement) element;
    final MethodReader methodReader = new MethodReader(context, ex, baseType).read();
    if (methodReader.isPublic()) {
      if (currentSubType != null) {
        currentSubType.addConstructor(methodReader);
      } else {
        if (methodReader.getParams().isEmpty()) {
          defaultPublicConstructor = true;
        }
        publicConstructors.add(methodReader);
      }
    }
  }

  private void readMethod(Element element, TypeElement type) {
    final ExecutableElement methodElement = (ExecutableElement) element;
    if (methodElement.getModifiers().contains(Modifier.PUBLIC)) {
      final List<? extends VariableElement> parameters = methodElement.getParameters();
      final String methodKey = methodElement.getSimpleName().toString();
      final MethodReader methodReader = new MethodReader(context, methodElement, type).read();
      if (parameters.size() == 1) {
        if (!maybeSetterMethods.containsKey(methodKey)) {
          maybeSetterMethods.put(methodKey, methodReader);
        }
        allSetterMethods.put(methodKey.toLowerCase(), methodReader);
      } else if (parameters.size() == 0) {
        if (!maybeGetterMethods.containsKey(methodKey)) {
          maybeGetterMethods.put(methodKey, methodReader);
        }
        allGetterMethods.put(methodKey.toLowerCase(), methodReader);
      }
    }
  }

  private void matchFieldsToSetterOrConstructor() {
    for (final FieldReader field : allFields) {
      if (constructorParamMap.get(field.fieldName()) != null) {
        field.constructorParam();
      } else {
        matchFieldToSetter(field);
      }
    }
  }

  private void matchFieldToSetter(FieldReader field) {
    if (!matchFieldToSetter2(field, false)
        && !matchFieldToSetter2(field, true)
        && !matchFieldToSetterByParam(field)
        && !field.isPublicField()) {
      context.logError(
          "Non public field "
              + baseType
              + " "
              + field.fieldName()
              + " with no matching setter or constructor?");
    }
  }

  private boolean matchFieldToSetterByParam(FieldReader field) {
    final String fieldName = field.fieldName();
    for (final MethodReader methodReader : maybeSetterMethods.values()) {
      final List<MethodReader.MethodParam> params = methodReader.getParams();
      final MethodReader.MethodParam methodParam = params.get(0);
      if (methodParam.name().equals(fieldName)) {
        field.setterMethod(methodReader);
        return true;
      }
    }
    return false;
  }

  private boolean matchFieldToSetter2(FieldReader field, boolean loose) {
    final String name = field.fieldName();
    MethodReader setter = setterLookup(name, loose);
    if (setter != null) {
      field.setterMethod(setter);
      return true;
    }
    setter = setterLookup(setterName(name), loose);
    if (setter != null) {
      field.setterMethod(setter);
      return true;
    }
    return false;
  }

  private MethodReader setterLookup(String name, boolean loose) {
    if (loose) {
      return allSetterMethods.get(name.toLowerCase());
    }
    return maybeSetterMethods.get(name);
  }

  private void matchFieldsToGetter() {
    for (final FieldReader field : allFields) {
      matchFieldToGetter(field);
    }
  }

  private void matchFieldToGetter(FieldReader field) {
    if (!matchFieldToGetter2(field, false)
        && !matchFieldToGetter2(field, true)
        && !field.isPublicField()) {
      nonAccessibleField = true;
      if (hasJsonAnnotation) {
        context.logError(
            "Non accessible field "
                + baseType
                + " "
                + field.fieldName()
                + " with no matching getter?");
      } else {
        context.logDebug("Non accessible field " + baseType + " " + field.fieldName());
      }
    }
  }

  private boolean matchFieldToGetter2(FieldReader field, boolean loose) {
    final String name = field.fieldName();
    MethodReader getter = getterLookup(name, loose);
    if (getter != null) {
      field.getterMethod(getter);
      return true;
    }
    getter = getterLookup(getterName(name), loose);
    if (getter != null) {
      field.getterMethod(getter);
      return true;
    }
    getter = getterLookup(isGetterName(name), loose);
    if (getter != null) {
      field.getterMethod(getter);
      return true;
    }
    return false;
  }

  private MethodReader getterLookup(String name, boolean loose) {
    if (!loose) {
      return maybeGetterMethods.get(name);
    }
    return allGetterMethods.get(name.toLowerCase());
  }

  private String setterName(String name) {
    return "set" + Util.initCap(name);
  }

  private String getterName(String name) {
    return "get" + Util.initCap(name);
  }

  private String isGetterName(String name) {
    return "is" + Util.initCap(name);
  }

  boolean nonAccessibleField() {
    return nonAccessibleField;
  }

  List<FieldReader> allFields() {
    return allFields;
  }

  MethodReader constructor() {
    return constructor;
  }

  private MethodReader determineConstructor() {
    if (defaultPublicConstructor) {
      return null;
    }
    if (publicConstructors.size() == 1) {
      return publicConstructors.get(0);
    }
    // check if there is only one public constructor
    final List<MethodReader> allPublic = new ArrayList<>();
    for (final MethodReader ctor : publicConstructors) {
      if (ctor.isPublic()) {
        allPublic.add(ctor);
      }
    }
    if (allPublic.size() == 1) {
      // fallback to the single public constructor
      return allPublic.get(0);
    }
    // find the largest constructor
    int argCount = 0;
    MethodReader largestConstructor = null;
    for (final MethodReader ctor : publicConstructors) {
      if (ctor.isPublic()) {
        final int paramCount = ctor.getParams().size();
        if (paramCount > argCount) {
          largestConstructor = ctor;
          argCount = paramCount;
        }
      }
    }

    return largestConstructor;
  }

  void process() {
    final String base = baseType.getQualifiedName().toString();
    if (!GenericType.isGeneric(base)) {
      read(baseType);
    }
    final TypeElement superElement = superOf(baseType);
    if (superElement != null) {
      addSuperType(superElement, null);
    }
    readSubTypes();
    processCompleted();
  }

  private void readSubTypes() {
    if (hasSubTypes()) {
      for (final TypeSubTypeMeta subType : subTypes.subTypes()) {
        currentSubType = subType;
        final TypeElement element = context.element(subType.type());
        currentSubType.setElement(element);
        addSuperType(element, baseType);
      }
    }
  }

  List<TypeSubTypeMeta> subTypes() {
    return subTypes.subTypes();
  }

  void processCompleted() {
    setFieldPositions();
    constructor = determineConstructor();
    if (constructor != null) {
      final List<MethodReader.MethodParam> params = constructor.getParams();
      for (final MethodReader.MethodParam param : params) {
        constructorParamMap.put(param.name(), param);
      }
    }
    matchFieldsToSetterOrConstructor();
    matchFieldsToGetter();
  }

  /** Set the index position of the fields (for PropertyNames). */
  private void setFieldPositions() {
    final int offset = subTypes.hasSubTypes() ? 1 : 0;
    for (int pos = 0, size = allFields.size(); pos < size; pos++) {
      allFields.get(pos).position(pos + offset);
    }
  }

  private void addSuperType(TypeElement element, TypeElement matchType) {
    final String type = element.getQualifiedName().toString();
    if (!JAVA_LANG_OBJECT.equals(type) && !GenericType.isGeneric(type)) {
      read(element);
      addSuperType(superOf(element), matchType);
    }
  }

  private TypeElement superOf(TypeElement element) {
    return (TypeElement) context.asElement(element.getSuperclass());
  }

  boolean hasSubTypes() {
    return subTypes.hasSubTypes();
  }
}
