package app.lawnchair.zer0.voice.z0live;

import android.app.KeyguardManager;
import android.content.Context;
import android.os.Handler;
import android.os.Looper;
import android.os.PowerManager;
import java.io.IOException;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
import okhttp3.MediaType;
import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.RequestBody;
import okhttp3.Response;
import org.json.JSONArray;
import org.json.JSONObject;

/** Authenticated, at-most-once command polling. Never initializes WebRTC or opens the microphone. */
final class Z0LiveGestureChannel implements AutoCloseable {
    private static volatile double lastActivity = System.currentTimeMillis() / 1000.0;
    private static final java.util.Set<Context> visibleOwners = java.util.Collections.newSetFromMap(new java.util.WeakHashMap<>());
    private static Z0LiveGestureChannel visiblePresence;
    static synchronized void visible(Context owner, boolean visible) {
        if (visible) visibleOwners.add(owner); else visibleOwners.remove(owner);
        if (!visibleOwners.isEmpty() && visiblePresence == null) {
            visiblePresence = new Z0LiveGestureChannel(owner, true);
            visiblePresence.start();
        } else if (visibleOwners.isEmpty() && visiblePresence != null) {
            visiblePresence.close();
            visiblePresence = null;
        }
    }
    private final Context context;
    private final Handler main = new Handler(Looper.getMainLooper());
    private final ScheduledExecutorService executor = Executors.newSingleThreadScheduledExecutor();
    private final OkHttpClient http = new OkHttpClient.Builder().callTimeout(2, TimeUnit.SECONDS)
            .followRedirects(false).followSslRedirects(false).build();
    private volatile boolean closed;
    private boolean screen;
    private final boolean assistant, presenceOnly;
    private int ticks;
    Z0LiveGestureChannel(Context context) { this(context, false); }
    private Z0LiveGestureChannel(Context context, boolean presenceOnly) {
        this.context = context.getApplicationContext();
        this.assistant = context instanceof Z0LiveAssistantService;
        this.presenceOnly = presenceOnly;
    }
    static void pin(Context context) {
        Z0LiveGestureChannel channel = new Z0LiveGestureChannel(context);
        channel.executor.execute(() -> {
            try {
                PairingValidation.Pairing pair = new Z0LiveStore(context).pairing();
                if (pair != null) channel.request(pair, "/v1/gesture/pin", Z0LiveService.object("ttl_seconds", 300));
            } catch (Exception ignored) { /* The next routing receipt is authoritative. */ }
            finally { channel.close(); }
        });
    }
    static void touch() { lastActivity = System.currentTimeMillis() / 1000.0; }
    void start() { executor.scheduleWithFixedDelay(this::tick, 0, presenceOnly ? 3000 : 500, TimeUnit.MILLISECONDS); }
    private JSONObject request(PairingValidation.Pairing pair, String path, JSONObject body) throws Exception {
        Request.Builder builder = new Request.Builder().url(pair.backendUrl + path)
                .header("Authorization", "Bearer " + pair.token);
        if (body != null) builder.post(RequestBody.create(body.toString(), MediaType.get("application/json")));
        try (Response response = http.newCall(builder.build()).execute()) {
            if (!response.isSuccessful() || response.body() == null) throw new IOException("Gesture broker unavailable");
            String raw = response.body().string();
            if (raw.length() > 32768) throw new IOException("Gesture response too large");
            return new JSONObject(raw);
        }
    }
    private void tick() {
        // A live call admits commands even without the Standby opt-in; the visible-only presence never does.
        boolean inCall = !presenceOnly && Z0LiveService.callRunning();
        if (((assistant || presenceOnly) && Z0LiveService.standbyEnabled()) || closed
                || !(inCall || context.getSharedPreferences("z0live_gestures", Context.MODE_PRIVATE).getBoolean("enabled", false))) return;
        try {
            PairingValidation.Pairing pair = new Z0LiveStore(context).pairing();
            if (pair == null) return;
            boolean unlocked = context.getSystemService(PowerManager.class).isInteractive()
                    && !context.getSystemService(KeyguardManager.class).isKeyguardLocked();
            if (unlocked && !screen) touch();
            screen = unlocked;
            if (presenceOnly || ticks++ % 6 == 0) {
                double now = System.currentTimeMillis() / 1000.0;
                request(pair, "/v1/gesture/activity", Z0LiveService.object("kind", "phone",
                        "activity_ts", lastActivity, "available", true,
                        "ready", inCall || Z0LiveService.standbyEnabled() || Z0LiveAssistantService.mayStart(context)));
            }
            // Visible-only presence can advertise ready=false after process loss, enabling the
            // separately opted-in ADB fallback. It never claims commands or starts a service.
            if (presenceOnly) return;
            JSONArray commands = request(pair, "/v1/gesture/commands", null).optJSONArray("commands");
            if (commands == null) return;
            for (int i = 0; i < commands.length(); i++) {
                JSONObject command = commands.getJSONObject(i);
                if (closed || command.optDouble("expires") < System.currentTimeMillis() / 1000.0) continue;
                main.post(() -> { if (!closed) Z0LiveService.gesture(context, command.optString("gesture")); });
                // The broker's claim is not a connected/audio receipt. Service status is authoritative.
            }
        } catch (Exception unavailable) { /* No credential, body, network or private exception logging. */ }
    }
    @Override public void close() {
        closed = true;
        executor.shutdownNow();
        http.dispatcher().cancelAll();
        new Thread(() -> http.connectionPool().evictAll(), "z0live-gesture-close").start();
    }
}
