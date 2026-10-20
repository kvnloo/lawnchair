package app.lawnchair.zer0.voice.z0live;

/** The same admission is used for authenticated commands and standalone offline tests. */
public final class Z0LiveGesturePolicy {
    public enum Action { NONE, START, MUTE, END }
    private Z0LiveGesturePolicy() {}
    public static Action decide(boolean enabled, boolean ready, boolean active, boolean ending, String gesture) {
        if (!enabled || ending) return Action.NONE;
        if ("triple_tap".equals(gesture)) return active ? Action.END : Action.NONE;
        if (!ready) return Action.NONE;
        if ("double_tap".equals(gesture)) return active ? Action.MUTE : Action.START;
        return Action.NONE;
    }
}
