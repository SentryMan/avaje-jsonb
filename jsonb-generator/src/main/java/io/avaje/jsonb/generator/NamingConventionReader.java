package io.avaje.jsonb.generator;

import io.avaje.jsonb.Json;
import java.util.Map;
import javax.lang.model.element.AnnotationMirror;
import javax.lang.model.element.AnnotationValue;
import javax.lang.model.element.ExecutableElement;
import javax.lang.model.element.TypeElement;

class NamingConventionReader {

  private static final String JSON_ANNOTATION = "io.avaje.jsonb.Json";
  private static final String NAMING_ATTRIBUTE = "naming()";
  private static final String TYPEPROPERTY_ATTRIBUTE = "typeProperty()";

  private String typeProperty;
  private NamingConvention namingConvention;

  NamingConventionReader(TypeElement element) {
    for (final AnnotationMirror mirror : element.getAnnotationMirrors()) {
      if (JSON_ANNOTATION.equals(mirror.getAnnotationType().toString())) {
        for (final Map.Entry<? extends ExecutableElement, ? extends AnnotationValue> entry :
            mirror.getElementValues().entrySet()) {
          if (NAMING_ATTRIBUTE.equals(entry.getKey().toString())) {
            namingConvention = NamingConvention.of(naming(entry.getValue().toString()));
          } else if (TYPEPROPERTY_ATTRIBUTE.equals(entry.getKey().toString())) {
            typeProperty = Util.trimQuotes(entry.getValue().toString());
          }
        }
      }
    }
  }

  static Json.Naming naming(String entry) {
    final int pos = entry.lastIndexOf('.');
    if (pos > -1) {
      entry = entry.substring(pos + 1);
    }
    return Json.Naming.valueOf(entry);
  }

  NamingConvention get() {
    return namingConvention != null ? namingConvention : NamingConvention.of(Json.Naming.Match);
  }

  String typeProperty() {
    return typeProperty != null ? typeProperty : "@type";
  }
}
