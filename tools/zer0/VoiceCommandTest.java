import app.lawnchair.zer0.voice.VoiceCommand;
import app.lawnchair.zer0.voice.VoiceCommand.Kind;

/** Standalone JVM contract tests; independent from Android SDK and recognition services. */
public final class VoiceCommandTest {
    private static int checked;

    private static void expect(String spoken, Kind kind, String target) {
        VoiceCommand actual = VoiceCommand.parse(spoken);
        if (actual.kind != kind || !actual.target.equals(target)) {
            throw new AssertionError("Expected " + kind + "(" + target + ") from '" + spoken
                    + "', got " + actual.kind + "(" + actual.target + ")");
        }
        checked++;
    }

    public static void main(String[] args) {
        expect("Switch to BUILD", Kind.SWITCH_SPACE, "build");
        expect("Go to life.", Kind.SWITCH_SPACE, "life");
        expect("open learn space", Kind.SWITCH_SPACE, "learn");
        expect("now space", Kind.SWITCH_SPACE, "now");
        expect("switch to finance", Kind.SWITCH_SPACE, "finance");
        expect("switch to Science Lab", Kind.SWITCH_SPACE, "science lab");
        expect("start focus", Kind.START_FOCUS, "");
        expect("start focus timer", Kind.START_FOCUS, "");
        expect("focus for 25 minutes", Kind.START_FOCUS, "");
        expect("start a 25 minute focus timer", Kind.START_FOCUS, "");
        expect("end focus", Kind.STOP_FOCUS, "");
        expect("cancel focus", Kind.STOP_FOCUS, "");
        expect("GO HOME!", Kind.SHOW_HOME, "");
        expect("back", Kind.GO_BACK, "");
        expect("show recent apps", Kind.SHOW_RECENTS, "");
        expect("Open Calculator", Kind.OPEN_APP, "calculator");
        expect("launch Café", Kind.OPEN_APP, "café");
        expect("open app Camera", Kind.OPEN_APP, "camera");
        expect("Search for Maps", Kind.SEARCH_APPS, "maps");
        expect("find app Camera", Kind.SEARCH_APPS, "camera");
        expect("Open", Kind.UNKNOWN, "");
        expect("open app", Kind.UNKNOWN, "");
        expect("search for", Kind.UNKNOWN, "");
        expect("Send a message to Mom", Kind.UNKNOWN, "");
        expect("delete all messages", Kind.UNKNOWN, "");
        expect("start five minute timer", Kind.UNKNOWN, "");
        expect("tap confirm payment", Kind.UNKNOWN, "");
        expect("go home and delete photos", Kind.UNKNOWN, "");
        expect("", Kind.UNKNOWN, "");
        expect(null, Kind.UNKNOWN, "");
        expect("x".repeat(161), Kind.UNKNOWN, "");
        System.out.println("PASS: " + checked + " voice command grammar checks");
    }
}
