package app.lawnchair.zer0.voice.z0live;

import java.text.Normalizer;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/** Returns candidates, never a guessed winner. Caller must disambiguate multiple matches. */
public final class Z0LiveAppMatcher {
    private Z0LiveAppMatcher() {}

    public static List<Integer> candidates(String query, List<String> labels, List<String> packages) {
        if (query == null || query.length() > 1000 || labels == null || packages == null
                || labels.size() != packages.size()) throw new IllegalArgumentException("Invalid app query");
        String key = normalized(query);
        if (!query.isBlank() && key.isEmpty()) return List.of();
        List<Integer> exact = new ArrayList<>(), contains = new ArrayList<>(), fuzzy = new ArrayList<>();
        for (int i = 0; i < labels.size(); i++) {
            String label = normalized(labels.get(i));
            if (key.isEmpty() || key.equals(label) || query.equals(packages.get(i))) exact.add(i);
            else if (label.contains(key)) contains.add(i);
            else if (distance(label, key) <= (key.length() >= 5 ? 2 : 1)) fuzzy.add(i);
        }
        return !exact.isEmpty() ? exact : !contains.isEmpty() ? contains : fuzzy;
    }

    private static String normalized(String value) {
        if (value == null) return "";
        return Normalizer.normalize(value, Normalizer.Form.NFKD).toLowerCase(Locale.ROOT)
                .replaceAll("\\p{M}", "").replaceAll("[^\\p{L}\\p{N}]", "");
    }

    private static int distance(String a, String b) {
        int[] row = new int[b.length() + 1];
        for (int j = 0; j <= b.length(); j++) row[j] = j;
        for (int i = 0; i < a.length(); i++) {
            int[] next = new int[b.length() + 1];
            next[0] = i + 1;
            for (int j = 0; j < b.length(); j++) {
                next[j + 1] = Math.min(Math.min(next[j] + 1, row[j + 1] + 1), row[j] + (a.charAt(i) == b.charAt(j) ? 0 : 1));
            }
            row = next;
        }
        return row[b.length()];
    }
}
