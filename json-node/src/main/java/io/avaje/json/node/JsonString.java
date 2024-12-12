package io.avaje.json.node;

public final /*value*/ class JsonString implements JsonNode {

  private final String value;

  public static JsonString of(String value) {
    return new JsonString(value);
  }

  private JsonString(String value) {
    this.value = value;
  }

  @Override
  public String toString() {
    return text();
  }

  @Override
  public JsonString unmodifiable() {
    return this;
  }

  @Override
  public JsonString copy() {
    return this;
  }

  @Override
  public Type type() {
    return Type.STRING;
  }

  @Override
  public String text() {
    return value;
  }

  public String value() {
    return value;
  }
}