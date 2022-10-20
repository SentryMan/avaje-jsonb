package io.avaje.jsonb.stream;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import io.avaje.jsonb.JsonReader;
import io.avaje.jsonb.JsonWriter;
import io.avaje.jsonb.spi.BufferedJsonWriter;
import java.io.ByteArrayOutputStream;
import java.io.StringWriter;
import org.junit.jupiter.api.Test;

class DieselAdapterTest {

  final JsonStream adapter =
      JsonStream.builder().serializeNulls(true).serializeEmpty(true).failOnUnknown(false).build();

  @Test
  void readArray() {
    try (JsonReader reader =
        adapter.reader("{\"a\":\"hi\",\"b\":[\"zz\",\"xx\",\"yy\"], \"c\":\"bye\"}")) {
      reader.beginObject();
      assertTrue(reader.hasNextField());
      assertEquals("a", reader.nextField());
      assertEquals("hi", reader.readString());
      assertTrue(reader.hasNextField());
      assertEquals("b", reader.nextField());
      reader.beginArray();
      assertTrue(reader.hasNextElement());
      assertEquals("zz", reader.readString());
      assertTrue(reader.hasNextElement());
      assertEquals("xx", reader.readString());
      assertTrue(reader.hasNextElement());
      assertEquals("yy", reader.readString());
      assertFalse(reader.hasNextElement());
      assertTrue(reader.hasNextField());
      assertEquals("c", reader.nextField());
      assertEquals("bye", reader.readString());
      assertFalse(reader.hasNextField());
      reader.endObject();
    }
  }

  @Test
  void write_usingBufferedWriter() {
    final BufferedJsonWriter jw0 = adapter.bufferedWriter();
    writeHello(jw0, "hello");
    assertThat(jw0.result()).isEqualTo("{\"one\":\"hello\"}");

    final BufferedJsonWriter jw1 = adapter.bufferedWriter();
    writeHello(jw1, "hi");
    assertThat(jw1.result()).isEqualTo("{\"one\":\"hi\"}");
  }

  @Test
  void write_to_writer() {
    final StringWriter sw = new StringWriter();
    try (JsonWriter jw0 = adapter.writer(sw)) {
      writeHello(jw0, "hello");
    }
    assertThat(sw.toString()).isEqualTo("{\"one\":\"hello\"}");

    final StringWriter sw1 = new StringWriter();
    try (JsonWriter jw1 = adapter.writer(sw1)) {
      writeHello(jw1, "hi");
    }
    assertThat(sw1.toString()).isEqualTo("{\"one\":\"hi\"}");
  }

  @Test
  void write_to_OutputStream() {
    final ByteArrayOutputStream os = new ByteArrayOutputStream();
    try (JsonWriter jw0 = adapter.writer(os)) {
      writeHello(jw0, "hello");
    }
    assertThat(os.toString()).isEqualTo("{\"one\":\"hello\"}");

    final ByteArrayOutputStream os1 = new ByteArrayOutputStream();
    try (JsonWriter jw1 = adapter.writer(os1)) {
      writeHello(jw1, "hi");
    }
    assertThat(os1.toString()).isEqualTo("{\"one\":\"hi\"}");
  }

  private void writeHello(JsonWriter jw, String message) {
    jw.beginObject();
    jw.name("one");
    jw.value(message);
    jw.endObject();
  }
}
