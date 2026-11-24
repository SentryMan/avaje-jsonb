package io.avaje.json.stream.core;

import static org.assertj.core.api.Assertions.assertThat;

import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.SplittableRandom;

import org.junit.jupiter.api.Test;

class RyuDoubleTest {

  static String ryu(double value) {
    final var buffer = new byte[RyuDouble.MAX_LENGTH + 8];
    final int len = RyuDouble.writeDouble(value, buffer, 4);
    return new String(buffer, 4, len, StandardCharsets.US_ASCII);
  }

  static String grisu(double value) {
    final var builder = new Grisu3.FastDtoaBuilder();
    if (!Grisu3.tryConvert(value, builder)) {
      return null;
    }
    final var buffer = new byte[64];
    return new String(buffer, 0, builder.copyTo(buffer, 0), StandardCharsets.US_ASCII);
  }

  /**
   * Significant digits of Double.toString, shortest on JDK 19+ except that it never uses fewer
   * than 2 digits (MIN_VALUE is 4.9E-324 where 5E-324 also round trips).
   */
  static int significantDigits(double value) {
    return new BigDecimal(Double.toString(value)).stripTrailingZeros().precision();
  }

  @Test
  void examples() {
    assertThat(ryu(1.0)).isEqualTo("1.0");
    assertThat(ryu(-1.0)).isEqualTo("-1.0");
    assertThat(ryu(100.0)).isEqualTo("100.0");
    assertThat(ryu(1e10)).isEqualTo("10000000000.0");
    assertThat(ryu(1e20)).isEqualTo("100000000000000000000.0");
    assertThat(ryu(1e21)).isEqualTo("1E+21");
    assertThat(ryu(1e22)).isEqualTo("1E+22");
    assertThat(ryu(1.5e22)).isEqualTo("1.5E+22");
    assertThat(ryu(123.456)).isEqualTo("123.456");
    assertThat(ryu(0.1)).isEqualTo("0.1");
    assertThat(ryu(0.000001)).isEqualTo("0.000001");
    assertThat(ryu(1.5e-6)).isEqualTo("0.0000015");
    assertThat(ryu(1e-7)).isEqualTo("1E-7");
    assertThat(ryu(-1.25e-7)).isEqualTo("-1.25E-7");
    assertThat(ryu(Double.MAX_VALUE)).isEqualTo("1.7976931348623157E+308");
    assertThat(ryu(Double.MIN_VALUE)).isEqualTo("5E-324");
    assertThat(ryu(Double.MIN_NORMAL)).isEqualTo("2.2250738585072014E-308");
    assertThat(ryu(0.30000000000000004)).isEqualTo("0.30000000000000004");
    assertThat(ryu(9007199254740993.0)).isEqualTo("9007199254740992.0");
  }

  @Test
  void matchesGrisu3_andRoundTrips() {
    final var r = new SplittableRandom(11);
    final List<String> bad = new ArrayList<>();
    var grisuFailures = 0;
    for (var i = 0; i < 2_000_000 && bad.size() < 20; i++) {
      final double value;
      switch (i % 4) {
        case 0:
          value = Double.longBitsToDouble(r.nextLong() & 0xFFEF_FFFF_FFFF_FFFFL);
          break;
        case 1:
          value = r.nextDouble();
          break;
        case 2:
          value = Math.round(r.nextDouble() * 1e8) / 100.0;
          break;
        default:
          value = (r.nextDouble() - 0.5) * Math.pow(10, r.nextInt(50) - 25);
      }
      if (value == 0 || !Double.isFinite(value)) {
        continue;
      }
      final var actual = ryu(value);
      final var expected = grisu(value);
      if (expected == null) {
        grisuFailures++;
      } else if (!expected.equals(actual)) {
        bad.add(value + ": ryu " + actual + " grisu " + expected);
        continue;
      }
      if (Double.parseDouble(actual) != value) {
        bad.add(value + ": ryu " + actual + " does not round trip");
      } else if (new BigDecimal(actual).stripTrailingZeros().precision() > significantDigits(value)) {
        bad.add(value + ": ryu " + actual + " is not shortest, Double.toString " + value);
      }
    }
    assertThat(bad).isEmpty();
    assertThat(grisuFailures).isPositive();
  }

  @Test
  void powersOfTenAndBoundaries() {
    final List<String> bad = new ArrayList<>();
    for (var e = -324; e <= 308; e++) {
      for (final double value : new double[] {Double.parseDouble("1e" + e), Double.parseDouble("9.999999999999999e" + e)}) {
        if (value == 0 || !Double.isFinite(value)) {
          continue;
        }
        for (final double v : new double[] {value, Math.nextUp(value), Math.nextDown(value), -value}) {
          final var actual = ryu(v);
          if (Double.parseDouble(actual) != v
            || new BigDecimal(actual).stripTrailingZeros().precision() > significantDigits(v)) {
            bad.add(v + " -> " + actual);
          }
          final var expected = grisu(v);
          if (expected != null && !expected.equals(actual)) {
            bad.add(v + ": ryu " + actual + " grisu " + expected);
          }
        }
      }
    }
    assertThat(bad).isEmpty();
  }
}
