package me.rakkoon69.rakunloots.match;

import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Random;

import static org.junit.jupiter.api.Assertions.assertEquals;

class CloseMatcherTest {

    private static List<String> closest(String typed, String... candidates) {
        return CloseMatcher.of(List.of(candidates)).closest(typed);
    }

    @Test
    void anExactHitIgnoringCaseIsTheOnlyMatchAndKeepsTheNameAsGiven() {
        assertEquals(List.of("Stone"), closest("STONE", "Stone", "stones", "stoned"));
    }

    @Test
    void aMissOfOneOrTwoEditsMatchesAndThreeDoesNot() {
        assertEquals(List.of("stone"), closest("stoxe", "stone"));
        assertEquals(List.of("stone"), closest("stxxe", "stone"));
        assertEquals(List.of(), closest("sxxxe", "stone"));
        assertEquals(List.of("stone"), closest("stne", "stone"));
        assertEquals(List.of("stone"), closest("stoneee", "stone"));
        assertEquals(List.of(), closest("stoneeee", "stone"));
    }

    @Test
    void aSwappedAdjacentPairCountsAsOneEditSoTwoSwapsStayInsideTheTolerance() {
        assertEquals(List.of("stone"), closest("stoen", "stone"));
        assertEquals(List.of("abcd"), closest("badc", "abcd"));
        assertEquals(List.of(), closest("badcfe", "abcdef"));
        assertEquals(List.of("abcd"), closest("bacd", "abcd"));
        assertEquals(List.of("abcde"), closest("bacdx", "abcde"));
    }

    @Test
    void aNameWhoseLengthIsMoreThanTheToleranceAwayIsNeverOffered() {
        assertEquals(List.of(), closest("ab", "abcde"));
        assertEquals(List.of("abcd"), closest("ab", "abcd"));
        assertEquals(List.of(), closest("", "abc"));
        assertEquals(List.of("ab"), closest("", "ab", "abc"));
    }

    @Test
    void aNameThatContainsWhatWasTypedIsClosestEvenWhenAnotherIsFewerEditsAway() {
        assertEquals(List.of("stonexx", "stane"), closest("stone", "stane", "stonexx"));
    }

    @Test
    void closerMatchesComeFirstAndOnlyTheClosestThreeAreKept() {
        assertEquals(List.of("stoxe", "sxone", "stoxx"),
                closest("stone", "xxone", "stxxe", "stoxx", "sxone", "stoxe"));
    }

    @Test
    void tiesGoByNameAndTheListHoldsAtMostThree() {
        assertEquals(List.of("bat", "cat", "hat"), closest("xat", "mat", "hat", "cat", "bat"));
    }

    @Test
    void aNearerMissBeatsAFurtherOneWhateverTheNames() {
        assertEquals(List.of("stoxe", "aaone"), closest("stone", "aaone", "stoxe"));
    }

    @Test
    void theMatcherAgreesWithAFullEditDistanceOnRandomNames() {
        Random random = new Random(7);
        for (int round = 0; round < 300; round++) {
            List<String> candidates = new ArrayList<>();
            for (int i = 0; i < 40; i++) {
                candidates.add(word(random, 1 + random.nextInt(7)));
            }
            CloseMatcher matcher = CloseMatcher.of(candidates);
            for (int t = 0; t < 20; t++) {
                String typed = word(random, random.nextInt(8));
                assertEquals(reference(typed, candidates), matcher.closest(typed),
                        "typed '" + typed + "' among " + candidates);
            }
        }
    }

    private static String word(Random random, int length) {
        StringBuilder builder = new StringBuilder();
        for (int i = 0; i < length; i++) {
            builder.append("abc".charAt(random.nextInt(3)));
        }
        return builder.toString();
    }

    /** The rules written the slow way: every candidate, a full table, a sort. */
    private static List<String> reference(String typed, List<String> candidates) {
        if (candidates.contains(typed)) {
            return List.of(typed);
        }
        record Scored(String name, int score) {
        }
        List<Scored> scored = new ArrayList<>();
        for (String name : candidates.stream().distinct().toList()) {
            if (Math.abs(name.length() - typed.length()) > 2) {
                continue;
            }
            int score = !typed.isEmpty() && name.contains(typed.toLowerCase(Locale.ROOT)) ? 0 : fullDistance(typed, name);
            if (score <= 2) {
                scored.add(new Scored(name, score));
            }
        }
        return scored.stream()
                .sorted(java.util.Comparator.comparingInt(Scored::score).thenComparing(Scored::name))
                .limit(3)
                .map(Scored::name)
                .toList();
    }

    private static int fullDistance(String a, String b) {
        int[][] d = new int[a.length() + 1][b.length() + 1];
        for (int i = 0; i <= a.length(); i++) {
            d[i][0] = i;
        }
        for (int j = 0; j <= b.length(); j++) {
            d[0][j] = j;
        }
        for (int i = 1; i <= a.length(); i++) {
            for (int j = 1; j <= b.length(); j++) {
                int cost = a.charAt(i - 1) == b.charAt(j - 1) ? 0 : 1;
                d[i][j] = Math.min(d[i - 1][j - 1] + cost, Math.min(d[i - 1][j] + 1, d[i][j - 1] + 1));
                if (i > 1 && j > 1 && a.charAt(i - 1) == b.charAt(j - 2) && a.charAt(i - 2) == b.charAt(j - 1)) {
                    d[i][j] = Math.min(d[i][j], d[i - 2][j - 2] + 1);
                }
            }
        }
        return d[a.length()][b.length()];
    }
}
