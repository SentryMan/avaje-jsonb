// Copyright 2018 Ulf Adams
//
// Licensed under the Apache License, Version 2.0 (the "License");
// you may not use this file except in compliance with the License.
// You may obtain a copy of the License at
//
//     http://www.apache.org/licenses/LICENSE-2.0
//
// Unless required by applicable law or agreed to in writing, software
// distributed under the License is distributed on an "AS IS" BASIS,
// WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
// See the License for the specific language governing permissions and
// limitations under the License.
//
// Modified by Rikard Pavelic (dsl-json), adapted for avaje-json-core.

package io.avaje.json.stream.core;

import java.math.BigInteger;

/**
 * Shortest round-trip double to decimal conversion (Ryu), written directly into a byte buffer.
 *
 * <p>Uses the same layout as the previous Grisu3 based writer: plain decimal notation when the
 * decimal point position is in {@code [-5, 21]} (integers get a {@code .0} suffix), otherwise
 * {@code 1.5E+22} / {@code 1E-7} style exponential notation.
 */
final class RyuDouble {

  private static final int DOUBLE_MANTISSA_BITS = 52;
  private static final long DOUBLE_MANTISSA_MASK = (1L << DOUBLE_MANTISSA_BITS) - 1;
  private static final int DOUBLE_EXPONENT_BITS = 11;
  private static final int DOUBLE_EXPONENT_MASK = (1 << DOUBLE_EXPONENT_BITS) - 1;
  private static final int DOUBLE_EXPONENT_BIAS = (1 << (DOUBLE_EXPONENT_BITS - 1)) - 1;

  private static final int POS_TABLE_SIZE = 326;
  private static final int NEG_TABLE_SIZE = 291;

  private static final int POW5_BITCOUNT = 121; // max 3*31 = 124
  private static final int POW5_QUARTER_BITCOUNT = 31;
  private static final int[][] POW5_SPLIT = new int[POS_TABLE_SIZE][4];

  private static final int POW5_INV_BITCOUNT = 122; // max 3*31 = 124
  private static final int POW5_INV_QUARTER_BITCOUNT = 31;
  private static final int[][] POW5_INV_SPLIT = new int[NEG_TABLE_SIZE][4];

  private static final long MAX_EXACT_LONG = 1L << 53;

  /** Most bytes one value can take, the worst case being a minus sign, "0.", five leading zeros and 17 digits. */
  static final int MAX_LENGTH = 25;

  static {
    final var mask = BigInteger.ONE.shiftLeft(POW5_QUARTER_BITCOUNT).subtract(BigInteger.ONE);
    final var invMask = BigInteger.ONE.shiftLeft(POW5_INV_QUARTER_BITCOUNT).subtract(BigInteger.ONE);
    for (int i = 0; i < POW5_SPLIT.length; i++) {
      final var pow = BigInteger.valueOf(5).pow(i);
      final int pow5len = pow.bitLength();
      for (int j = 0; j < 4; j++) {
        POW5_SPLIT[i][j] = pow
          .shiftRight(pow5len - POW5_BITCOUNT + (3 - j) * POW5_QUARTER_BITCOUNT)
          .and(mask)
          .intValueExact();
      }
      if (i < POW5_INV_SPLIT.length) {
        // We want floor(log_2 5^q) here, which is pow5len - 1.
        final int j = pow5len - 1 + POW5_INV_BITCOUNT;
        final var inv = BigInteger.ONE.shiftLeft(j).divide(pow).add(BigInteger.ONE);
        for (int k = 0; k < 4; k++) {
          final var part = inv.shiftRight((3 - k) * POW5_INV_QUARTER_BITCOUNT);
          POW5_INV_SPLIT[i][k] = (k == 0 ? part : part.and(invMask)).intValueExact();
        }
      }
    }
  }

  private RyuDouble() {
  }

  /**
   * Write a finite, non-zero double into the buffer, which must have {@link #MAX_LENGTH} bytes
   * available from position.
   *
   * @return the number of bytes written
   */
  static int writeDouble(final double value, final byte[] buffer, final int position) {
    final long whole = (long) value;
    if (whole == value && whole > -MAX_EXACT_LONG && whole < MAX_EXACT_LONG) {
      // Whole numbers are common and easy: write the digits and add ".0"
      final long abs = Math.abs(whole);
      return format(whole < 0, abs, decimalLength(abs), 0, buffer, position);
    }
    // Step 1: Decode the floating point number, and unify normalized and subnormal cases.
    final long bits = Double.doubleToRawLongBits(value);
    final int ieeeExponent = (int) ((bits >>> DOUBLE_MANTISSA_BITS) & DOUBLE_EXPONENT_MASK);
    final long ieeeMantissa = bits & DOUBLE_MANTISSA_MASK;
    int e2;
    final long m2;
    if (ieeeExponent == 0) {
      // Denormal number - no implicit leading 1, and the exponent is 1, not 0.
      e2 = 1 - DOUBLE_EXPONENT_BIAS - DOUBLE_MANTISSA_BITS;
      m2 = ieeeMantissa;
    } else {
      // Add implicit leading 1.
      e2 = ieeeExponent - DOUBLE_EXPONENT_BIAS - DOUBLE_MANTISSA_BITS;
      m2 = ieeeMantissa | (1L << DOUBLE_MANTISSA_BITS);
    }

    // Step 2: Determine the interval of legal decimal representations.
    final boolean even = (m2 & 1) == 0;
    final long mv = 4 * m2;
    final long mp = 4 * m2 + 2;
    final int mmShift = ((m2 != (1L << DOUBLE_MANTISSA_BITS)) || (ieeeExponent <= 1)) ? 1 : 0;
    final long mm = 4 * m2 - 1 - mmShift;
    e2 -= 2;

    // Step 3: Convert to a decimal power base using 128-bit arithmetic.
    // -1077 = 1 - 1023 - 53 - 2 <= e_2 - 2 <= 2046 - 1023 - 53 - 2 = 968
    long dv;
    long dp;
    long dm;
    final int e10;
    boolean dmIsTrailingZeros = false;
    boolean dvIsTrailingZeros = false;
    if (e2 >= 0) {
      final int q = Math.max(0, ((e2 * 78913) >>> 18) - 1);
      // k = constant + floor(log_2(5^q))
      final int k = POW5_INV_BITCOUNT + pow5bits(q) - 1;
      final int i = -e2 + q + k;
      dv = mulPow5InvDivPow2(mv, q, i);
      dp = mulPow5InvDivPow2(mp, q, i);
      dm = mulPow5InvDivPow2(mm, q, i);
      e10 = q;
      if (q <= 21) {
        if (mv % 5 == 0) {
          dvIsTrailingZeros = multipleOfPowerOf5(mv, q);
        } else if (even) {
          dmIsTrailingZeros = multipleOfPowerOf5(mm, q);
        } else if (multipleOfPowerOf5(mp, q)) {
          dp--;
        }
      }
    } else {
      final int q = Math.max(0, ((-e2 * 732923) >>> 20) - 1);
      final int i = -e2 - q;
      final int k = pow5bits(i) - POW5_BITCOUNT;
      final int j = q - k;
      dv = mulPow5divPow2(mv, i, j);
      dp = mulPow5divPow2(mp, i, j);
      dm = mulPow5divPow2(mm, i, j);
      e10 = q + e2;
      if (q <= 1) {
        dvIsTrailingZeros = true;
        if (even) {
          dmIsTrailingZeros = mmShift == 1;
        } else {
          dp--;
        }
      } else if (q < 63) {
        dvIsTrailingZeros = (mv & ((1L << (q - 1)) - 1)) == 0;
      }
    }

    // Step 4: Find the shortest decimal representation in the interval of legal representations.
    int removed = 0;
    int lastRemovedDigit = 0;
    long output;
    if (dmIsTrailingZeros || dvIsTrailingZeros) {
      while (dp / 10 > dm / 10) {
        dmIsTrailingZeros &= dm % 10 == 0;
        dvIsTrailingZeros &= lastRemovedDigit == 0;
        lastRemovedDigit = (int) (dv % 10);
        dp /= 10;
        dv /= 10;
        dm /= 10;
        removed++;
      }
      if (dmIsTrailingZeros && even) {
        while (dm % 10 == 0) {
          dvIsTrailingZeros &= lastRemovedDigit == 0;
          lastRemovedDigit = (int) (dv % 10);
          dp /= 10;
          dv /= 10;
          dm /= 10;
          removed++;
        }
      }
      if (dvIsTrailingZeros && (lastRemovedDigit == 5) && (dv % 2 == 0)) {
        // Round even if the exact number is .....50..0.
        lastRemovedDigit = 4;
      }
      output = dv + ((dv == dm && !(dmIsTrailingZeros && even)) || (lastRemovedDigit >= 5) ? 1 : 0);
    } else {
      // Usual case: only the last digit we drop affects rounding. If dropping r digits works then
      // so does dropping fewer, so try big steps first (8, then 4, 2 and 1) rather than one at a time.
      boolean roundUp = false;
      while (dp / 100_000_000 > dm / 100_000_000) {
        final long div = dv / 100_000_000;
        roundUp = dv - div * 100_000_000 >= 50_000_000;
        dv = div;
        dp /= 100_000_000;
        dm /= 100_000_000;
        removed += 8;
      }
      if (dp / 10_000 > dm / 10_000) {
        final long div = dv / 10_000;
        roundUp = dv - div * 10_000 >= 5_000;
        dv = div;
        dp /= 10_000;
        dm /= 10_000;
        removed += 4;
      }
      if (dp / 100 > dm / 100) {
        final long div = dv / 100;
        roundUp = dv - div * 100 >= 50;
        dv = div;
        dp /= 100;
        dm /= 100;
        removed += 2;
      }
      if (dp / 10 > dm / 10) {
        final long div = dv / 10;
        roundUp = dv - div * 10 >= 5;
        dv = div;
        dm /= 10;
        removed++;
      }
      output = dv + ((dv == dm || roundUp) ? 1 : 0);
    }
    return format(bits < 0, output, decimalLength(output), e10 + removed, buffer, position);
  }

  private static final long[] POW_10 = new long[19];

  static {
    POW_10[0] = 1;
    for (int i = 1; i < POW_10.length; i++) {
      POW_10[i] = POW_10[i - 1] * 10;
    }
  }

  /**
   * Write {@code output * 10^exp10} in the same layout Grisu3 used, given output has olength digits.
   */
  private static int format(boolean negative, long output, int olength, int exp10, byte[] buffer, int position) {
    int index = position;
    if (negative) {
      buffer[index++] = '-';
    }
    // Where the decimal point falls, counted from the first digit (value is 0.d1d2... * 10^point)
    final int point = olength + exp10;
    if (point < -5 || point > 21) {
      // Very large or small: scientific notation like 1.5E+22
      if (olength > 1) {
        writeDigits(output, buffer, index + 1, olength);
        buffer[index] = buffer[index + 1];
        buffer[index + 1] = '.';
        index += olength + 1;
      } else {
        buffer[index++] = (byte) ('0' + output);
      }
      buffer[index++] = 'E';
      int exp = point - 1;
      if (exp < 0) {
        buffer[index++] = '-';
        exp = -exp;
      } else {
        buffer[index++] = '+';
      }
      if (exp >= 100) {
        buffer[index++] = (byte) ('0' + exp / 100);
        exp %= 100;
        buffer[index++] = (byte) ('0' + exp / 10);
      } else if (exp >= 10) {
        buffer[index++] = (byte) ('0' + exp / 10);
      }
      buffer[index++] = (byte) ('0' + exp % 10);
    } else if (point >= olength) {
      // A whole number: the digits, any trailing zeros, then ".0"
      writeDigits(output, buffer, index, olength);
      index += olength;
      for (int i = olength; i < point; i++) {
        buffer[index++] = '0';
      }
      buffer[index++] = '.';
      buffer[index++] = '0';
    } else if (point > 0) {
      // The decimal point lands somewhere inside the digits
      writeDigits(output, buffer, index, olength + 1);
      System.arraycopy(buffer, index + 1, buffer, index, point);
      buffer[index + point] = '.';
      index += olength + 1;
    } else {
      // Below one: "0." then leading zeros then the digits
      buffer[index++] = '0';
      buffer[index++] = '.';
      for (int i = point; i < 0; i++) {
        buffer[index++] = '0';
      }
      writeDigits(output, buffer, index, olength);
      index += olength;
    }
    return index - position;
  }

  /** Write the lowest {@code length} digits of value into buffer, filling start to start + length. */
  private static void writeDigits(long value, byte[] buffer, int start, int length) {
    for (int i = start + length - 1; i >= start; i--) {
      buffer[i] = (byte) ('0' + value % 10);
      value /= 10;
    }
  }

  private static int decimalLength(long v) {
    for (int i = 1; i < POW_10.length; i++) {
      if (v < POW_10[i]) {
        return i;
      }
    }
    return 19;
  }

  private static int pow5bits(int e) {
    return ((e * 1217359) >>> 19) + 1;
  }

  private static boolean multipleOfPowerOf5(long value, int q) {
    return pow5Factor(value) >= q;
  }

  private static int pow5Factor(long value) {
    int count = 0;
    while (value > 0 && value % 5 == 0) {
      value /= 5;
      count++;
    }
    return count;
  }

  /**
   * Compute the high digits of m * 5^p / 10^q = m * 5^(p - q) / 2^q = m * 5^i / 2^j, with q chosen
   * such that m * 5^i / 2^j has sufficiently many decimal digits to represent the original floating
   * point number.
   */
  private static long mulPow5divPow2(long m, int i, int j) {
    return mulShift(m, POW5_SPLIT[i], j);
  }

  /**
   * Compute the high digits of m / 5^i / 2^j such that the result is accurate to at least 9
   * decimal digits. i and j are already chosen appropriately.
   */
  private static long mulPow5InvDivPow2(long m, int i, int j) {
    return mulShift(m, POW5_INV_SPLIT[i], j);
  }

  private static long mulShift(long m, int[] split, int j) {
    // m has at most 55 bits.
    final long mHigh = m >>> 31;
    final long mLow = m & 0x7fffffff;
    final long bits13 = mHigh * split[0]; // 124
    final long bits03 = mLow * split[0];  // 93
    final long bits12 = mHigh * split[1]; // 93
    final long bits02 = mLow * split[1];  // 62
    final long bits11 = mHigh * split[2]; // 62
    final long bits01 = mLow * split[2];  // 31
    final long bits10 = mHigh * split[3]; // 31
    final long bits00 = mLow * split[3];  // 0
    final int actualShift = j - 3 * 31 - 21;
    return ((((((((bits00 >>> 31) + bits01 + bits10) >>> 31)
      + bits02 + bits11) >>> 31)
      + bits03 + bits12) >>> 21)
      + (bits13 << 10)) >>> actualShift;
  }
}
