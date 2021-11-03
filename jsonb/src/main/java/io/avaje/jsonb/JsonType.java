package io.avaje.jsonb;

import java.io.*;
import java.util.List;

/**
 * Provides API to serialise a type to and from JSON.
 * <p>
 * JsonType does not exist in Moshi and has been added to provide a
 * slightly easier API to use than JsonAdapter.
 */
public interface JsonType<T> {

  /**
   * Return the list type for this JsonType.
   */
  JsonType<List<T>> list();

  String toJson(T value) throws IOException;

  void toJson(JsonWriter writer, T value) throws IOException;

  void toJson(Writer writer, T value) throws IOException;

  void toJson(OutputStream outputStream, T value) throws IOException;

  T fromJson(JsonReader reader) throws IOException;

  T fromJson(String content) throws IOException;

  T fromJson(Reader reader) throws IOException;

  T fromJson(InputStream inputStream) throws IOException;

}
