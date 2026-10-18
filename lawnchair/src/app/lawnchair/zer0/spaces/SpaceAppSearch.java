package app.lawnchair.zer0.spaces;

import java.text.Normalizer;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;

/**
 * A small deterministic, offline command-palette filter for caller-approved visible apps.
 *
 * <p>It does not enumerate Android apps or change Lawnchair's actual app drawer. The caller
 * must first enforce personal-profile and hidden-app visibility, and revalidate on launch.
 */
public final class SpaceAppSearch {
    public static final int MAX_RESULTS = 8;

    public static final class Entry {
        public final String component;
        public final String label;

        public Entry(String component, String label) {
            this.component = component;
            this.label = label;
        }
    }

    private static final class Ranked {
        final Entry entry;
        final int rank;

        Ranked(Entry entry, int rank) {
            this.entry = entry;
            this.rank = rank;
        }
    }

    private SpaceAppSearch() {}

    public static List<Entry> filter(List<Entry> visible, String query) {
        String needle = normalizedQuery(query);
        if (visible == null || needle == null) return List.of();
        ArrayList<Ranked> ranked = new ArrayList<>();
        Set<String> seen = new HashSet<>();
        for (Entry candidate : visible) {
            if (candidate == null || candidate.label == null || candidate.component == null
                    || candidate.component.isEmpty() || !seen.add(candidate.component)) continue;
            String title = Normalizer.normalize(candidate.label, Normalizer.Form.NFKC)
                    .toLowerCase(Locale.ROOT);
            String component = candidate.component.toLowerCase(Locale.ROOT);
            int score = rank(title, component, needle);
            if (score < Integer.MAX_VALUE) ranked.add(new Ranked(candidate, score));
        }
        ranked.sort(Comparator.comparingInt((Ranked r) -> r.rank)
                .thenComparing(r -> r.entry.label, String.CASE_INSENSITIVE_ORDER)
                .thenComparing(r -> r.entry.component));
        ArrayList<Entry> matches = new ArrayList<>();
        for (Ranked result : ranked) {
            if (matches.size() == MAX_RESULTS) break;
            matches.add(result.entry);
        }
        return matches;
    }

    /** Enter opens only a unique, exact visible app label; ambiguity never guesses. */
    public static Entry exactUnique(List<Entry> visible, String query) {
        String needle = normalizedQuery(query);
        if (visible == null || needle == null) return null;
        Entry only = null;
        for (Entry candidate : visible) {
            if (candidate == null || candidate.label == null || candidate.component == null) continue;
            String title = Normalizer.normalize(candidate.label, Normalizer.Form.NFKC)
                    .toLowerCase(Locale.ROOT);
            if (title.equals(needle)) {
                if (only != null) return null;
                only = candidate;
            }
        }
        return only;
    }

    private static int rank(String title, String component, String needle) {
        if (title.equals(needle)) return 0;
        if (title.startsWith(needle)) return 1;
        if (title.contains(" " + needle)) return 2;
        if (title.contains(needle)) return 3;
        if (component.contains(needle)) return 4;
        return Integer.MAX_VALUE;
    }

    private static String normalizedQuery(String query) {
        if (query == null) return null;
        String normalized = Normalizer.normalize(query, Normalizer.Form.NFKC);
        if (normalized.length() > 160) return null;
        for (int i = 0; i < normalized.length();) {
            int cp = normalized.codePointAt(i);
            if (Character.isISOControl(cp)
                    || Character.getType(cp) == Character.FORMAT) return null;
            i += Character.charCount(cp);
        }
        String text = normalized.trim().toLowerCase(Locale.ROOT).replaceAll(" +", " ");
        return text.isEmpty() ? null : text;
    }
}
