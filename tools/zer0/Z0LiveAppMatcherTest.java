package app.lawnchair.zer0.voice.z0live;

import java.util.List;

public final class Z0LiveAppMatcherTest {
    private static int checked;
    private static final List<String> LABELS = List.of("Chrome", "Chrome Beta", "Clock", "Camera", "Google Maps", "Maps Go", "Café", "Duplicate", "Duplicate");
    private static final List<String> PACKAGES = List.of("com.android.chrome", "com.chrome.beta", "clock", "camera", "com.maps", "com.maps.go", "cafe", "duplicate.a", "duplicate.b");

    public static void main(String[] args) {
        match("Chrome", 0);
        match("  CHROME! ", 0);
        match("com.android.chrome", 0);
        match("chrom", 0, 1);
        match("chorme", 0);
        match("Camra", 3);
        match("maps", 4, 5);
        match("cafe", 6);
        match("Duplicate", 7, 8);
        match("duplicate.b", 8);
        match("no matching app");
        match("", 0, 1, 2, 3, 4, 5, 6, 7, 8);
        match("💚");
        try { Z0LiveAppMatcher.candidates("x".repeat(1001), LABELS, PACKAGES); throw new AssertionError("unbounded query"); }
        catch (IllegalArgumentException expected) { checked++; }
        System.out.println("PASS: " + checked + " z0live real-app candidate policy checks (not handset execution)");
    }

    private static void match(String query, Integer... expected) {
        List<Integer> actual = Z0LiveAppMatcher.candidates(query, LABELS, PACKAGES);
        if (!actual.equals(List.of(expected))) throw new AssertionError(query + " expected " + List.of(expected) + " got " + actual);
        checked++;
    }
}
