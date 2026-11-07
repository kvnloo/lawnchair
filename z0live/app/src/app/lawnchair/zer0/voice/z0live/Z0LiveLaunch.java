package app.lawnchair.zer0.voice.z0live;

import android.content.Intent;

/** Launch flags for the z0live call screen: a call task must never be a Recents entry. */
public final class Z0LiveLaunch {
    /** On every intent that opens the call screen, whatever flags the original caller sent. */
    public static final int CALL_FLAGS = Intent.FLAG_ACTIVITY_EXCLUDE_FROM_RECENTS;
    /** LinkActivity's relaunch out of the trampoline task. */
    public static final int RELAUNCH_FLAGS = Intent.FLAG_ACTIVITY_NEW_TASK | CALL_FLAGS;

    private Z0LiveLaunch() {}
}
