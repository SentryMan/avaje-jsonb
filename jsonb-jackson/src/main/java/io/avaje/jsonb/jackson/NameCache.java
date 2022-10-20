package io.avaje.jsonb.jackson;

import com.fasterxml.jackson.core.io.SerializedString;
import java.util.concurrent.ConcurrentHashMap;

final class NameCache {

  private final ConcurrentHashMap<String, SerializedString> keys = new ConcurrentHashMap<>();

  SerializedString get(String name) {
    return keys.computeIfAbsent(
        name,
        _name -> {
          final SerializedString val = new SerializedString(_name);
          val.asQuotedUTF8();
          return val;
        });
  }
}
