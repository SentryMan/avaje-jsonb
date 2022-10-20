package io.avaje.jsonb.jackson;

import static org.assertj.core.api.Assertions.assertThat;

import com.fasterxml.jackson.core.io.SerializedString;
import org.junit.jupiter.api.Test;

class NameCacheTest {

  @Test
  void get_whenSameKey_expectSameInstance() {

    NameCache cache = new NameCache();
    SerializedString name0 = cache.get("name");
    SerializedString name1 = cache.get("name");

    assertThat(name0).isSameAs(name1);
  }

  @Test
  void get_whenDiffKey() {

    NameCache cache = new NameCache();
    SerializedString name0 = cache.get("name");
    SerializedString name1 = cache.get("notName");

    assertThat(name0).isNotSameAs(name1);
  }
}
