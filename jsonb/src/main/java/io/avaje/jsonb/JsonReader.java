/*
 * Copyright (C) 2014 Square, Inc.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *    https://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package io.avaje.jsonb;

import java.io.Closeable;
import java.io.IOException;

public interface JsonReader extends Closeable {

  void beginArray() throws IOException;

  void endArray();

  boolean hasNextElement() throws IOException;

  void beginObject() throws IOException;

  void endObject() throws IOException;

  boolean hasNextField() throws IOException;

  String nextField() throws IOException;

  boolean nextBoolean() throws IOException;

  int nextInt() throws IOException;

  long nextLong() throws IOException;

  double nextDouble() throws IOException;

  String nextString() throws IOException;

  boolean peekIsNull();

  <T> T nextNull();

  String path();

  /**
   * Return the current Token.
   */
  Token peek() throws IOException;

  /**
   * Close the resources of the reader.
   */
  void close() throws IOException;

  /**
   * Skip the next value.
   */
  void skipValue() throws IOException;

  /**
   * Reading json with an unmapped field, throw an Exception if failOnUnmapped is true.
   */
  void unmappedField(String fieldName);

  /**
   * A structure, name, or value type in a JSON-encoded string.
   */
  enum Token {

    /**
     * The opening of a JSON array. Written using {@link JsonWriter#beginArray} and read using
     * {@link JsonReader#beginArray}.
     */
    BEGIN_ARRAY,

//    /**
//     * The closing of a JSON array. Written using {@link JsonWriter#endArray} and read using {@link
//     * JsonReader#endArray}.
//     */
//    END_ARRAY,

    /**
     * The opening of a JSON object. Written using {@link JsonWriter#beginObject} and read using
     * {@link JsonReader#beginObject}.
     */
    BEGIN_OBJECT,

//    /**
//     * The closing of a JSON object. Written using {@link JsonWriter#endObject} and read using
//     * {@link JsonReader#endObject}.
//     */
//    END_OBJECT,
//
//    /**
//     * A JSON property name. Within objects, tokens alternate between names and their values.
//     * Written using {@link JsonWriter#name} and read using {@link JsonReader#nextField()}
//     */
//    NAME,

    /**
     * A JSON string.
     */
    STRING,

    /**
     * A JSON number represented in this API by a Java {@code double}, {@code long}, or {@code int}.
     */
    NUMBER,

    /**
     * A JSON {@code true} or {@code false}.
     */
    BOOLEAN,

    /**
     * A JSON {@code null}.
     */
    NULL,

//    /**
//     * The end of the JSON stream. This sentinel value is returned by {@link JsonReader#peek()} to
//     * signal that the JSON-encoded value has no more tokens.
//     */
//    END_DOCUMENT
  }
}