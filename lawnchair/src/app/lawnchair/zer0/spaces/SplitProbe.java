package app.lawnchair.zer0.spaces;

import java.util.Locale;

/**
 * An intentionally minimal, local receipt for Samsung/Android multi-window experiments.
 *
 * <p>Launch API acceptance and user reports are NOT OS-level verification of window layout.
 * No application packages, screen contents, audio or hardware identifiers are exported.
 */
public final class SplitProbe {
    public enum Mode {
        PAIR,
        ADJACENT_TO_SPACES,
        ACCESSIBILITY_ACTION,
        FREEFORM_BOUNDS
    }

    public enum Stage {
        NOT_SENT,
        FIRST_SENT,
        BOTH_SENT,
        FAILED_FIRST,
        FAILED_SECOND,
        ADJACENT_SENT,
        FAILED_ADJACENT,
        ACCESSIBILITY_SENT,
        FAILED_ACCESSIBILITY,
        BOUNDS_SENT,
        FAILED_BOUNDS
    }

    public enum Observation {
        UNREPORTED,
        TWO_VISIBLE,
        FULLSCREEN_ONLY,
        ONE_VISIBLE,
        NO_CHANGE
    }

    private SplitProbe() {}

    public static Mode modeOrNull(String value) {
        try {
            return Mode.valueOf(value);
        } catch (IllegalArgumentException | NullPointerException ignored) {
            return null;
        }
    }

    public static Stage stageOrDefault(String value) {
        try {
            return Stage.valueOf(value);
        } catch (IllegalArgumentException | NullPointerException ignored) {
            return Stage.NOT_SENT;
        }
    }

    public static Observation observationOrDefault(String value) {
        try {
            return Observation.valueOf(value);
        } catch (IllegalArgumentException | NullPointerException ignored) {
            return Observation.UNREPORTED;
        }
    }

    /**
     * Copy-friendly metadata only. A user-observed result is explicitly distinguishable
     * from an independently verified OS window state, which this build cannot read.
     */
    public static String formatReceipt(
            String requestId,
            Mode mode,
            Stage stage,
            Observation observation,
            int androidApi,
            boolean freeformFeatureAdvertised,
            boolean launcherWasInMultiwindow) {
        if (mode == null || stage == null || observation == null || androidApi < 1) {
            throw new IllegalArgumentException("Invalid probe metadata");
        }
        String safeId = requestId != null && requestId.matches("[A-Za-z0-9-]{1,64}")
                ? requestId : "redacted";
        return "hyprspaces-probe:v1"
                + "\nid=" + safeId
                + "\nmode=" + mode.name().toLowerCase(Locale.ROOT)
                + "\nlaunch_calls=" + stage.name().toLowerCase(Locale.ROOT)
                + "\nuser_reported=" + observation.name().toLowerCase(Locale.ROOT)
                + "\nos_verified=false"
                + "\nandroid_api=" + androidApi
                + "\nfreeform_feature_advertised=" + freeformFeatureAdvertised
                + "\nspaces_in_multiwindow_at_request=" + launcherWasInMultiwindow;
    }
}
