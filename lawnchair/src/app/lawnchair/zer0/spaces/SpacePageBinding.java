package app.lawnchair.zer0.spaces;

/**
 * Validates one short-lived, explicit navigation request to an existing Launcher3
 * home screen ID. It does not create, delete or reorder workspace pages.
 */
public final class SpacePageBinding {
    public static final int NO_SCREEN = -1;
    public static final long MAX_REQUEST_AGE_MS = 10_000;

    private SpacePageBinding() {}

    public static boolean canNavigate(int screenId, long requestedAtElapsedMs, long nowElapsedMs) {
        return screenId >= 0 && requestedAtElapsedMs > 0
                && nowElapsedMs >= requestedAtElapsedMs
                && nowElapsedMs - requestedAtElapsedMs <= MAX_REQUEST_AGE_MS;
    }
}
