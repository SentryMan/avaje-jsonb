package io.avaje.jsonb.stream;

import static org.assertj.core.api.Assertions.assertThat;

import java.nio.charset.StandardCharsets;
import org.junit.jupiter.api.Test;

class EscapeTest {

  @Test
  void hello() {
    assertThat(asString(Escape.quoteEscape("Hello"))).isEqualTo("\"Hello\"");
  }

  @Test
  void quote() {
    assertThat(asString(Escape.quoteEscape("H\"ello"))).isEqualTo("\"H\\\"ello\"");
  }

  @Test
  void escape() {
    assertThat(asString(Escape.quoteEscape("a\\z"))).isEqualTo("\"a\\\\z\"");
    assertThat(asString(Escape.quoteEscape("a\nz"))).isEqualTo("\"a\\nz\"");
    assertThat(asString(Escape.quoteEscape("a\tz"))).isEqualTo("\"a\\tz\"");
    assertThat(asString(Escape.quoteEscape("a\bz"))).isEqualTo("\"a\\bz\"");
  }

  private String asString(byte[] helloBytes) {
    return new String(helloBytes, 0, helloBytes.length, StandardCharsets.UTF_8);
  }
}
