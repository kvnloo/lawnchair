import java.util.Arrays;
import java.util.Collections;
import app.lawnchair.zer0.voice.AppNameMatcher;
import app.lawnchair.zer0.voice.VoiceCommand;

public final class AppNameMatcherTest {
    private static int checked;

    private static void check(String target, int expected, String... labels) {
        int found = AppNameMatcher.uniqueIndex(target, Arrays.asList(labels));
        if (found != expected) {
            throw new AssertionError("Expected index " + expected + " for '" + target
                    + "', got " + found);
        }
        checked++;
    }

    public static void main(String[] args) {
        check("Maps", 1, "Camera", "Maps", "YouTube");
        check("maps", 0, "Maps");
        check(" Maps ", 0, "maps");
        check("Ｃａｍｅｒａ", 0, "Camera");
        check("Café", 0, "Café");
        check("Camera", AppNameMatcher.NOT_FOUND, "Camera Pro");
        check("camera", AppNameMatcher.AMBIGUOUS, "Camera", "CAMERA");
        check("Maps", AppNameMatcher.AMBIGUOUS, "Maps", "Maps");
        check("", AppNameMatcher.NOT_FOUND, "Maps");
        check(" ", AppNameMatcher.NOT_FOUND, "Maps");
        check(null, AppNameMatcher.NOT_FOUND, "Maps");
        check("bank", AppNameMatcher.NOT_FOUND, "Banking");
        if (AppNameMatcher.uniqueIndex("Maps", Collections.emptyList()) != AppNameMatcher.NOT_FOUND) {
            throw new AssertionError("Empty app list should not match");
        }
        checked++;
        if (AppNameMatcher.uniqueIndex("Maps", null) != AppNameMatcher.NOT_FOUND) {
            throw new AssertionError("Missing app list should not match");
        }
        checked++;
        VoiceCommand command = VoiceCommand.parse("Open Maps");
        if (command.kind != VoiceCommand.Kind.OPEN_APP
                || AppNameMatcher.uniqueIndex(command.target, Arrays.asList("Camera", "Maps")) != 1) {
            throw new AssertionError("Expected recognized Maps command to resolve uniquely");
        }
        checked++;
        System.out.println("PASS: " + checked + " app-label and parser integration checks");
    }
}
