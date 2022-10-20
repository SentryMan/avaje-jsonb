package io.avaje.jsonb.generator;

import io.avaje.jsonb.Json;

class NamingConvention {

  private final Json.Naming naming;
  private final Convert convert;

  static NamingConvention of(Json.Naming naming) {
    return new NamingConvention(naming);
  }

  private NamingConvention(Json.Naming naming) {
    this.naming = naming;
    this.convert = init(naming);
  }

  @Override
  public String toString() {
    return naming.toString();
  }

  Convert init(Json.Naming naming) {
    switch (naming) {
      case Match:
        return fieldName -> fieldName;
      case LowerHyphen:
        return new LowerExtra('-');
      case LowerUnderscore:
        return new LowerExtra('_');
      case LowerSpace:
        return new LowerExtra(' ');
      case UpperCamel:
        return new UpperCamel();
      case UpperUnderscore:
        return new UpperExtra('_');
      case UpperHyphen:
        return new UpperExtra('-');
      case UpperSpace:
        return new UpperExtra(' ');
    }
    throw new IllegalStateException();
  }

  public String from(String fieldName) {
    return convert.convert(fieldName);
  }

  interface Convert {
    String convert(String fieldName);
  }

  static class UpperCamel implements Convert {

    @Override
    public String convert(String name) {
      if (name.toLowerCase().equals(name)) {
        return name.toUpperCase();
      }
      final StringBuilder sb = new StringBuilder(name.length());
      boolean change = true;
      for (final char ch : name.toCharArray()) {
        if (change && Character.isLowerCase(ch)) {
          sb.append(Character.toUpperCase(ch));
          change = false;
        } else {
          sb.append(ch);
        }
      }
      return sb.toString();
    }
  }

  static class LowerExtra implements Convert {

    private final char extra;

    LowerExtra(char extra) {
      this.extra = extra;
    }

    @Override
    public String convert(String name) {
      final StringBuilder sb = new StringBuilder(name.length());
      boolean upper = false;
      for (final char ch : name.toCharArray()) {
        if (Character.isUpperCase(ch)) {
          if (!upper) {
            sb.append(extra);
          }
          sb.append(Character.toLowerCase(ch));
          upper = true;
        } else {
          sb.append(ch);
          upper = false;
        }
      }
      return sb.toString();
    }
  }

  static class UpperExtra implements Convert {

    private final char extra;

    UpperExtra(char extra) {
      this.extra = extra;
    }

    @Override
    public String convert(String name) {
      final StringBuilder sb = new StringBuilder(name.length());
      boolean upper = false;
      for (final char ch : name.toCharArray()) {
        if (Character.isUpperCase(ch)) {
          if (!upper) {
            sb.append(extra);
          }
          sb.append(ch);
          upper = true;
        } else {
          sb.append(Character.toUpperCase(ch));
          upper = false;
        }
      }
      return sb.toString();
    }
  }
}
