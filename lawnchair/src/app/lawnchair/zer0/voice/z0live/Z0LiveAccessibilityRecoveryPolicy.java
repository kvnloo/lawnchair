package app.lawnchair.zer0.voice.z0live;

/** Pure admission logic: absent opt-in is OFF, including on older installations. */
public final class Z0LiveAccessibilityRecoveryPolicy {
    public enum Action { NONE, RESTORE, NOTIFY }
    public static Action decide(boolean optedIn, boolean enabled, boolean canWrite) {
        if (!optedIn || enabled) return Action.NONE;
        return canWrite ? Action.RESTORE : Action.NOTIFY;
    }
    private Z0LiveAccessibilityRecoveryPolicy() {}
}
