/*
 * Copyright (C) 2016 Square, Inc.
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

/**
 * Thrown when the data being parsed is not encoded as valid JSON,
 * method invocation fails for json views, or and underlying IOException occurs.
 */
public class JsonException extends RuntimeException {

  public JsonException(String message) {
    super(message);
  }

  public JsonException(Throwable exception) {
    super(exception);
  }

  public JsonException(String message, Throwable cause) {
    super(message, cause);
  }

  public static JsonException of(Throwable e) {
    if (e instanceof JsonException) {
      return (JsonException) e;
    }
    return new JsonException(e);
  }
}