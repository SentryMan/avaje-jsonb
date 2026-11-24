package io.avaje.json.stream.core;

import java.math.BigInteger;

/**
 * Turns a decimal mantissa and exponent into the nearest double using the Eisel-Lemire algorithm.
 *
 * <p>The trick is to multiply the mantissa by a precomputed 128-bit approximation of the power of
 * five, which nearly always tells us the correctly rounded result straight away. For the handful
 * of inputs where the approximation is too close to call, this returns NaN and the caller falls
 * back to the slower exact conversion in {@link RyuParser}.
 *
 * <p>Algorithm by Michael Eisel and Daniel Lemire, see "Number Parsing at a Gigabyte per Second"
 * (https://arxiv.org/abs/2101.11408) and the fast_float library. The structure here follows Nigel
 * Tao's implementation in Go's strconv package (Copyright 2020 The Go Authors, BSD-3-Clause).
 */
final class EiselLemire {

  static final int MIN_EXP10 = -342;
  static final int MAX_EXP10 = 308;

  /** Powers of five from 5^MIN_EXP10 to 5^MAX_EXP10, each scaled to 128 bits and stored as a high, low pair. */
  private static final long[] POW5_128 = new long[2 * (MAX_EXP10 - MIN_EXP10 + 1)];

  static {
    final var mask64 = BigInteger.ONE.shiftLeft(64).subtract(BigInteger.ONE);
    final var two128 = BigInteger.ONE.shiftLeft(128);
    for (int q = MIN_EXP10; q <= MAX_EXP10; q++) {
      BigInteger c;
      if (q >= 0) {
        final var power5 = BigInteger.valueOf(5).pow(q);
        final int shift = power5.bitLength() - 128;
        c = shift > 0 ? power5.shiftRight(shift) : power5.shiftLeft(-shift);
      } else {
        final var power5 = BigInteger.valueOf(5).pow(-q);
        // z is the number of bits needed to hold 5^-q
        final int z = power5.subtract(BigInteger.ONE).bitLength();
        final int b = q >= -27 ? z + 127 : 2 * z + 128;
        c = BigInteger.ONE.shiftLeft(b).divide(power5).add(BigInteger.ONE);
        while (c.compareTo(two128) >= 0) {
          c = c.shiftRight(1);
        }
      }
      final int i = 2 * (q - MIN_EXP10);
      POW5_128[i] = c.shiftRight(64).longValue();
      POW5_128[i + 1] = c.and(mask64).longValue();
    }
  }

  private EiselLemire() {
  }

  /**
   * Return {@code w * 10^q} rounded to the nearest double. The mantissa w is treated as unsigned
   * and must not be zero. Returns NaN when the answer is too close to call, or when the result
   * would be subnormal or overflow, so the caller can use the exact path instead.
   */
  static double toDouble(long w, int q) {
    if (q < MIN_EXP10 || q > MAX_EXP10) {
      return Double.NaN;
    }
    final int lz = Long.numberOfLeadingZeros(w);
    w <<= lz;
    long exp2 = ((217706L * q) >> 16) + 64 + 1023 - lz;

    final int index = 2 * (q - MIN_EXP10);
    final long powHi = POW5_128[index];
    long xHi = mulHi(w, powHi);
    long xLo = w * powHi;

    // The low bits might carry into the part we keep, so bring in the next 64 bits of the power to be sure
    if ((xHi & 0x1FF) == 0x1FF && Long.compareUnsigned(xLo + w, w) < 0) {
      final long powLo = POW5_128[index + 1];
      final long yHi = mulHi(w, powLo);
      final long yLo = w * powLo;
      long mergedHi = xHi;
      final long mergedLo = xLo + yHi;
      if (Long.compareUnsigned(mergedLo, xLo) < 0) {
        mergedHi++;
      }
      if ((mergedHi & 0x1FF) == 0x1FF && mergedLo + 1 == 0 && Long.compareUnsigned(yLo + w, w) < 0) {
        return Double.NaN;
      }
      xHi = mergedHi;
      xLo = mergedLo;
    }

    // Keep the top 54 bits, one more than a double holds, so we can round
    final long msb = xHi >>> 63;
    long mantissa = xHi >>> (msb + 9);
    exp2 -= 1 ^ msb;

    // Exactly halfway between two doubles: let the exact path apply round half to even
    if (xLo == 0 && (xHi & 0x1FF) == 0 && (mantissa & 3) == 1) {
      return Double.NaN;
    }

    // Round the extra bit away, rounding up may overflow into a new top bit
    mantissa += mantissa & 1;
    mantissa >>>= 1;
    if ((mantissa >>> 53) > 0) {
      mantissa >>>= 1;
      exp2++;
    }
    // Too small for a normal double or too big to be finite, leave those to the exact path
    if (exp2 <= 0 || exp2 >= 0x7FF) {
      return Double.NaN;
    }
    return Double.longBitsToDouble((exp2 << 52) | (mantissa & 0xFFFFFFFFFFFFFL));
  }

  /** High 64 bits of the unsigned 128-bit product a * b (Math.unsignedMultiplyHigh needs Java 18). */
  static long mulHi(long a, long b) {
    return Math.multiplyHigh(a, b) + ((a >> 63) & b) + ((b >> 63) & a);
  }
}
