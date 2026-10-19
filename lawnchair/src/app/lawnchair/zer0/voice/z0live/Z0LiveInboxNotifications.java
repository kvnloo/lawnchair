package app.lawnchair.zer0.voice.z0live;

import android.Manifest;
import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.app.job.JobInfo;
import android.app.job.JobParameters;
import android.app.job.JobScheduler;
import android.app.job.JobService;
import android.content.ComponentName;
import android.content.Context;
import android.content.pm.PackageManager;
import android.os.Build;
import com.android.launcher3.R;
import java.io.IOException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import okhttp3.OkHttpClient;
import org.json.JSONObject;

/** Best-effort, silent inbox notifications while inactive. This job never starts voice or executes tools. */
public final class Z0LiveInboxNotifications extends JobService {
    private static final int JOB = 8794;
    private static final String CHANNEL = "z0live_inbox_silent";
    private final ExecutorService worker = Executors.newSingleThreadExecutor();
    private volatile boolean stopped;

    public static void schedule(Context context) {
        context.getSystemService(JobScheduler.class).schedule(new JobInfo.Builder(JOB,
                new ComponentName(context, Z0LiveInboxNotifications.class))
                .setRequiredNetworkType(JobInfo.NETWORK_TYPE_ANY).setPeriodic(15 * 60 * 1000L).build());
    }
    public static void cancel(Context context) { context.getSystemService(JobScheduler.class).cancel(JOB); }

    public static void show(Context context, JSONObject item) {
        if (Build.VERSION.SDK_INT >= 33 && context.checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED) return;
        NotificationManager manager = context.getSystemService(NotificationManager.class);
        if (!manager.areNotificationsEnabled()) return;
        manager.createNotificationChannel(new NotificationChannel(CHANNEL, context.getString(R.string.z0live_inbox_notification_channel), NotificationManager.IMPORTANCE_LOW));
        PendingIntent open = PendingIntent.getActivity(context, 8794, Z0LiveActivity.intent(context, false),
                PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE);
        Notification publicVersion = new Notification.Builder(context, CHANNEL).setSmallIcon(R.drawable.z0live_mic)
                .setContentTitle(context.getString(R.string.z0live_inbox_notification_title)).setSilent(true).build();
        String say = item.optString("say", "");
        if (say.length() > 4000) say = say.substring(0, 4000);
        Notification reply = new Notification.Builder(context, CHANNEL).setSmallIcon(R.drawable.z0live_mic)
                .setContentTitle(context.getString(R.string.z0live_inbox_notification_title)).setContentText(say)
                .setStyle(new Notification.BigTextStyle().bigText(say)).setContentIntent(open)
                .setVisibility(Notification.VISIBILITY_PRIVATE).setPublicVersion(publicVersion)
                .setAutoCancel(true).setOnlyAlertOnce(true).setSilent(true).build();
        manager.notify("z0live_inbox", (int) (item.optLong("id") % Integer.MAX_VALUE), reply);
    }

    @Override public boolean onStartJob(JobParameters parameters) {
        stopped = false;
        worker.execute(() -> {
            boolean retry = false;
            try {
                if (!Z0LiveService.hasActiveSession()) {
                    Z0LiveStore store = new Z0LiveStore(this);
                    PairingValidation.Pairing pairing = store.pairing();
                    if (pairing != null) {
                        Z0LiveProtocol backend = new Z0LiveProtocol(new OkHttpClient(), pairing.backendUrl, pairing.token);
                        long cursor = store.inboxCursor(pairing);
                        if (cursor < 0) store.setInboxCursor(pairing, backend.inboxHead());
                        else {
                            Z0LiveProtocol.InboxPage page = backend.inboxPage(cursor);
                            for (int i = 0; i < page.messages.length() && !stopped; i++) {
                                if (Z0LiveService.hasActiveSession()) break;
                                JSONObject item = page.messages.optJSONObject(i);
                                if (item != null) {
                                    show(this, item);
                                    store.setInboxCursor(pairing, item.optLong("id"));
                                }
                            }
                        }
                    }
                }
            } catch (IOException | RuntimeException error) { retry = true; }
            if (!stopped) jobFinished(parameters, retry);
        });
        return true;
    }
    @Override public boolean onStopJob(JobParameters parameters) { stopped = true; return true; }
    @Override public void onDestroy() { stopped = true; worker.shutdown(); super.onDestroy(); }
}
