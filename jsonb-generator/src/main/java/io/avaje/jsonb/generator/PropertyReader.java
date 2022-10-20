package io.avaje.jsonb.generator;

import java.util.Map;
import javax.lang.model.element.AnnotationMirror;
import javax.lang.model.element.AnnotationValue;
import javax.lang.model.element.Element;
import javax.lang.model.element.ExecutableElement;

class PropertyReader {

  private static final String JSON_PROPERTY = "io.avaje.jsonb.Json.Property";

  static String name(NamingConvention namingConvention, String fieldName, Element element) {
    final String name = propertyAttribute(element);
    return name != null ? name : namingConvention.from(fieldName);
  }

  /** Read the Json.Property annotation using annotation mirrors. */
  static String propertyAttribute(Element element) {
    for (final AnnotationMirror mirror : element.getAnnotationMirrors()) {
      if (JSON_PROPERTY.equals(mirror.getAnnotationType().toString())) {
        for (final Map.Entry<? extends ExecutableElement, ? extends AnnotationValue> entry :
            mirror.getElementValues().entrySet()) {
          return Util.trimQuotes(entry.getValue().toString());
        }
      }
    }
    return null;
  }
}
