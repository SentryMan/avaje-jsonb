package org.example;

import static org.assertj.core.api.Assertions.assertThat;

import io.avaje.jsonb.JsonType;
import io.avaje.jsonb.Jsonb;
import java.util.List;
import org.junit.jupiter.api.Test;

class MyIntEnumTest {

  Jsonb jsonb = Jsonb.builder().build();

  @Test
  void toJson_withJsonValue_expect_valueInJson() {

    JsonType<MyIntEnum> type = jsonb.type(MyIntEnum.class);

    assertThat(type.toJson(MyIntEnum.ONE)).isEqualTo("97");
    assertThat(type.toJson(MyIntEnum.TWO)).isEqualTo("98");
  }

  @Test
  void fromJson() {

    JsonType<List<MyIntEnum>> type = jsonb.type(MyIntEnum.class).list();

    List<MyIntEnum> myEnums = type.fromJson("[97, 98]");
    assertThat(myEnums).hasSize(2);
    assertThat(myEnums.get(0)).isEqualTo(MyIntEnum.ONE);
    assertThat(myEnums.get(1)).isEqualTo(MyIntEnum.TWO);
  }
}
