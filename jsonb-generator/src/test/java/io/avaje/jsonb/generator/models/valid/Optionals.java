package io.avaje.jsonb.generator.models.valid;

import java.util.Optional;
import java.util.OptionalDouble;
import java.util.OptionalInt;
import java.util.OptionalLong;

import io.avaje.jsonb.Json;

@Json
public class Optionals {

  Optional<String> op;
  OptionalInt intOp;
  OptionalDouble doubleOp;
  OptionalLong longOp;
  String stringyString;

  public Optional<String> getOp() {
    return op;
  }

  public void setOp(Optional<String> op) {
    this.op = op;
  }

  public OptionalInt getIntOp() {
    return intOp;
  }

  public void setIntOp(OptionalInt intOp) {
    this.intOp = intOp;
  }

  public OptionalDouble getDoubleOp() {
    return doubleOp;
  }

  public void setDoubleOp(OptionalDouble doubleOp) {
    this.doubleOp = doubleOp;
  }

  public OptionalLong getLongOp() {
    return longOp;
  }

  public void setLongOp(OptionalLong longOp) {
    this.longOp = longOp;
  }

  public String getStringyString() {
    return stringyString;
  }

  public void setStringyString(String stringyString) {
    this.stringyString = stringyString;
  }
}
