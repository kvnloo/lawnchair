package app.lawnchair.zer0.voice.z0live;

import android.app.Activity;
import android.app.PendingIntent;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.os.Build;
import android.os.SystemClock;
import java.util.UUID;

/**
 * NOOP receives an ephemeral PendingIntent capability ONLY after the owner
 * opens gesture setup from Lawnchair. The target receiver is not exported.
 * Mutable fill-in fields carry live event sequence/time; the immutable grant
 * is its creator's private component and the per-session nonce.
 */
public final class OuraGestureReceiver extends BroadcastReceiver {
    public static final String EXTRA_CAPABILITY = "app.lawnchair.zer0.voice.z0live.CAPABILITY";
    public static final String EXTRA_GESTURE = "app.lawnchair.zer0.voice.z0live.OURA_GESTURE";
    public static final String EXTRA_SEQUENCE = "app.lawnchair.zer0.voice.z0live.OURA_SEQUENCE";
    public static final String EXTRA_TIMESTAMP = "app.lawnchair.zer0.voice.z0live.OURA_TIMESTAMP";
    private static final String EXTRA_NONCE = "app.lawnchair.zer0.voice.z0live.OURA_NONCE";
    private static final String ACTION = "app.lawnchair.zer0.voice.z0live.OURA_CAPABILITY_EVENT";
    private static final long TTL_MS = 5 * 60 * 1000L;

    private static String nonce;
    private static int generation = -1;
    private static long expiresAt;
    private static long lastAcceptedAt = -1;
    private static long lastSequence;

    /** Only invoked by a visible owner tap on z0live's gesture onboarding button. */
    public static synchronized PendingIntent arm(Activity activity) {
        int activeGeneration = Z0LiveService.gestureSessionGeneration();
        if (activeGeneration < 0) return null;
        nonce = UUID.randomUUID().toString();
        generation = activeGeneration;
        expiresAt = SystemClock.elapsedRealtime() + TTL_MS;
        lastSequence = 0;
        lastAcceptedAt = -1;
        Intent target = new Intent(activity, OuraGestureReceiver.class)
                .setAction(ACTION).putExtra(EXTRA_NONCE, nonce);
        // Android's mutable PendingIntent lets the trusted NOOP app include the
        // monotonically increasing sequence and elapsed-time timestamp.
        int flags = PendingIntent.FLAG_CANCEL_CURRENT |
                (Build.VERSION.SDK_INT >= 31 ? PendingIntent.FLAG_MUTABLE : 0);
        return PendingIntent.getBroadcast(activity, 0x3366, target, flags);
    }

    @Override
    public void onReceive(Context context, Intent intent) {
        boolean approved;
        synchronized (OuraGestureReceiver.class) {
            long now = SystemClock.elapsedRealtime();
            boolean authenticated = intent != null
                    && ACTION.equals(intent.getAction())
                    && nonce != null && nonce.equals(intent.getStringExtra(EXTRA_NONCE))
                    && now < expiresAt && generation == Z0LiveService.gestureSessionGeneration();
            OuraGestureAdmission.Decision decision = authenticated
                    ? OuraGestureAdmission.evaluate(true, true, true,
                            intent.getStringExtra(EXTRA_GESTURE),
                            intent.getLongExtra(EXTRA_TIMESTAMP, -1),
                            now,
                            intent.getLongExtra(EXTRA_SEQUENCE, -1),
                            lastSequence, lastAcceptedAt)
                    : OuraGestureAdmission.Decision.REJECT;
            approved = decision == OuraGestureAdmission.Decision.TOGGLE_EXISTING_SESSION_MUTE;
            if (approved) {
                lastSequence = intent.getLongExtra(EXTRA_SEQUENCE, -1);
                lastAcceptedAt = now;
            }
        }
        if (approved) Z0LiveService.toggleMute(context);
    }
}
