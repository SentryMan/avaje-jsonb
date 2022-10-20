package io.avaje.jsonb.generator;

import java.util.Map;
import javax.lang.model.element.AnnotationMirror;
import javax.lang.model.element.AnnotationValue;
import javax.lang.model.element.Element;
import javax.lang.model.element.ExecutableElement;

class PropertyIgnoreReader {

  private static final String JSON_IGNORE = "io.avaje.jsonb.Json.Ignore";
  private static final String JSON_UNMAPPED = "io.avaje.jsonb.Json.Unmapped";
  private static final String JSON_RAW = "io.avaje.jsonb.Json.Raw";

  private boolean unmapped;
  private boolean raw;
  private boolean ignoreSerialize;
  private boolean ignoreDeserialize;

  PropertyIgnoreReader(Element element) {
    read(element);
  }

  boolean unmapped() {
    return unmapped;
  }

  boolean raw() {
    return raw;
  }

  boolean serialize() {
    return !ignoreSerialize;
  }

  boolean deserialize() {
    return !ignoreDeserialize;
  }

  /** Read the Json.Property annotation using annotation mirrors. */
  void read(Element element) {
    for (final AnnotationMirror mirror : element.getAnnotationMirrors()) {
      if (JSON_UNMAPPED.equals(mirror.getAnnotationType().toString())) {
        unmapped = true;
      } else if (JSON_RAW.equals(mirror.getAnnotationType().toString())) {
        raw = true;
      } else if (JSON_IGNORE.equals(mirror.getAnnotationType().toString())) {
        ignoreDeserialize = true;
        ignoreSerialize = true;
        for (final Map.Entry<? extends ExecutableElement, ? extends AnnotationValue> entry :
            mirror.getElementValues().entrySet()) {
          final String key = entry.getKey().toString();
          final String value = entry.getValue().toString();
          if ("deserialize()".equals(key)) {
            ignoreDeserialize = "false".equals(value);
          } else if ("serialize()".equals(key)) {
            ignoreSerialize = "false".equals(value);
          } else {
            throw new IllegalStateException("Unknown attribute on @Json.Ignore " + key);
          }
        }
      }
    }
  }
}
