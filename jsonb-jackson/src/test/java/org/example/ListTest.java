package org.example;

import static org.assertj.core.api.Assertions.assertThat;

import io.avaje.jsonb.JsonType;
import io.avaje.jsonb.Jsonb;
import io.avaje.jsonb.Types;
import java.io.IOException;
import java.lang.reflect.Type;
import java.util.Arrays;
import java.util.List;
import org.junit.jupiter.api.Test;

class ListTest {

  @Test
  void simpleList() throws IOException {

    Jsonb jsonb = Jsonb.builder().build();

    Type listOfString = Types.listOf(String.class);
    JsonType<List<String>> listOfStringType = jsonb.type(listOfString);

    List<String> data = Arrays.asList("one", "two", "three");
    String asJson = listOfStringType.toJson(data);

    assertThat(asJson).isEqualTo("[\"one\",\"two\",\"three\"]");

    List<String> fromJson = listOfStringType.fromJson(asJson);
    assertThat(fromJson).contains("one", "two", "three");
  }

  @Test
  void adapter_list() throws IOException {

    Jsonb jsonb = Jsonb.builder().build();

    JsonType<String> stringType = jsonb.type(String.class);
    JsonType<List<String>> list = stringType.list();

    List<String> data = Arrays.asList("one", "two", "three");
    String asJson = list.toJson(data);

    assertThat(asJson).isEqualTo("[\"one\",\"two\",\"three\"]");

    List<String> fromJson = list.fromJson(asJson);
    assertThat(fromJson).contains("one", "two", "three");
  }
}
