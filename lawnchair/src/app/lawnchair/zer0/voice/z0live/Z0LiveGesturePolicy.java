package app.lawnchair.zer0.voice.z0live;

/**
 * The same admission is used for authenticated commands and standalone offline tests.
 * Owner mapping: double tap with no call = start a call, double tap during a call = toggle mute,
 * triple tap = end the call.
 */
public final class Z0LiveGesturePolicy {
    public enum Action { NONE, START, MUTE, END }
    private Z0LiveGesturePolicy() {}
    /**
     * Whether ring commands are admitted at all: Standby is armed, OR a call is live. A call that was
     * started any other way (controls, tile, the broker's adb relaunch) must still take mute and end
     * from the ring; otherwise the broker has no target and falls back to relaunching the Activity.
     */
    public static boolean accepts(boolean standby, boolean callRunning) { return standby || callRunning; }
    public static Action decide(boolean enabled, boolean ready, boolean active, boolean ending, String gesture) {
        if (!enabled || ending) return Action.NONE;
        if ("triple_tap".equals(gesture)) return active ? Action.END : Action.NONE;
        if (!ready) return Action.NONE;
        if ("double_tap".equals(gesture)) return active ? Action.MUTE : Action.START;
        return Action.NONE;
    }
}
