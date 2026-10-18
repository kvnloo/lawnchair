package app.lawnchair.zer0.spaces;

/** Pure geometry, not a permission to move another Android app's task. */
public final class WindowBoundsRequest {
    private WindowBoundsRequest() {}

    /**
     * Return a centered, bounded rectangle as [left, top, right, bottom] screen pixels.
     * Callers must separately check platform support and observe the real window outcome.
     */
    public static int[] centered(int left, int top, int right, int bottom) {
        long width = (long) right - left;
        long height = (long) bottom - top;
        if (width < 420 || height < 420 || width > 100_000 || height > 100_000) {
            return null;
        }
        long requestedWidth = width * 72 / 100;
        long requestedHeight = height * 65 / 100;
        if (requestedWidth < 300 || requestedHeight < 300) return null;
        long x = left + (width - requestedWidth) / 2;
        long y = top + (height - requestedHeight) / 2;
        long x2 = x + requestedWidth;
        long y2 = y + requestedHeight;
        if (x < Integer.MIN_VALUE || y < Integer.MIN_VALUE
                || x2 > Integer.MAX_VALUE || y2 > Integer.MAX_VALUE) {
            return null;
        }
        return new int[] {(int) x, (int) y, (int) x2, (int) y2};
    }

    /**
     * API 30 supplies WindowManager.getMaximumWindowMetrics(). The freeform feature
     * flag only enables a best-effort *request*, never a promised popup.
     */
    public static boolean eligible(int apiLevel, boolean freeformAdvertised, int[] bounds) {
        return apiLevel >= 30 && freeformAdvertised && bounds != null
                && bounds.length == 4 && bounds[2] > bounds[0] && bounds[3] > bounds[1];
    }

    public static String boundsCsv(int[] bounds) {
        if (bounds == null || bounds.length != 4
                || bounds[0] < 0 || bounds[1] < 0
                || bounds[2] <= bounds[0] || bounds[3] <= bounds[1]) {
            throw new IllegalArgumentException("Invalid bounds");
        }
        return bounds[0] + "," + bounds[1] + "," + bounds[2] + "," + bounds[3];
    }
}
