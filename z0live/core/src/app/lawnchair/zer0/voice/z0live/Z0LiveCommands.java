package app.lawnchair.zer0.voice.z0live;

/** The only calls a launcher makes into the z0live companion. Each launcher sends the same intent. */
public final class Z0LiveCommands {
    public static final String START = "z0live.action.START", MUTE = "z0live.action.MUTE", END = "z0live.action.END";
    /** Senders address the companion by package and keep its command task out of Recents. */
    public static final String PACKAGE = "app.zer0.z0live";
    /** FLAG_ACTIVITY_NEW_TASK | FLAG_ACTIVITY_EXCLUDE_FROM_RECENTS, literal so this class stays plain JVM. */
    public static final int SEND_FLAGS = 0x10000000 | 0x00800000;

    public enum Effect { OPEN_AND_START, TOGGLE_MUTE, END, NONE }

    private Z0LiveCommands() {}

    /** START only ever starts (through the visible controls, which own the mic permission); MUTE/END only touch a live call. */
    public static Effect decide(String action, boolean active) {
        if (START.equals(action)) return active ? Effect.NONE : Effect.OPEN_AND_START;
        if (MUTE.equals(action)) return active ? Effect.TOGGLE_MUTE : Effect.NONE;
        if (END.equals(action)) return active ? Effect.END : Effect.NONE;
        return Effect.NONE;
    }
}
