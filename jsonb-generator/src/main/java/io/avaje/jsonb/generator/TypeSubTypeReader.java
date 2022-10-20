package io.avaje.jsonb.generator;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import javax.lang.model.element.AnnotationMirror;
import javax.lang.model.element.AnnotationValue;
import javax.lang.model.element.ExecutableElement;
import javax.lang.model.element.TypeElement;

/** Read the @Json.SubType annotations. */
class TypeSubTypeReader {

  private static final String JSON_SUBTYPE = "io.avaje.jsonb.Json.SubType";
  private static final String JSON_SUBTYPES = "io.avaje.jsonb.Json.SubTypes";

  private final TypeElement baseType;
  private final List<TypeSubTypeMeta> subTypes = new ArrayList<>();

  TypeSubTypeReader(TypeElement baseType, ProcessingContext context) {
    this.baseType = baseType;
    read();
  }

  List<TypeSubTypeMeta> subTypes() {
    return subTypes;
  }

  boolean hasSubTypes() {
    return !subTypes.isEmpty();
  }

  void read() {
    for (final AnnotationMirror mirror : baseType.getAnnotationMirrors()) {
      final String annType = mirror.getAnnotationType().toString();
      if (JSON_SUBTYPE.equals(annType)) {
        readSubType(mirror);
      } else if (JSON_SUBTYPES.equals(annType)) {
        for (final Map.Entry<? extends ExecutableElement, ? extends AnnotationValue> entry :
            mirror.getElementValues().entrySet()) {
          for (final Object importType : (List<?>) entry.getValue().getValue()) {
            readSubType((AnnotationMirror) importType);
          }
        }
      }
    }
  }

  private void readSubType(AnnotationMirror mirror) {
    final TypeSubTypeMeta meta = new TypeSubTypeMeta();
    for (final Map.Entry<? extends ExecutableElement, ? extends AnnotationValue> entry :
        mirror.getElementValues().entrySet()) {
      final String key = entry.getKey().toString();
      final String val = entry.getValue().toString();
      meta.add(key, val);
    }
    // context.logError("subtype attr "+attributes);
    subTypes.add(meta);
  }
}
