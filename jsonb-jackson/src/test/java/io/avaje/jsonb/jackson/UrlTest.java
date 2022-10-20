package io.avaje.jsonb.jackson;

import static org.assertj.core.api.Assertions.assertThat;

import io.avaje.jsonb.JsonType;
import io.avaje.jsonb.Jsonb;
import java.io.IOException;
import java.net.URI;
import java.net.URL;
import org.junit.jupiter.api.Test;

class UrlTest {

  Jsonb jsonb = Jsonb.builder().build();

  @Test
  void url() throws IOException {

    JsonType<URL> type = jsonb.type(URL.class);
    String asJson = type.toJson(new URL("https://foo.com"));
    assertThat(asJson).isEqualTo("\"https://foo.com\"");

    URL fromJson = type.list().fromJson("[\"https://foo.com\"]").get(0);
    assertThat(fromJson).isEqualTo(new URL("https://foo.com"));
  }

  @Test
  void uri() throws IOException {

    JsonType<URI> type = jsonb.type(URI.class);
    String asJson = type.toJson(URI.create("https://foo.com"));
    assertThat(asJson).isEqualTo("\"https://foo.com\"");

    URI fromJson = type.list().fromJson("[\"https://foo.com\"]").get(0);
    assertThat(fromJson).isEqualTo(URI.create("https://foo.com"));
  }
}
