package me.rakkoon69.rakunloots.match;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/**
 * Finds up to {@value #LIMIT} names close to what was typed, ignoring case. Build it once per pool of names and ask it
 * for each typed string.
 *
 * <p>An exact hit is a hash lookup and is the only match. Otherwise only names whose length is within
 * {@value #TOLERANCE} of the typed length are read, since a longer difference cannot be {@value #TOLERANCE} edits away.
 * A name that contains what was typed counts as closest, then names by edit distance, with a swapped adjacent pair
 * counted as one edit. Ties go by name.
 */
public final class CloseMatcher {

    public static final int LIMIT = 3;
    public static final int TOLERANCE = 2;

    private static final int FAR = TOLERANCE + 1;

    private final Map<String, String> byLowerName = new HashMap<>();
    /** Names of one length, in lower case, and the same names as they were given, at the same index. */
    private final List<String[]> lowerByLength = new ArrayList<>();
    private final List<String[]> namesByLength = new ArrayList<>();
    private final int longest;

    private CloseMatcher(Collection<String> candidates) {
        List<List<String>> lower = new ArrayList<>();
        List<List<String>> names = new ArrayList<>();
        int max = 0;
        for (String name : candidates) {
            String key = name.toLowerCase(Locale.ROOT);
            if (byLowerName.putIfAbsent(key, name) != null) {
                continue;
            }
            while (lower.size() <= key.length()) {
                lower.add(new ArrayList<>());
                names.add(new ArrayList<>());
            }
            lower.get(key.length()).add(key);
            names.get(key.length()).add(name);
            max = Math.max(max, key.length());
        }
        for (int length = 0; length < lower.size(); length++) {
            lowerByLength.add(lower.get(length).toArray(String[]::new));
            namesByLength.add(names.get(length).toArray(String[]::new));
        }
        longest = max;
    }

    public static CloseMatcher of(Collection<String> candidates) {
        return new CloseMatcher(candidates);
    }

    /** Up to {@value #LIMIT} names, closest first. Empty when none is within the tolerance. */
    public List<String> closest(String typed) {
        String lower = typed.toLowerCase(Locale.ROOT);
        String exact = byLowerName.get(lower);
        if (exact != null) {
            return List.of(exact);
        }
        int from = Math.max(0, lower.length() - TOLERANCE);
        int to = Math.min(longest, lower.length() + TOLERANCE);
        if (from > to) {
            return List.of();
        }
        int[] previous = new int[to + 2];
        int[] current = new int[to + 2];
        String[] bestNames = new String[LIMIT];
        int[] bestScores = new int[LIMIT];
        int kept = 0;
        for (int length = from; length <= to; length++) {
            String[] keys = lowerByLength.get(length);
            String[] names = namesByLength.get(length);
            for (int k = 0; k < keys.length; k++) {
                int score = !lower.isEmpty() && keys[k].contains(lower)
                        ? 0
                        : distance(lower, keys[k], previous, current);
                if (score > TOLERANCE) {
                    continue;
                }
                kept = insert(bestNames, bestScores, kept, names[k], score);
            }
        }
        return List.of(Arrays.copyOf(bestNames, kept));
    }

    private static int insert(String[] bestNames, int[] bestScores, int kept, String name, int score) {
        int at = kept;
        while (at > 0 && (bestScores[at - 1] > score
                || bestScores[at - 1] == score && bestNames[at - 1].compareTo(name) > 0)) {
            at--;
        }
        if (at >= LIMIT) {
            return kept;
        }
        for (int i = Math.min(kept, LIMIT - 1); i > at; i--) {
            bestNames[i] = bestNames[i - 1];
            bestScores[i] = bestScores[i - 1];
        }
        bestNames[at] = name;
        bestScores[at] = score;
        return Math.min(kept + 1, LIMIT);
    }

    /**
     * Edit distance with a swapped adjacent pair as one edit, or {@link #FAR} when it passes the tolerance. Only the
     * cells within the tolerance of the diagonal are computed. The two arrays are reused for every row: the one being
     * written still holds the row before the last, which the swap reads.
     */
    private static int distance(String a, String b, int[] first, int[] second) {
        int n = a.length();
        int m = b.length();
        if (Math.abs(n - m) > TOLERANCE) {
            return FAR;
        }
        int[] previous = first;
        int[] current = second;
        Arrays.fill(previous, 0, m + 2, FAR);
        Arrays.fill(current, 0, m + 2, FAR);
        for (int j = 0; j <= Math.min(m, TOLERANCE); j++) {
            previous[j] = j;
        }
        int previousMin = 0;
        for (int i = 1; i <= n; i++) {
            int low = Math.max(1, i - TOLERANCE);
            int high = Math.min(m, i + TOLERANCE);
            // the row before the last, at j-2 and j-1, read before this row overwrites it
            int twoBack = low >= 2 ? current[low - 2] : FAR;
            int oneBack = current[low - 1];
            current[low - 1] = low == 1 && i <= TOLERANCE ? i : FAR;
            int rowMin = current[low - 1];
            for (int j = low; j <= high; j++) {
                int old = current[j];
                int cost = a.charAt(i - 1) == b.charAt(j - 1) ? 0 : 1;
                int value = Math.min(previous[j - 1] + cost, Math.min(previous[j] + 1, current[j - 1] + 1));
                if (i >= 2 && j >= 2 && a.charAt(i - 1) == b.charAt(j - 2) && a.charAt(i - 2) == b.charAt(j - 1)) {
                    value = Math.min(value, twoBack + 1);
                }
                value = Math.min(value, FAR);
                current[j] = value;
                rowMin = Math.min(rowMin, value);
                twoBack = oneBack;
                oneBack = old;
            }
            current[high + 1] = FAR;
            // a swap reads two rows back, so both rows must be past the tolerance before the rest is hopeless
            if (rowMin > TOLERANCE && previousMin > TOLERANCE) {
                return FAR;
            }
            previousMin = rowMin;
            int[] swap = previous;
            previous = current;
            current = swap;
        }
        return Math.min(previous[m], FAR);
    }
}
