package org.example.customer.alias;

import io.avaje.jsonb.Json;

@Json
public record WithAlias(int id, @Json.Alias({"something", "something2"}) String alias, String message) {

}
