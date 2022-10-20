package io.avaje.jsonb.generator;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import javax.lang.model.element.AnnotationMirror;
import javax.lang.model.element.AnnotationValue;
import javax.lang.model.element.Element;
import javax.lang.model.element.ExecutableElement;

class ImportReader {

  private static final String JSON_IMPORT = "io.avaje.jsonb.Json.Import";

  /** Read the Json.Import annotation using annotation mirrors. */
  List<String> read(Element element) {
    final List<String> fullNames = new ArrayList<>();

    final List<? extends AnnotationMirror> annotationMirrors = element.getAnnotationMirrors();
    for (final AnnotationMirror mirror : annotationMirrors) {
      if (JSON_IMPORT.equals(mirror.getAnnotationType().toString())) {
        for (final Map.Entry<? extends ExecutableElement, ? extends AnnotationValue> entry :
            mirror.getElementValues().entrySet()) {
          for (final Object importType : (List<?>) entry.getValue().getValue()) {
            fullNames.add(Util.trimClassSuffix(importType.toString()));
          }
        }
      }
    }
    return fullNames;
  }
}
