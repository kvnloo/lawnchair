import app.lawnchair.zer0.spaces.SplitProbe;
import app.lawnchair.zer0.spaces.SplitProbe.Mode;
import app.lawnchair.zer0.spaces.SplitProbe.Stage;
import app.lawnchair.zer0.spaces.SplitProbe.Observation;

public final class SplitProbeTest {
    private static int checked;
    private static void check(boolean value, String label) {
        if (!value) throw new AssertionError(label);
        checked++;
    }
    private static void rejected(Runnable action, String label) {
        try {
            action.run();
            throw new AssertionError("Expected rejection: " + label);
        } catch (IllegalArgumentException expected) {
            checked++;
        }
    }
    public static void main(String[] args) {
        String id = "7cb315a1-b0d2-4a7d-9daa-224493b29c8a";
        String pair = SplitProbe.formatReceipt(id, Mode.PAIR, Stage.BOTH_SENT,
                Observation.TWO_VISIBLE, 37, true, false);
        check(pair.contains("mode=pair"), "pair mode");
        check(pair.contains("launch_calls=both_sent"), "launched is not verified");
        check(pair.contains("user_reported=two_visible"), "human observation clearly tagged");
        check(pair.contains("os_verified=false"), "not falsely system verified");
        check(pair.contains("android_api=37"), "android API");
        check(pair.contains("freeform_feature_advertised=true"), "capability feature");
        check(!pair.contains("com.android"), "no app identifier");
        check(SplitProbe.modeOrNull("PAIR") == Mode.PAIR, "valid mode");
        check(SplitProbe.modeOrNull("unknown") == null, "bad mode");
        check(SplitProbe.stageOrDefault("unknown") == Stage.NOT_SENT, "bad stage");
        check(SplitProbe.observationOrDefault(null) == Observation.UNREPORTED,
                "missing report");
        String adjacent = SplitProbe.formatReceipt("a\nprivate", Mode.ADJACENT_TO_SPACES,
                Stage.FAILED_ADJACENT, Observation.NO_CHANGE, 36, false, true);
        check(adjacent.contains("id=redacted") && !adjacent.contains("private"),
                "untrusted ID sanitized");
        check(adjacent.contains("mode=adjacent_to_spaces"), "adjacent mode");
        String accessibility = SplitProbe.formatReceipt(id, Mode.ACCESSIBILITY_ACTION,
                Stage.ACCESSIBILITY_SENT, Observation.UNREPORTED, 37, false, true);
        check(accessibility.contains("mode=accessibility_action"), "explicit AX mode");
        check(accessibility.contains("launch_calls=accessibility_sent"), "only accepted by system");
        check(accessibility.contains("os_verified=false"), "AX accepted does not verify layout");
        String bounds = SplitProbe.formatReceipt(id, Mode.FREEFORM_BOUNDS,
                Stage.BOUNDS_SENT, Observation.FULLSCREEN_ONLY, 37, true, false);
        check(bounds.contains("mode=freeform_bounds"), "public bounds request mode");
        check(bounds.contains("launch_calls=bounds_sent"), "bounds sent without popup promise");
        check(bounds.contains("os_verified=false"), "bounds is not observed freeform state");
        check(adjacent.contains("spaces_in_multiwindow_at_request=true"),
                "source window state included");
        rejected(() -> SplitProbe.formatReceipt(id, null, Stage.NOT_SENT,
                Observation.UNREPORTED, 37, false, false), "null mode");
        rejected(() -> SplitProbe.formatReceipt(id, Mode.PAIR, Stage.NOT_SENT,
                Observation.UNREPORTED, 0, false, false), "invalid API");
        System.out.println("PASS: " + checked + " multiwindow probe checks");
    }
}
