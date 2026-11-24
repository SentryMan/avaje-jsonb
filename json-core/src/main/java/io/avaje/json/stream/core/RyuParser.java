/*
 * Copyright (c) 2015, Nova Generacija Softvera d.o.o.
 * All rights reserved.
 *
 * Redistribution and use in source and binary forms, with or without
 * modification, are permitted provided that the following conditions are met:
 *
 *     * Redistributions of source code must retain the above copyright notice,
 *       this list of conditions and the following disclaimer.
 *
 *     * Redistributions in binary form must reproduce the above copyright notice,
 *       this list of conditions and the following disclaimer in the documentation
 *       and/or other materials provided with the distribution.
 *
 *     * Neither the name of Nova Generacija Softvera d.o.o. nor the names of its
 *       contributors may be used to endorse or promote products derived from this
 *       software without specific prior written permission.
 *
 * THIS SOFTWARE IS PROVIDED BY THE COPYRIGHT HOLDERS AND CONTRIBUTORS "AS IS" AND
 * ANY EXPRESS OR IMPLIED WARRANTIES, INCLUDING, BUT NOT LIMITED TO, THE IMPLIED
 * WARRANTIES OF MERCHANTABILITY AND FITNESS FOR A PARTICULAR PURPOSE ARE
 * DISCLAIMED. IN NO EVENT SHALL THE COPYRIGHT OWNER OR CONTRIBUTORS BE LIABLE FOR
 * ANY DIRECT, INDIRECT, INCIDENTAL, SPECIAL, EXEMPLARY, OR CONSEQUENTIAL DAMAGES
 * (INCLUDING, BUT NOT LIMITED TO, PROCUREMENT OF SUBSTITUTE GOODS OR SERVICES;
 * LOSS OF USE, DATA, OR PROFITS; OR BUSINESS INTERRUPTION) HOWEVER CAUSED AND
 * ON ANY THEORY OF LIABILITY, WHETHER IN CONTRACT, STRICT LIABILITY, OR TORT
 * (INCLUDING NEGLIGENCE OR OTHERWISE) ARISING IN ANY WAY OUT OF THE USE OF THIS
 * SOFTWARE, EVEN IF ADVISED OF THE POSSIBILITY OF SUCH DAMAGE.
 */
package io.avaje.json.stream.core;

import java.util.Arrays;

/**
 * Exact decimal to double conversion, ported from dsl-json's {@code RyuParser}.
 *
 * <p>Gives the same answer as {@link Double#parseDouble(String)} however many digits there are,
 * and does it without allocating. Mantissas that fit in a {@code long} use plain 64-bit math,
 * longer ones use a scratch buffer that each parser keeps for reuse.
 *
 * <p>This is the slow but always correct path. Most numbers are handled by {@link EiselLemire}
 * before they get here.
 */
final class RyuParser {

  /** log2(10) as a fixed point number with 40 fraction bits, used to estimate binary exponents. */
  private static final long LOG2_10_F40 = 3652498566964L;

  /** The biggest power of five that still fits in a long. */
  private static final int MAX_POW5_LONG = 27;

  /** Big number powers of five, stored as 64-bit words with the lowest word first. Grows when needed. */
  private static Pow5Table POW5_TABLE = buildPow5Table(900);

  /** Powers of five small enough to fit in a single long. */
  private static final long[] POW5_LONG = new long[MAX_POW5_LONG + 1];

  static {
    for (int i = 0; i <= MAX_POW5_LONG; i++) {
      POW5_LONG[i] = POW5_TABLE.values[i][0];
    }
  }

  private long[] scratch = new long[64];

  // The number we read, as a mantissa M and decimal exponent E
  /** The mantissa while it still fits in a long. */
  private long mLong;
  /** How many words of scratch the mantissa takes once it outgrows a long, zero while mLong is in use. */
  private int bigWords;
  /** How many digits we have read. */
  private int totalDigits;
  /** Position of the first non-zero digit, or -1 if every digit so far is zero. */
  private int firstNonZero;
  /** The decimal exponent: the written exponent minus the number of digits after the point. */
  private int exp;
  private boolean negative;

  private static final class Pow5Table {
    final long[][] values;
    final int[] words;
    final int[] bitLen;

    Pow5Table(long[][] values, int[] words, int[] bitLen) {
      this.values = values;
      this.words = words;
      this.bitLen = bitLen;
    }
  }

  private static void ensurePow5(int n) {
    if (n < POW5_TABLE.values.length) {
      return;
    }
    final int newSize = Math.max(n + 1, POW5_TABLE.values.length * 2);
    POW5_TABLE = buildPow5Table(newSize);
  }

  private static Pow5Table buildPow5Table(int size) {
    final var values = new long[size][];
    final var words = new int[size];
    final var bitLen = new int[size];
    long[] cur = {1};
    for (int i = 0; i < size; i++) {
      values[i] = cur;
      words[i] = cur.length;
      bitLen[i] = bitLength(cur, 0, cur.length);
      if (i + 1 < size) {
        cur = mulBy5(cur);
      }
    }
    return new Pow5Table(values, words, bitLen);
  }

  private static long[] mulBy5(long[] a) {
    final int n = a.length;
    final var out = new long[n + 1];
    long carry = 0;
    for (int i = 0; i < n; i++) {
      final long lo = a[i] * 5;
      final long hi = mulHi(a[i], 5);
      final long sum = lo + carry;
      out[i] = sum;
      carry = hi + (Long.compareUnsigned(sum, lo) < 0 ? 1L : 0L);
    }
    if (carry != 0) {
      out[n] = carry;
    }
    int m = out.length;
    while (m > 1 && out[m - 1] == 0) {
      m--;
    }
    return m == out.length ? out : Arrays.copyOf(out, m);
  }

  /**
   * Parse the number between start and end in buf. Trailing whitespace is fine.
   */
  double parseDouble(JParser reader, byte[] buf, int start, int end) {
    scan(reader, buf, start, end);
    return toDouble();
  }

  private void scan(JParser reader, byte[] buf, int start, int end0) {
    int end = end0;
    while (end > start && isWs(buf[end - 1])) {
      end--;
    }
    negative = false;
    if (end == start) {
      // Nothing but whitespace, so there is no number here
      if (end0 != start) {
        NumberParser.numberException(reader, start, end0, "Unknown digit", ' ');
      }
      NumberParser.numberException(reader, start, end0, "Digit not found");
    }
    int i = start;
    if (buf[i] == '-') {
      negative = true;
      i++;
    } else if (buf[i] == '+') {
      NumberParser.numberException(reader, start, end0, "Unknown digit", '+');
    }
    if (end == i) {
      NumberParser.numberException(reader, start, end0, "Digit not found");
    }
    long mLong = 0;
    int bigWords = 0;
    int totalDigits = 0;
    int firstNonZero = -1;
    final int digitsStart = i;
    // We only need the first digit for this. Without one, "Digit not found" has already been thrown
    final boolean leadingZero = buf[digitsStart] == '0';
    byte ch = ' ';
    int fracCount = 0;
    boolean fraction = false;
    for (; i < end; i++) {
      ch = buf[i];
      if (ch == 'e' || ch == 'E') {
        break;
      }
      if (ch == '.' && !fraction) {
        if (i == digitsStart) {
          NumberParser.numberException(reader, start, end0, "Digit not found");
        }
        if (i + 1 == end) {
          NumberParser.numberException(reader, start, end0, "Number ends with a dot");
        }
        fraction = true;
        continue;
      }
      final int ind = ch - '0';
      if (ind < 0 || ind > 9) {
        if (leadingZero && i > digitsStart + 1) {
          NumberParser.numberException(reader, start, end0, "Leading zero is not allowed");
        }
        NumberParser.numberException(reader, start, end0, "Unknown digit", (char) ch);
      }
      if (bigWords == 0) {
        if (mLong <= (Long.MAX_VALUE - ind) / 10) {
          mLong = mLong * 10 + ind;
        } else {
          final long[] s = ensureCapacity(scratch, 2);
          final long lo = mLong * 10;
          long hi = mulHi(mLong, 10);
          final long sum = lo + ind;
          if (Long.compareUnsigned(sum, lo) < 0) {
            hi++;
          }
          s[0] = sum;
          s[1] = hi; // never zero, since we only get here once mLong is too big for a long
          bigWords = 2;
          // mLong is at least 10^17 here, so counting its digits tells us where the first non-zero digit was
          firstNonZero = totalDigits - decimalLength(mLong);
          mLong = 0;
        }
      } else {
        final long[] s = ensureCapacity(scratch, bigWords + 1);
        long carry = ind;
        for (int w = 0; w < bigWords; w++) {
          final long word = s[w];
          final long lo = word * 10;
          final long hi = mulHi(word, 10);
          final long sum = lo + carry;
          s[w] = sum;
          carry = hi + (Long.compareUnsigned(sum, lo) < 0 ? 1L : 0L);
        }
        if (carry != 0) {
          s[bigWords++] = carry;
        }
      }
      totalDigits++;
      if (fraction) {
        fracCount++;
      }
    }
    if (i == digitsStart) {
      NumberParser.numberException(reader, start, end0, "Digit not found");
    }
    if (leadingZero && !fraction && totalDigits > 1) {
      NumberParser.numberException(reader, start, end0, "Leading zero is not allowed");
    }
    if (i < end) { // what's left must be the exponent, after an 'e' or 'E'
      i++;
      boolean expNeg = false;
      if (i < end && (buf[i] == '-' || buf[i] == '+')) {
        expNeg = buf[i] == '-';
        i++;
      }
      final int expDigitsStart = i;
      int expPart = 0;
      for (; i < end; i++) {
        final int ind = buf[i] - '0';
        if (ind < 0 || ind > 9) {
          NumberParser.numberException(reader, start, end0, "Unknown digit", (char) buf[i]);
        }
        // Cap it: an exponent this big means infinity or zero anyway
        expPart = Math.min(expPart * 10 + ind, 10000);
      }
      if (i == expDigitsStart) {
        NumberParser.numberException(reader, start, end0, "Digit not found");
      }
      exp = (expNeg ? -expPart : expPart) - fracCount;
    } else {
      exp = -fracCount;
    }
    // We switch to big numbers on signed overflow, so a value just above Long.MAX_VALUE can leave an empty top word. Drop it
    while (bigWords > 1 && scratch[bigWords - 1] == 0) {
      bigWords--;
    }
    this.mLong = mLong;
    this.bigWords = bigWords;
    this.totalDigits = totalDigits;
    this.firstNonZero = firstNonZero;
  }

  private static boolean isWs(byte b) {
    return (b >= 9 && b <= 13) || b == 32 || b == -96 || (b >= -31 && b <= -29);
  }

  private double toDouble() {
    // A big mantissa is never zero, so the only zero case is mLong == 0
    if (bigWords == 0 && mLong == 0) {
      return negative ? -0.0 : 0.0;
    }
    final int e10 = exp;
    // Small mantissa and small exponent: both fit exactly in a double, so one multiply or divide
    // already gives the correctly rounded answer
    if (bigWords == 0 && mLong < (1L << 53) && e10 >= -22 && e10 <= 22) {
      final double result = e10 >= 0 ? mLong * NumberParser.EXACT_POW_10[e10] : mLong / NumberParser.EXACT_POW_10[-e10];
      return negative ? -result : result;
    }
    if (bigWords == 0) {
      final double result = EiselLemire.toDouble(mLong, e10);
      if (result == result) {
        return negative ? -result : result;
      }
    }
    // firstNonZero is only set once we switch to big numbers, so for a long mantissa count its digits instead
    final int sig = bigWords == 0 ? decimalLength(mLong) : totalDigits - firstNonZero;
    // Quick exits for numbers far too big (infinity) or far too small (zero)
    if (e10 + sig - 1 >= 309) {
      return negative ? Double.NEGATIVE_INFINITY : Double.POSITIVE_INFINITY;
    }
    if (e10 + sig <= -325) {
      return negative ? -0.0 : 0.0;
    }
    final int nM = bigWords == 0 ? 1 : bigWords;
    if (bigWords == 0) {
      scratch[0] = mLong;
    }
    final int aExp = Math.max(e10, 0);
    final int bExp = Math.max(-e10, 0);
    ensurePow5(Math.max(aExp, bExp));
    final Pow5Table tab = POW5_TABLE;
    final int n5max = Math.max(tab.words[aExp], tab.words[bExp]);
    // Size scratch for all the steps up front so it doesn't move while we use it
    final long[] s = ensureCapacity(scratch, 5 * nM + 7 * n5max + 128);

    // Find the power of two just below the value
    final int blM = bitLength(s, 0, nM);
    int e = blM - 1 + (int) (((long) e10 * LOG2_10_F40) >> 40);
    while (gePow2(s, nM, e + 1)) {
      e++;
    }
    while (!gePow2(s, nM, e)) {
      e--;
    }
    final boolean subnormal = e < -1022;
    final int t = subnormal ? e10 + 1074 : e10 + 52 - e;

    // Work out the 53-bit mantissa as a rounded division A / D (ties go to even)
    long q = fastDivide(t, aExp, bExp);
    if (q < 0) {
      q = slowDivide(s, nM, t, aExp, bExp, subnormal ? 52 : 53);
    }
    final long bits;
    if (subnormal) {
      bits = q; // if rounding carried q up to 2^52, these bits already encode the smallest normal double
    } else {
      if (q == (1L << 53)) {
        e++;
        q = 1L << 52;
      }
      if (e > 1023) {
        return negative ? Double.NEGATIVE_INFINITY : Double.POSITIVE_INFINITY;
      }
      bits = ((long) (e + 1023) << 52) | (q & 0xFFFFFFFFFFFFFL);
    }
    final double result = Double.longBitsToDouble(bits);
    return negative ? -result : result;
  }

  /** Is the parsed value at least 2^k? The mantissa is in s and is at least one. */
  private boolean gePow2(long[] s, int nM, int k) {
    final int e10 = exp;
    final int t = k - e10; // same question as M * 5^E >= 2^t, with the twos moved over
    ensurePow5(Math.abs(e10));
    final Pow5Table tab = POW5_TABLE;
    if (e10 >= 0) {
      if (t <= 0) {
        return true; // the left side is at least one and the right side isn't
      }
      final int blM = bitLength(s, 0, nM);
      final int bl5 = tab.bitLen[e10];
      // Multiplying adds bit lengths, give or take one bit
      if (blM + bl5 - 1 >= t + 1) {
        return true;
      }
      if (blM + bl5 <= t) {
        return false;
      }
      // Too close to tell from bit lengths, so compute the product
      final int offP = nM + tab.words[e10];
      ensureCapacity(s, offP + nM + tab.words[e10]);
      final int nP = mul(s, 0, nM, tab.values[e10], tab.words[e10], offP);
      return bitLength(s, offP, nP) == t + 1;
    }
    // Negative exponent: compare M with 5^-E * 2^t instead, which avoids dividing
    final int bExp = -e10;
    if (t >= 0) {
      final int blR = t + tab.bitLen[bExp]; // shifting just adds t bits
      final int blM = bitLength(s, 0, nM);
      if (blM != blR) {
        return blM > blR;
      }
      final int offR = nM + tab.words[bExp] + 1;
      ensureCapacity(s, offR + tab.words[bExp] + 1);
      final int nR = shiftLeft(tab.values[bExp], 0, tab.words[bExp], t, offR);
      return cmp(s, 0, nM, s, offR, nR) >= 0;
    }
    // Shift M up instead so both sides are whole numbers
    final int sh = -t;
    final int blL = bitLength(s, 0, nM) + sh; // shifting left loses no bits
    if (blL != tab.bitLen[bExp]) {
      return blL > tab.bitLen[bExp];
    }
    final int offL = nM + tab.words[bExp] + 1;
    ensureCapacity(s, offL + nM + (sh >>> 6) + 2);
    final int nL = shiftLeft(s, 0, nM, sh, offL);
    return cmp(s, offL, nL, tab.values[bExp], 0, tab.words[bExp]) >= 0;
  }

  /** Try the rounded division A / D with plain longs. Returns -1 if either is too big for that. */
  private long fastDivide(int t, int aExp, int bExp) {
    if (bigWords != 0 || aExp > MAX_POW5_LONG || bExp > MAX_POW5_LONG) {
      return -1;
    }
    final long pow5a = POW5_LONG[aExp];
    if (mLong > Long.MAX_VALUE / pow5a) {
      return -1;
    }
    final long n = mLong * pow5a;
    if (t >= 0) {
      if (t >= 63 || n > Long.MAX_VALUE >> t) {
        return -1;
      }
      return divRound(n << t, POW5_LONG[bExp]);
    }
    final int sh = -t;
    final long pow5b = POW5_LONG[bExp];
    if (sh >= 64 || pow5b > Long.MAX_VALUE >> sh) {
      return -1;
    }
    return divRound(n, pow5b << sh);
  }

  /** A close double approximation of the big number in s, good to about 51 bits. At most 16 words. */
  private static double approx(long[] s, int off, int n) {
    final long w = s[off + n - 1];
    final double dw = (double) (w >>> 1) + ((w & 1L) != 0 ? 0.5 : 0.0); // half of w treated as unsigned
    if (n == 1) {
      return dw * 2.0;
    }
    final long v = s[off + n - 2];
    final double dv = (double) (v >>> 1) + ((v & 1L) != 0 ? 0.5 : 0.0);
    final int k = (n - 1) << 6;
    return Math.scalb(dw, k + 1) + Math.scalb(dv, k - 63);
  }

  /** Nudge the estimate q until it is exactly A / D rounded down. Gives up with -1 after 32 tries. */
  private long correctQuotient(long[] s, int offD, int nD, int offA, int nA, long q, int offT) {
    int guard = 0;
    while (cmp(s, offT, mulLong(s, offD, nD, q, offT), s, offA, nA) > 0) {
      if (++guard > 32) {
        return -1;
      }
      q--;
    }
    while (cmp(s, offT, mulLong(s, offD, nD, q + 1, offT), s, offA, nA) <= 0) {
      if (++guard > 32) {
        return -1;
      }
      q++;
    }
    return q;
  }

  /** A / D rounded to nearest, ties to even, for non-negative longs. */
  private static long divRound(long a, long d) {
    final long q = a / d;
    final long rem = a % d;
    final long half = d >>> 1;
    if (rem > half || (rem == half && (d & 1) == 0 && (q & 1) == 1)) {
      return q + 1;
    }
    return q;
  }

  /**
   * Work out M * 5^E * 2^T exactly with big numbers, rounded to nearest with ties to even.
   * The caller has already scaled things so the answer fits in maxBits bits (53, or 52 for subnormals).
   */
  private long slowDivide(long[] s, int nM, int t, int aExp, int bExp, int maxBits) {
    ensurePow5(Math.max(aExp, bExp));
    final Pow5Table tab = POW5_TABLE;
    // Numerator part one: the mantissa times any positive power of five
    final int offN;
    final int nN;
    if (aExp == 0) {
      offN = 0;
      nN = nM;
    } else {
      offN = nM + tab.words[aExp];
      ensureCapacity(s, offN + nM + tab.words[aExp]);
      nN = mul(s, 0, nM, tab.values[aExp], tab.words[aExp], offN);
    }
    // Positive powers of two also go on top
    final int offA = offN + nN;
    final int nA;
    if (t >= 0) {
      ensureCapacity(s, offA + nN + 1);
      nA = shiftLeft(s, offN, nN, t, offA);
    } else {
      ensureCapacity(s, offA + nN);
      System.arraycopy(s, offN, s, offA, nN);
      nA = nN;
    }
    // Negative powers of five and two go in the denominator
    final int shD = t < 0 ? -t : 0;
    final int offD = offA + nA;
    final int n5b = tab.words[bExp];
    ensureCapacity(s, offD + n5b + (shD >>> 6) + 2);
    final int nD = shiftLeft(tab.values[bExp], 0, n5b, shD, offD);

    // Scratch space for the remainder and the rounding check
    final int nRmax = Math.max(nA, nD);
    final int offR = offD + nD;
    final int offT = offR + nRmax;
    final int off2R = offT + nD + 1;
    ensureCapacity(s, off2R + nA + 1);

    // Start from a double estimate of A / D, which is only a few units off, then fix it up with
    // exact big number steps. If the estimate is no good, fall back to a binary search.
    long q = -1;
    if (nA <= 16 && nD <= 16) {
      final double ratio = approx(s, offA, nA) / approx(s, offD, nD);
      if (ratio >= 0 && ratio < (double) (1L << maxBits) * 2.0) {
        q = correctQuotient(s, offD, nD, offA, nA, (long) ratio, offT);
      }
    }
    if (q < 0) {
      q = 0;
      for (int bit = maxBits - 1; bit >= 0; bit--) {
        final long mid = q | (1L << bit);
        final int nT = mulLong(s, offD, nD, mid, offT);
        if (cmp(s, offT, nT, s, offA, nA) <= 0) {
          q = mid;
        }
      }
    }
    // What's left over after dividing
    final int nT2 = mulLong(s, offD, nD, q, offT);
    sub(s, offA, nA, s, offT, nT2, offR);
    // Round up if the remainder is more than half of D, or exactly half and q is odd
    final int n2R = shiftLeft(s, offR, nA, 1, off2R);
    final int c = cmp(s, off2R, n2R, s, offD, nD);
    if (c > 0 || (c == 0 && (q & 1) == 1)) {
      q++;
    }
    return q;
  }

  private long[] ensureCapacity(long[] cur, int needed) {
    if (needed <= cur.length) {
      return cur;
    }
    final long[] next = Arrays.copyOf(cur, Math.max(needed, cur.length * 2));
    scratch = next;
    return next;
  }

  /** How many decimal digits a positive long has. */
  private static int decimalLength(long v) {
    int len = 1;
    for (long p = 10; len < 19 && v >= p; p *= 10) {
      len++;
    }
    return len;
  }

  /** Number of bits in the big number at s[off], n words long. Its top word must not be zero. */
  private static int bitLength(long[] s, int off, int n) {
    return n * 64 - Long.numberOfLeadingZeros(s[off + n - 1]);
  }

  /** Compare two big numbers in s, treating words as unsigned. */
  private static int cmp(long[] a, int offA, int nA, long[] b, int offB, int nB) {
    int la = nA;
    int lb = nB;
    while (la > 1 && a[offA + la - 1] == 0) {
      la--;
    }
    while (lb > 1 && b[offB + lb - 1] == 0) {
      lb--;
    }
    if (la != lb) {
      return la < lb ? -1 : 1;
    }
    for (int i = la - 1; i >= 0; i--) {
      final int c = Long.compareUnsigned(a[offA + i], b[offB + i]);
      if (c != 0) {
        return c;
      }
    }
    return 0;
  }

  /** Multiply two big numbers, writing the result at offOut, which must not overlap the inputs. Returns the word count. */
  private int mul(long[] a, int offA, int nA, long[] b, int nB, int offOut) {
    final long[] s = scratch;
    Arrays.fill(s, offOut, offOut + nA + nB, 0);
    for (int i = 0; i < nA; i++) {
      final long ai = a[offA + i];
      if (ai == 0) {
        continue;
      }
      long carry = 0;
      for (int j = 0; j <= nB; j++) {
        final long bj = j < nB ? b[j] : 0;
        final long lo = ai * bj;
        final long hi = mulHi(ai, bj);
        final int k = offOut + i + j;
        final long sum1 = s[k] + lo;
        final long c1 = Long.compareUnsigned(sum1, lo) < 0 ? 1L : 0L;
        final long sum2 = sum1 + carry;
        final long c2 = Long.compareUnsigned(sum2, sum1) < 0 ? 1L : 0L;
        s[k] = sum2;
        carry = hi + c1 + c2; // can't overflow, same as with long multiplication by hand
      }
    }
    int n = nA + nB;
    while (n > 1 && s[offOut + n - 1] == 0) {
      n--;
    }
    return n;
  }

  /** Multiply big number d by the long q, writing the result at offOut. Returns the word count. */
  private int mulLong(long[] d, int offD, int nD, long q, int offOut) {
    final long[] s = scratch;
    if (q == 0) {
      s[offOut] = 0;
      return 1;
    }
    long carry = 0;
    for (int j = 0; j <= nD; j++) {
      final long dj = j < nD ? d[offD + j] : 0;
      final long lo = dj * q;
      final long hi = mulHi(dj, q);
      final long sum = lo + carry;
      s[offOut + j] = sum;
      carry = hi + (Long.compareUnsigned(sum, lo) < 0 ? 1L : 0L);
    }
    int n = nD + 1;
    while (n > 1 && s[offOut + n - 1] == 0) {
      n--;
    }
    return n;
  }

  /** Subtract b from a (a must be the larger), writing nA words at offOut. */
  private void sub(long[] a, int offA, int nA, long[] b, int offB, int nB, int offOut) {
    final long[] s = scratch;
    long borrow = 0;
    for (int i = 0; i < nA; i++) {
      final long ai = a[offA + i];
      final long bi = i < nB ? b[offB + i] : 0;
      final long t = ai - bi;
      // We need to borrow when the subtraction wrapped around
      final long nb = Long.compareUnsigned(t, ai) > 0 ? 1L : 0L;
      final long v = t - borrow;
      s[offOut + i] = v;
      // Same check for taking off the earlier borrow
      borrow = nb + (Long.compareUnsigned(v, t) > 0 ? 1L : 0L);
    }
  }

  /** Shift a big number left by bits, writing the result at offOut away from the input. Returns the word count. */
  private int shiftLeft(long[] a, int offA, int nA, int bits, int offOut) {
    final long[] s = scratch;
    if (bits == 0) {
      System.arraycopy(a, offA, s, offOut, nA);
      return nA;
    }
    final int wordShift = bits >>> 6;
    final int bitShift = bits & 63;
    Arrays.fill(s, offOut, offOut + nA + wordShift + 1, 0);
    for (int i = 0; i < nA; i++) {
      final long v = a[offA + i];
      if (v == 0) {
        continue;
      }
      final int k = offOut + i + wordShift;
      s[k] |= v << bitShift;
      if (bitShift != 0) {
        s[k + 1] |= v >>> (64 - bitShift);
      }
    }
    int n = nA + wordShift + 1;
    while (n > 1 && s[offOut + n - 1] == 0) {
      n--;
    }
    return n;
  }

  /** High 64 bits of the unsigned 128-bit product a * b. */
  private static long mulHi(long a, long b) {
    return Math.multiplyHigh(a, b) + ((a >> 63) & b) + ((b >> 63) & a);
  }
}
