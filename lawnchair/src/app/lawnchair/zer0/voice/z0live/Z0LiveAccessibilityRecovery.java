package app.lawnchair.zer0.voice.z0live;

import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.provider.Settings;
import com.android.launcher3.R;
import java.util.ArrayList;
import java.util.List;

/** Restores only service registration, never per-session screen-control consent or microphone capture. */
public final class Z0LiveAccessibilityRecovery {
    private static final String CHANNEL = "z0live_control_recovery";
    private static final int NOTICE = 8796;
    private static boolean notified;
    private static ComponentName component(Context context) {
        return new ComponentName(context, Z0LiveAccessibilityService.class);
    }
    public static boolean optedIn(Context context) {
        return context.getSharedPreferences("z0live_control", Context.MODE_PRIVATE).getBoolean("in_app_control", false);
    }
    public static synchronized void setOptedIn(Context context, boolean enabled) {
        // Same monitor as repair: OFF wins before any subsequent repair can read the preference.
        context.getSharedPreferences("z0live_control", Context.MODE_PRIVATE).edit().putBoolean("in_app_control", enabled).apply();
        if (!enabled) {
            notified = false;
            context.getSystemService(NotificationManager.class).cancel(NOTICE);
        } else check(context);
    }
    public static Intent settingsIntent(Context context) {
        return new Intent("android.settings.ACCESSIBILITY_DETAILS_SETTINGS")
                .putExtra("android.intent.extra.COMPONENT_NAME", component(context).flattenToString())
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
    }
    public static synchronized boolean enabled(Context context) {
        String raw = Settings.Secure.getString(context.getContentResolver(), Settings.Secure.ENABLED_ACCESSIBILITY_SERVICES);
        if (raw == null) return false;
        for (String entry : raw.split(":")) if (component(context).equals(ComponentName.unflattenFromString(entry))) return true;
        return false;
    }
    public static synchronized void check(Context context) {
        boolean opted = optedIn(context);
        boolean enabled = enabled(context);
        boolean writable = context.checkSelfPermission("android.permission.WRITE_SECURE_SETTINGS") == PackageManager.PERMISSION_GRANTED;
        Z0LiveAccessibilityRecoveryPolicy.Action action = Z0LiveAccessibilityRecoveryPolicy.decide(opted, enabled, writable);
        if (action == Z0LiveAccessibilityRecoveryPolicy.Action.RESTORE) {
            try {
                // Re-read immediately before merging; preserve every unrelated service verbatim.
                String raw = Settings.Secure.getString(context.getContentResolver(), Settings.Secure.ENABLED_ACCESSIBILITY_SERVICES);
                List<String> entries = new ArrayList<>();
                if (raw != null && !raw.isEmpty()) for (String entry : raw.split(":")) if (!entry.isEmpty()) entries.add(entry);
                if (!enabled(context)) entries.add(component(context).flattenToString());
                boolean written = Settings.Secure.putString(context.getContentResolver(), Settings.Secure.ENABLED_ACCESSIBILITY_SERVICES, String.join(":", entries));
                if (written) Settings.Secure.putInt(context.getContentResolver(), Settings.Secure.ACCESSIBILITY_ENABLED, 1);
                enabled = enabled(context);
            } catch (SecurityException denied) { /* Permission may have been revoked since admission. */ }
        }
        NotificationManager manager = context.getSystemService(NotificationManager.class);
        if (!opted || enabled) {
            notified = false;
            manager.cancel(NOTICE);
        } else if (!notified) {
            manager.createNotificationChannel(new NotificationChannel(CHANNEL, context.getString(R.string.z0live_control_recovery_channel), NotificationManager.IMPORTANCE_DEFAULT));
            PendingIntent open = PendingIntent.getActivity(context, NOTICE, settingsIntent(context), PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE);
            try {
                manager.notify(NOTICE, new Notification.Builder(context, CHANNEL).setSmallIcon(R.drawable.z0live_mic)
                        .setContentTitle(context.getString(R.string.z0live_control_recovery_title))
                        .setContentText(context.getString(R.string.z0live_control_recovery_detail))
                        .setContentIntent(open).setAutoCancel(true).setOnlyAlertOnce(true).build());
                notified = true;
            } catch (SecurityException denied) { /* Tool calls still return permission_required. */ }
        }
    }
    private Z0LiveAccessibilityRecovery() {}
}
