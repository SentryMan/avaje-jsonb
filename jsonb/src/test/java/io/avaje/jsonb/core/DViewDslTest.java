package io.avaje.jsonb.core;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

class DViewDslTest {

  @Test
  void parse() {
    final ViewDsl dsl = new ViewDslParser().parse("id,name");
    assertTrue(dsl.contains("id"));
    assertTrue(dsl.contains("name"));
  }

  @Test
  void parse_withExtraBrace() {
    final ViewDsl dsl = new ViewDslParser().parse("(id,name)");
    assertTrue(dsl.contains("id"));
    assertTrue(dsl.contains("name"));
  }

  @Test
  void parse2() {
    final ViewDsl dsl = new ViewDslParser().parse("id,name,billingAddress(*)");
    assertTrue(dsl.contains("id"));
    assertTrue(dsl.contains("name"));
    assertTrue(dsl.contains("billingAddress"));
    dsl.push("billingAddress");
    assertTrue(dsl.contains("*"));
    assertTrue(dsl.contains("anyThingWillMatch"));
    dsl.pop();
    assertTrue(dsl.contains("name"));
  }

  @Test
  void parse_whiteSpace() {
    final ViewDsl dsl = new ViewDslParser().parse("id , name ,  billingAddress (  * )  ");
    assertTrue(dsl.contains("id"));
    assertTrue(dsl.contains("name"));
    assertTrue(dsl.contains("billingAddress"));
    dsl.push("billingAddress");
    assertTrue(dsl.contains("*"));
    assertTrue(dsl.contains("anyThingWillMatch"));
    dsl.pop();
    assertTrue(dsl.contains("name"));
  }

  @Test
  void parse_nestedWithWildCard() {
    final ViewDsl dsl = new ViewDslParser().parse("id,name,billingAddress(*),contacts(lastName)");
    assertTrue(dsl.contains("id"));
    assertTrue(dsl.contains("name"));
    assertTrue(dsl.contains("billingAddress"));
    dsl.push("billingAddress");
    assertTrue(dsl.contains("*"));
    assertTrue(dsl.contains("name"));
    assertTrue(dsl.contains("anyThingWillMatch"));
    dsl.pop();
    dsl.push("contacts");
    assertTrue(dsl.contains("lastName"));
    assertFalse(dsl.contains("firstName"));
    dsl.pop();
    assertTrue(dsl.contains("name"));
  }
}
