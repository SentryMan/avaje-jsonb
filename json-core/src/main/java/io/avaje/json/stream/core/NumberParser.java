package io.avaje.json.stream.core;

import java.math.BigDecimal;
import java.math.BigInteger;

final class NumberParser {

  /** Largest mantissa that converts to double without rounding. */
  private static final long MAX_EXACT_MANTISSA = 1L << 53;
  /** Largest power of ten that is exactly representable as a double. */
  private static final int MAX_EXACT_POW_10 = 22;
  private static final int MAX_LONG_DIGITS = 18;
  static final double[] EXACT_POW_10 = {
    1e0, 1e1, 1e2, 1e3, 1e4, 1e5, 1e6, 1e7, 1e8, 1e9, 1e10,
    1e11, 1e12, 1e13, 1e14, 1e15, 1e16, 1e17, 1e18, 1e19, 1e20, 1e21, 1e22
  };

  private static boolean isTerminator(final byte b) {
    return b == ',' || b == '}' || b == ']';
  }

  static short deserializeShort(final JParser reader) {
    if (reader.currentToken() == '"') {
      final int position = reader.getCurrentIndex();
      final char[] buf = reader.readSimpleQuote();
      try {
        return parseNumberGeneric(buf, reader.getCurrentIndex() - position - 1, reader, true).shortValueExact();
      } catch (ArithmeticException ignore) {
        throw reader.newParseErrorAt("Short overflow detected", reader.getCurrentIndex() - position);
      }
    }
    final int start = reader.scanNumber();
    final int end = reader.getCurrentIndex();
    final byte[] buf = reader.buffer;
    final byte ch = buf[start];
    final int value = ch == '-'
      ? parseNegativeInt(buf, reader, start, end)
      : parsePositiveInt(buf, reader, start, end, 0);
    if (value < Short.MIN_VALUE || value > Short.MAX_VALUE) {
      throw reader.newParseErrorAt("Short overflow detected", reader.getCurrentIndex());
    }
    return (short) value;
  }

  static int deserializeInt(final JParser reader) {
    if (reader.currentToken() == '"') {
      final int position = reader.getCurrentIndex();
      final char[] buf = reader.readSimpleQuote();
      try {
        return parseNumberGeneric(buf, reader.getCurrentIndex() - position - 1, reader, true).intValueExact();
      } catch (ArithmeticException ignore) {
        throw reader.newParseErrorAt("Integer overflow detected", reader.getCurrentIndex() - position);
      }
    }
    // single pass fast path: plain integer directly followed by , ] or }
    final byte[] buf = reader.buffer;
    final int start = reader.numberStart();
    final int limit = reader.length();
    final boolean negative = buf[start] == '-';
    final int digitStart = negative ? start + 1 : start;
    final int maxEnd = Math.min(limit, digitStart + 10);
    long value = 0;
    int i = digitStart;
    int d;
    while (i < maxEnd && (d = buf[i] - '0') >= 0 && d <= 9) {
      value = value * 10 + d;
      i++;
    }
    if (i < limit && isTerminator(buf[i]) && i > digitStart && (buf[digitStart] != '0' || i == digitStart + 1)) {
      if (negative) {
        value = -value;
      }
      if (value >= Integer.MIN_VALUE && value <= Integer.MAX_VALUE) {
        reader.numberEnd(start, i);
        return (int) value;
      }
    }
    return deserializeIntSlow(reader);
  }

  private static int deserializeIntSlow(final JParser reader) {
    final int start = reader.scanNumber();
    final int end = reader.getCurrentIndex();
    final byte[] buf = reader.buffer;
    final byte ch = buf[start];
    if (ch == '-') {
      if (end > start + 2 && buf[start + 1] == '0' && buf[start + 2] >= '0' && buf[start + 2] <= '9') {
        numberException(reader, start, end, "Leading zero is not allowed");
      }
      return parseNegativeInt(buf, reader, start, end);
    } else {
      if (ch == '0' && end > start + 1 && buf[start + 1] >= '0' && buf[start + 1] <= '9') {
        numberException(reader, start, end, "Leading zero is not allowed");
      }
      return parsePositiveInt(buf, reader, start, end, 0);
    }
  }

  private static int parsePositiveInt(final byte[] buf, final JParser reader, final int start, final int end, final int offset) {
    int value = 0;
    int i = start + offset;
    if (i == end) numberException(reader, start, end, "Digit not found");
    for (; i < end; i++) {
      final int ind = buf[i] - 48;
      if (ind < 0 || ind > 9) {
        if (i > start + offset && reader.allWhitespace(i, end)) return value;
        else if (i == end - 1 && buf[i] == '.') numberException(reader, start, end, "Number ends with a dot");
        final BigDecimal v = parseNumberGeneric(reader.prepareBuffer(start, end - start), end - start, reader, false);
        if (v.scale() > 0) numberException(reader, start, end, "Expecting int but found decimal value", v);
        return v.intValue();

      }
      value = (value << 3) + (value << 1) + ind;
      if (value < 0) {
        numberException(reader, start, end, "Integer overflow detected");
      }
    }
    return value;
  }

  private static int parseNegativeInt(final byte[] buf, final JParser reader, final int start, final int end) {
    int value = 0;
    int i = start + 1;
    if (i == end) numberException(reader, start, end, "Digit not found");
    for (; i < end; i++) {
      final int ind = buf[i] - 48;
      if (ind < 0 || ind > 9) {
        if (i > start + 1 && reader.allWhitespace(i, end)) return value;
        else if (i == end - 1 && buf[i] == '.') numberException(reader, start, end, "Number ends with a dot");
        final BigDecimal v = parseNumberGeneric(reader.prepareBuffer(start, end - start), end - start, reader, false);
        if (v.scale() > 0) numberException(reader, start, end, "Expecting int but found decimal value", v);
        return v.intValue();
      }
      value = (value << 3) + (value << 1) - ind;
      if (value > 0) {
        numberException(reader, start, end, "Integer overflow detected");
      }
    }
    return value;
  }

  private static BigDecimal parseNumberGeneric(final char[] buf, final int len, final JParser reader, final boolean withQuotes) {
    int end = len;
    while (end > 0 && Character.isWhitespace(buf[end - 1])) {
      end--;
    }
    if (end > reader.maxNumberDigits) {
      throw reader.maxNumberDigitsExceeded(len, end);
    }
    final int offset = buf[0] == '-' ? 1 : 0;
    if (buf[offset] == '0' && end > offset + 1 && buf[offset + 1] >= '0' && buf[offset + 1] <= '9') {
      throw reader.newParseErrorAt("Leading zero is not allowed. Error parsing number", len + (withQuotes ? 2 : 0));
    }
    try {
      return new BigDecimal(buf, 0, end);
    } catch (NumberFormatException nfe) {
      throw reader.newParseErrorAt("Error parsing number", len + (withQuotes ? 2 : 0), nfe);
    }
  }

  static void numberException(final JParser reader, final int start, final int end, String message) {
    final int len = end - start;
    if (len > reader.maxNumberDigits) {
      throw reader.maxNumberDigitsExceeded(len, end);
    }
    throw reader.newParseErrorWith("Error parsing number", len, message, null, ". Error parsing number");
  }

  static void numberException(final JParser reader, final int start, final int end, String message, Object messageArgument) {
    final int len = end - start;
    if (len > reader.maxNumberDigits) {
      throw reader.maxNumberDigitsExceeded(len, end);
    }
    throw reader.newParseErrorWith("Error parsing number", len, message, messageArgument, ". Error parsing number");
  }


  static long deserializeLong(final JParser reader) {
    if (reader.currentToken() == '"') {
      final int position = reader.getCurrentIndex();
      final char[] buf = reader.readSimpleQuote();
      try {
        return parseNumberGeneric(buf, reader.getCurrentIndex() - position - 1, reader, true).longValueExact();
      } catch (ArithmeticException ignore) {
        throw reader.newParseErrorAt("Long overflow detected", reader.getCurrentIndex() - position);
      }
    }
    // single pass fast path: plain integer of up to 18 digits directly followed by , ] or }
    final byte[] buf = reader.buffer;
    final int start = reader.numberStart();
    final int limit = reader.length();
    final boolean negative = buf[start] == '-';
    final int digitStart = negative ? start + 1 : start;
    final int maxEnd = Math.min(limit, digitStart + MAX_LONG_DIGITS);
    long value = 0;
    int i = digitStart;
    int d;
    while (i < maxEnd && (d = buf[i] - '0') >= 0 && d <= 9) {
      value = value * 10 + d;
      i++;
    }
    if (i < limit && isTerminator(buf[i]) && i > digitStart && (buf[digitStart] != '0' || i == digitStart + 1)) {
      reader.numberEnd(start, i);
      return negative ? -value : value;
    }
    return deserializeLongSlow(reader);
  }

  private static long deserializeLongSlow(final JParser reader) {
    final int start = reader.scanNumber();
    final int end = reader.getCurrentIndex();
    final byte[] buf = reader.buffer;
    final byte ch = buf[start];
    int i = start;
    long value = 0;
    if (ch == '-') {
      i = start + 1;
      if (i == end) numberException(reader, start, end, "Digit not found");
      final boolean leadingZero = buf[i] == 48;
      for (; i < end; i++) {
        final int ind = buf[i] - 48;
        if (ind < 0 || ind > 9) {
          if (leadingZero && i > start + 2) {
            numberException(reader, start, end, "Leading zero is not allowed");
          }
          if (i > start + 1 && reader.allWhitespace(i, end)) return value;
          return parseLongGeneric(reader, start, end);
        }
        value = (value << 3) + (value << 1) - ind;
        if (value > 0) {
          numberException(reader, start, end, "Long overflow detected");
        }
      }
      if (leadingZero && i > start + 2) {
        numberException(reader, start, end, "Leading zero is not allowed");
      }
      return value;
    }
    if (i == end) numberException(reader, start, end, "Digit not found");
    final boolean leadingZero = buf[i] == 48;
    for (; i < end; i++) {
      final int ind = buf[i] - 48;
      if (ind < 0 || ind > 9) {
        if (leadingZero && i > start + 1) {
          numberException(reader, start, end, "Leading zero is not allowed");
        }
        if (ch == '+' && i > start + 1 && reader.allWhitespace(i, end)) return value;
        else if (ch != '+' && i > start && reader.allWhitespace(i, end)) return value;
        return parseLongGeneric(reader, start, end);
      }
      value = (value << 3) + (value << 1) + ind;
      if (value < 0) {
        numberException(reader, start, end, "Long overflow detected");
      }
    }
    if (leadingZero && i > start + 1) {
      numberException(reader, start, end, "Leading zero is not allowed");
    }
    return value;
  }

  private static long parseLongGeneric(final JParser reader, final int start, final int end) {
    final int len = end - start;
    final char[] buf = reader.prepareBuffer(start, len);
    if (len > 0 && buf[len - 1] == '.') numberException(reader, start, end, "Number ends with a dot");
    final BigDecimal v = parseNumberGeneric(buf, len, reader, false);
    if (v.scale() > 0) numberException(reader, start, end, "Expecting long, but found decimal value ", v);
    return v.longValue();
  }

  private static class NumberInfo {
    final char[] buffer;
    final int length;

    NumberInfo(final char[] buffer, final int length) {
      this.buffer = buffer;
      this.length = length;
    }
  }

  private static NumberInfo readLongNumber(final JParser reader, final int start) {
    int len = reader.length() - start;
    char[] result = reader.prepareBuffer(start, len);
    while (reader.length() == reader.getCurrentIndex()) {
      if (reader.isEndOfStream()) break;
      reader.scanNumber(); // peek, do not read
      int end = reader.getCurrentIndex();
      int oldLen = len;
      len += end;
      if (len > reader.maxNumberDigits) {
        throw reader.maxNumberDigitsExceeded(len, len);
      }
      char[] tmp = result;
      result = new char[len];
      System.arraycopy(tmp, 0, result, 0, oldLen);
      System.arraycopy(reader.prepareBuffer(0, end), 0, result, oldLen, end);
    }
    return new NumberInfo(result, len);
  }

  static double deserializeDouble(final JParser reader) {
    if (reader.currentToken() == '"') {
      final int position = reader.getCurrentIndex();
      final char[] buf = reader.readSimpleQuote();
      return parseDoubleGeneric(buf, reader.getCurrentIndex() - position - 1, reader, true);
    }
    final double fast = fastDouble(reader);
    if (fast == fast) {
      return fast;
    }
    final int start = reader.scanNumber();
    return parseDouble(reader, start, reader.getCurrentIndex());
  }

  /**
   * Single pass parse of a number directly followed by {@code ,}, {@code ]} or {@code }}.
   *
   * <p>Numbers of up to 18 digits are converted exactly: by one multiply or divide when the mantissa
   * fits in 53 bits and the power of ten is at most 22, otherwise by Eisel-Lemire.
   *
   * @return the parsed value, or NaN when the number needs the slow path
   */
  private static double fastDouble(final JParser reader) {
    final byte[] buf = reader.buffer;
    final int start = reader.numberStart();
    final int limit = reader.length();
    final boolean negative = buf[start] == '-';
    final int digitStart = negative ? start + 1 : start;
    int i = digitStart;
    int d;
    long mantissa = 0;
    final int intEnd = Math.min(limit, digitStart + MAX_LONG_DIGITS);
    while (i < intEnd && (d = buf[i] - '0') >= 0 && d <= 9) {
      mantissa = mantissa * 10 + d;
      i++;
    }
    final int intDigits = i - digitStart;
    if (intDigits == 0 || i >= limit || (intDigits > 1 && buf[digitStart] == '0')) {
      return Double.NaN;
    }
    byte b = buf[i];
    int exp10 = 0;
    if (b == '.') {
      final int fracStart = ++i;
      final int fracEnd = Math.min(limit, fracStart + MAX_LONG_DIGITS - intDigits);
      while (i < fracEnd && (d = buf[i] - '0') >= 0 && d <= 9) {
        mantissa = mantissa * 10 + d;
        i++;
      }
      if (i == fracStart || i >= limit) {
        return Double.NaN;
      }
      exp10 = fracStart - i;
      b = buf[i];
    }
    if (b == 'e' || b == 'E') {
      if (++i >= limit) {
        return Double.NaN;
      }
      b = buf[i];
      final boolean expNegative = b == '-';
      if ((expNegative || b == '+') && ++i >= limit) {
        return Double.NaN;
      }
      final int expStart = i;
      final int expEnd = Math.min(limit, expStart + 3);
      int exp = 0;
      while (i < expEnd && (d = buf[i] - '0') >= 0 && d <= 9) {
        exp = exp * 10 + d;
        i++;
      }
      if (i == expStart || i >= limit) {
        return Double.NaN;
      }
      exp10 += expNegative ? -exp : exp;
      b = buf[i];
    }
    if (!isTerminator(b)) {
      return Double.NaN;
    }
    final double value;
    if (mantissa == 0) {
      value = 0;
    } else if (mantissa <= MAX_EXACT_MANTISSA && exp10 >= -MAX_EXACT_POW_10 && exp10 <= MAX_EXACT_POW_10) {
      // Both values are exact doubles here, so a single multiply or divide gives the correctly rounded result
      value = exp10 >= 0 ? mantissa * EXACT_POW_10[exp10] : mantissa / EXACT_POW_10[-exp10];
    } else {
      value = EiselLemire.toDouble(mantissa, exp10);
      if (value != value) {
        return Double.NaN;
      }
    }
    reader.numberEnd(start, i);
    return negative ? -value : value;
  }

  private static double parseDouble(final JParser reader, final int start, final int end) {
    final int len = end - start;
    if (len > MAX_LONG_DIGITS && end == reader.length()) {
      final NumberInfo tmp = readLongNumber(reader, start);
      return parseDoubleGeneric(tmp.buffer, tmp.length, reader, false);
    }
    if (len > reader.maxNumberDigits) {
      throw reader.maxNumberDigitsExceeded(len, len);
    }
    return reader.ryuParser().parseDouble(reader, reader.buffer, start, end);
  }

  private static double parseDoubleGeneric(final char[] buf, final int len, final JParser reader, final boolean withQuotes) {
    int end = len;
    while (end > 0 && Character.isWhitespace(buf[end - 1])) {
      end--;
    }
    if (end > reader.maxNumberDigits) {
      throw reader.maxNumberDigitsExceeded(len, end);
    }
    final int offset = buf[0] == '-' ? 1 : 0;
    if (buf[offset] == '0' && end > offset + 1 && buf[offset + 1] >= '0' && buf[offset + 1] <= '9') {
      throw reader.newParseErrorAt("Leading zero is not allowed. Error parsing number", len + (withQuotes ? 2 : 0));
    }
    try {
      return Double.parseDouble(new String(buf, 0, end));
    } catch (NumberFormatException nfe) {
      throw reader.newParseErrorAt("Error parsing number", len + (withQuotes ? 2 : 0), nfe);
    }
  }

  static BigDecimal deserializeDecimal(final JParser reader) {
    if (reader.currentToken() == '"') {
      final int len = reader.parseString();
      return parseNumberGeneric(reader.chars, len, reader, true);
    }
    final int start = reader.scanNumber();
    int end = reader.getCurrentIndex();
    if (end == reader.length()) {
      NumberInfo info = readLongNumber(reader, start);
      return parseNumberGeneric(info.buffer, info.length, reader, false);
    }
    int len = end - start;
    if (len > 18) {
      return parseNumberGeneric(reader.prepareBuffer(start, len), len, reader, false);
    }
    final byte[] buf = reader.buffer;
    final byte ch = buf[start];
    if (ch == '-') {
      return parseNegativeDecimal(buf, reader, start, end);
    }
    return parsePositiveDecimal(buf, reader, start, end);
  }

  private static BigDecimal parsePositiveDecimal(final byte[] buf, final JParser reader, final int start, final int end) {
    long value = 0;
    byte ch = ' ';
    int i = start;
    final boolean leadingZero = buf[start] == 48;
    for (; i < end; i++) {
      ch = buf[i];
      if (ch == '.' || ch == 'e' || ch == 'E') break;
      final int ind = ch - 48;
      if (ind < 0 || ind > 9) {
        if (leadingZero && i > start + 1) {
          numberException(reader, start, end, "Leading zero is not allowed");
        }
        if (i > start && reader.allWhitespace(i, end)) return BigDecimal.valueOf(value);
        numberException(reader, start, end, "Unknown digit", (char) ch);
      }
      value = (value << 3) + (value << 1) + ind;
    }
    if (i == start) numberException(reader, start, end, "Digit not found");
    else if (leadingZero && ch != '.' && i > start + 1)
      numberException(reader, start, end, "Leading zero is not allowed");
    else if (i == end) return BigDecimal.valueOf(value);
    else if (ch == '.') {
      i++;
      if (i == end) numberException(reader, start, end, "Number ends with a dot");
      int dp = i;
      for (; i < end; i++) {
        ch = buf[i];
        if (ch == 'e' || ch == 'E') break;
        final int ind = ch - 48;
        if (ind < 0 || ind > 9) {
          if (reader.allWhitespace(i, end)) return BigDecimal.valueOf(value, i - dp);
          numberException(reader, start, end, "Unknown digit", (char) ch);
        }
        value = (value << 3) + (value << 1) + ind;
      }
      if (i == end) return BigDecimal.valueOf(value, end - dp);
      else if (ch == 'e' || ch == 'E') {
        final int ep = i;
        i++;
        ch = buf[i];
        final int exp;
        if (ch == '-') {
          exp = parseNegativeInt(buf, reader, i, end);
        } else if (ch == '+') {
          exp = parsePositiveInt(buf, reader, i, end, 1);
        } else {
          exp = parsePositiveInt(buf, reader, i, end, 0);
        }
        return BigDecimal.valueOf(value, ep - dp - exp);
      }
      return BigDecimal.valueOf(value, end - dp);
    } else if (ch == 'e' || ch == 'E') {
      i++;
      ch = buf[i];
      final int exp;
      if (ch == '-') {
        exp = parseNegativeInt(buf, reader, i, end);
      } else if (ch == '+') {
        exp = parsePositiveInt(buf, reader, i, end, 1);
      } else {
        exp = parsePositiveInt(buf, reader, i, end, 0);
      }
      return BigDecimal.valueOf(value, -exp);
    }
    return BigDecimal.valueOf(value);
  }

  private static BigDecimal parseNegativeDecimal(final byte[] buf, final JParser reader, final int start, final int end) {
    long value = 0;
    byte ch = ' ';
    int i = start + 1;
    final boolean leadingZero = buf[start + 1] == 48;
    for (; i < end; i++) {
      ch = buf[i];
      if (ch == '.' || ch == 'e' || ch == 'E') break;
      final int ind = ch - 48;
      if (ind < 0 || ind > 9) {
        if (leadingZero && i > start + 2) {
          numberException(reader, start, end, "Leading zero is not allowed");
        }
        if (i > start + 1 && reader.allWhitespace(i, end)) return BigDecimal.valueOf(value);
        numberException(reader, start, end, "Unknown digit", (char) ch);
      }
      value = (value << 3) + (value << 1) - ind;
    }
    if (i == start + 1) numberException(reader, start, end, "Digit not found");
    else if (leadingZero && ch != '.' && i > start + 2)
      numberException(reader, start, end, "Leading zero is not allowed");
    else if (i == end) return BigDecimal.valueOf(value);
    else if (ch == '.') {
      i++;
      if (i == end) numberException(reader, start, end, "Number ends with a dot");
      int dp = i;
      for (; i < end; i++) {
        ch = buf[i];
        if (ch == 'e' || ch == 'E') break;
        final int ind = ch - 48;
        if (ind < 0 || ind > 9) {
          if (reader.allWhitespace(i, end)) return BigDecimal.valueOf(value, i - dp);
          numberException(reader, start, end, "Unknown digit", (char) ch);
        }
        value = (value << 3) + (value << 1) - ind;
      }
      if (i == end) return BigDecimal.valueOf(value, end - dp);
      else if (ch == 'e' || ch == 'E') {
        final int ep = i;
        i++;
        ch = buf[i];
        final int exp;
        if (ch == '-') {
          exp = parseNegativeInt(buf, reader, i, end);
        } else if (ch == '+') {
          exp = parsePositiveInt(buf, reader, i, end, 1);
        } else {
          exp = parsePositiveInt(buf, reader, i, end, 0);
        }
        return BigDecimal.valueOf(value, ep - dp - exp);
      }
      return BigDecimal.valueOf(value, end - dp);
    } else if (ch == 'e' || ch == 'E') {
      i++;
      ch = buf[i];
      final int exp;
      if (ch == '-') {
        exp = parseNegativeInt(buf, reader, i, end);
      } else if (ch == '+') {
        exp = parsePositiveInt(buf, reader, i, end, 1);
      } else {
        exp = parsePositiveInt(buf, reader, i, end, 0);
      }
      return BigDecimal.valueOf(value, -exp);
    }
    return BigDecimal.valueOf(value);
  }


  private static BigInteger parseBigIntGeneric(char[] buf, int len, JParser reader) {
    int end;
    for (end = len; end > 0 && Character.isWhitespace(buf[end - 1]); --end) {
      // do nothing
    }
    if (end > reader.maxNumberDigits) {
      throw reader.maxNumberDigitsExceeded(len, end);
    } else {
      try {
        return new BigInteger(new String(buf, 0, end));
      } catch (NumberFormatException var5) {
        throw reader.newParseErrorAt("Error parsing number", len, var5);
      }
    }
  }

  static BigInteger deserializeBigInt(JParser reader) {
    int start;
    if (reader.currentToken() == 34) {
      start = reader.parseString();
      return parseBigIntGeneric(reader.chars, start, reader);
    } else {
      start = reader.scanNumber();
      int end = reader.getCurrentIndex();
      int len = end - start;
      if (len > 18) {
        end = reader.findNonWhitespace(end);
        len = end - start;
        if (end == reader.length()) {
          NumberInfo info = readLongNumber(reader, start);
          return parseBigIntGeneric(info.buffer, info.length, reader);
        }

        if (len > 18) {
          return parseBigIntGeneric(reader.prepareBuffer(start, len), len, reader);
        }
      }

      byte[] buf = reader.buffer;
      byte ch = buf[start];
      int i = start;
      long value = 0L;
      int ind;
      if (ch == 45) {
        i = start + 1;
        if (i == end) {
          numberException(reader, start, end, "Digit not found");
        }

        while (i < end) {
          ind = buf[i] - 48;
          if (ind < 0 || ind > 9) {
            if (i > start + 1 && reader.allWhitespace(i, end)) {
              return BigInteger.valueOf(value);
            }

            numberException(reader, start, end, "Unknown digit", (char) ch);
          }

          value = (value << 3) + (value << 1) - (long) ind;
          ++i;
        }

        return BigInteger.valueOf(value);
      } else {
        if (start == end) {
          numberException(reader, start, end, "Digit not found");
        }

        while (i < end) {
          ind = buf[i] - 48;
          if (ind < 0 || ind > 9) {
            if (ch == 43 && i > start + 1 && reader.allWhitespace(i, end)) {
              return BigInteger.valueOf(value);
            }

            if (ch != 43 && i > start && reader.allWhitespace(i, end)) {
              return BigInteger.valueOf(value);
            }

            numberException(reader, start, end, "Unknown digit", (char) ch);
          }

          value = (value << 3) + (value << 1) + (long) ind;
          ++i;
        }

        return BigInteger.valueOf(value);
      }
    }
  }

}
