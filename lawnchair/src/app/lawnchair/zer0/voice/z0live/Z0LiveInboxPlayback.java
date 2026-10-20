package app.lawnchair.zer0.voice.z0live;

/** Stable local receipt identity, independent of playback callbacks and service generations. */
public final class Z0LiveInboxPlayback {
    public static final class Receipt {
        public final long id;
        public final boolean success;
        private Receipt(long id, boolean success) { this.id = id; this.success = success; }
    }
    private long activeId;

    public void begin(long id) {
        if (id <= 0 || activeId != 0) throw new IllegalStateException("Invalid concurrent inbox playback");
        activeId = id;
    }

    /** Success, failure and explicit interruption are all terminal; duplicate callbacks do nothing. */
    public Receipt finish(boolean success) {
        if (activeId == 0) return null;
        Receipt result = new Receipt(activeId, success);
        activeId = 0;
        return result;
    }

    public static long cursorAfter(long previous, Receipt receipt) {
        return receipt == null ? previous : Math.max(previous, receipt.id);
    }
}
