package app.lawnchair.zer0.voice;

import java.text.Normalizer;
import java.util.List;
import java.util.Locale;

/**
 * Finds an exact app label without guessing between packages or activities.
 *
 * <p>Only visible, caller-authorized profile activities should be supplied.
 * Return -1 when absent, -2 when ambiguous, or the unique matching index.
 */
public final class AppNameMatcher {
    public static final int NOT_FOUND = -1;
    public static final int AMBIGUOUS = -2;

    private AppNameMatcher() {}

    public static int uniqueIndex(String requestedLabel, List<String> labels) {
        String requested = canonical(requestedLabel);
        if (requested.isEmpty() || labels == null) return NOT_FOUND;

        int matchedIndex = NOT_FOUND;
        for (int i = 0; i < labels.size(); i++) {
            if (!requested.equals(canonical(labels.get(i)))) continue;
            if (matchedIndex != NOT_FOUND) return AMBIGUOUS;
            matchedIndex = i;
        }
        return matchedIndex;
    }

    private static String canonical(String text) {
        if (text == null) return "";
        return Normalizer.normalize(text, Normalizer.Form.NFKC)
                .trim()
                .toLowerCase(Locale.ROOT);
    }
}
