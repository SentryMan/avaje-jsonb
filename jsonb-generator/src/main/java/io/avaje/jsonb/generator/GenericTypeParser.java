package io.avaje.jsonb.generator;

import java.util.Stack;

class GenericTypeParser {

  private final String raw;

  private StringBuilder buf = new StringBuilder();

  private final Stack<GenericType> stack = new Stack<>();

  GenericTypeParser(String raw) {
    this.raw = raw;
    stack.push(new GenericType(raw));
  }

  GenericType parse() {
    final int len = raw.length();
    for (int i = 0; i < len; i++) {
      final char ch = raw.charAt(i);
      processChar(ch);
    }

    return stack.peek();
  }

  private void processChar(char ch) {
    switch (ch) {
      case '<':
        startParam();
        break;
      case '>':
        endParam();
        break;
      case ',':
        nextParam();
        break;
      default:
        buf.append(ch);
    }
  }

  private void nextParam() {
    endParam();

    final GenericType param = new GenericType();
    stack.peek().addParam(param);
    stack.push(param);
  }

  private void endParam() {
    final String content = buf.toString();
    if (!content.isEmpty()) {
      stack.peek().setMainType(content);
      buf = new StringBuilder();
    }
    stack.pop();
  }

  private void startParam() {
    stack.peek().setMainType(buf.toString());
    buf = new StringBuilder();

    final GenericType param = new GenericType();
    stack.peek().addParam(param);
    stack.push(param);
  }
}
