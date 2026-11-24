package io.avaje.json.stream.core;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.ArrayList;
import java.util.List;
import java.util.SplittableRandom;

import org.junit.jupiter.api.Test;

class EiselLemireTest {

  static void check(long w, int q, List<String> bad, int[] undecided) {
    final var actual = EiselLemire.toDouble(w, q);
    if (actual != actual) {
      undecided[0]++;
      return;
    }
    final var text = Long.toUnsignedString(w) + "e" + q;
    final var expected = Double.parseDouble(text);
    if (Double.compare(actual, expected) != 0) {
      bad.add(text + " -> " + actual + " expected " + expected);
    }
  }

  @Test
  void randomMantissas_matchJdk() {
    final var r = new SplittableRandom(5);
    final List<String> bad = new ArrayList<>();
    final var undecided = new int[1];
    for (var i = 0; i < 1_000_000 && bad.size() < 20; i++) {
      final long w;
      switch (i % 3) {
        case 0:
          w = r.nextLong();
          break;
        case 1:
          // Up to 17 digits, which is what JSON usually carries
          w = 1 + r.nextLong(99_999_999_999_999_999L);
          break;
        default:
          w = 1 + r.nextLong(1L << 54);
      }
      if (w == 0) {
        continue;
      }
      check(w, EiselLemire.MIN_EXP10 + r.nextInt(EiselLemire.MAX_EXP10 - EiselLemire.MIN_EXP10 + 1), bad, undecided);
      check(w, r.nextInt(80) - 40, bad, undecided);
    }
    assertThat(bad).isEmpty();
    // Fallbacks should be rare: tiny or huge results plus the odd too-close-to-call case
    assertThat(undecided[0]).isLessThan(250_000);
  }

  @Test
  void halfwayAndBoundaries_matchJdk() {
    final List<String> bad = new ArrayList<>();
    final var undecided = new int[1];
    final long[] mantissas = {
      1, 9, 10, 9007199254740992L, 9007199254740993L, 9007199254740995L, 18014398509481985L,
      Long.MAX_VALUE, -1L, 4503599627370497L, 17976931348623157L, 22250738585072014L, 49406564584124654L
    };
    for (final long w : mantissas) {
      for (var q = EiselLemire.MIN_EXP10; q <= EiselLemire.MAX_EXP10; q++) {
        check(w, q, bad, undecided);
      }
    }
    assertThat(bad).isEmpty();
  }
}
