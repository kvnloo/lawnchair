package app.lawnchair.zer0.voice.z0live;

import android.Manifest;
import android.app.Activity;
import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.app.Service;
import android.content.Context;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.content.pm.ServiceInfo;
import android.media.AudioAttributes;
import android.media.AudioFocusRequest;
import android.media.AudioManager;
import android.os.Build;
import android.os.Handler;
import android.os.IBinder;
import android.os.Looper;
import android.util.Log;
import com.android.launcher3.R;
import android.speech.tts.TextToSpeech;
import android.speech.tts.UtteranceProgressListener;
import java.io.IOException;
import java.util.HashSet;
import java.util.Locale;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicInteger;
import okhttp3.OkHttpClient;
import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

/** Explicit, non-sticky native microphone foreground session. Never started by a receiver. */
public final class Z0LiveService extends Service {
    private static final String CHANNEL = "z0live_voice";
    private static final int NOTIFICATION = 8791;
    private static final String START = "z0live.START", END = "z0live.END", MUTE = "z0live.MUTE";
    private static volatile Z0LiveService current;
    private final Handler main = new Handler(Looper.getMainLooper());
    private final ExecutorService network = Executors.newSingleThreadExecutor();
    private final OkHttpClient http = new OkHttpClient.Builder()
            .connectTimeout(8, java.util.concurrent.TimeUnit.SECONDS)
            .readTimeout(15, java.util.concurrent.TimeUnit.SECONDS)
            .callTimeout(20, java.util.concurrent.TimeUnit.SECONDS)
            .followRedirects(false).followSslRedirects(false).build();
    private final Z0LiveTelemetry telemetry = new Z0LiveTelemetry();
    private final Z0LiveTurnState turn = new Z0LiveTurnState();
    private final AtomicInteger telemetryFailed = new AtomicInteger();
    private final Z0LiveAudioState audio = new Z0LiveAudioState();
    private final Set<String> toolCalls = new HashSet<>();
    private final java.util.Map<String, String> toolNames = new java.util.HashMap<>();
    private Z0LiveProtocol backend;
    private Z0LivePeer peer;
    private TextToSpeech tts;
    private AudioManager audioManager;
    private AudioFocusRequest focusRequest;
    private boolean running, pendingNetwork, connecting, telemetryBusy, inboxBusy, ttsReady;
    private boolean speaking, ending, closingResources;
    private int generation, reachabilityAttempts, pendingTools;
    private Z0LiveStore store;
    private long inboxCursor;
    private String sessionId = "", status = "", caption = "", error = "";
    private String speakingInboxId;
    private volatile String brokerSessionToEnd = "";
    private JSONObject inboxItem;
    private final Runnable healthRetry = () -> checkBackend();
    private final Runnable connectionTimeout = () -> fail(getString(R.string.z0live_service_connection_timeout));
    private final Runnable inboxTimeout = () -> finishInbox(false);
    private final Runnable telemetryTick = new Runnable() {
        public void run() {
            if (!running) return;
            flush(false);
            main.postDelayed(this, 2500);
        }
    };
    private final Runnable inboxTick = new Runnable() {
        public void run() {
            if (!running) return;
            pollInbox();
            main.postDelayed(this, 3000);
        }
    };

    /** Called only by the resumed, permission-granted entry Activity. */
    public static void start(Activity activity) {
        if (activity.isFinishing() || activity.isDestroyed()
                || activity.checkSelfPermission(Manifest.permission.RECORD_AUDIO) != PackageManager.PERMISSION_GRANTED) {
            throw new IllegalStateException("Open z0live and grant microphone permission first");
        }
        if (current != null && (current.running || current.closingResources)) return;
        activity.startForegroundService(new Intent(activity, Z0LiveService.class).setAction(START));
    }
    public static void end(Context context) {
        Z0LiveService service = current;
        if (service != null) service.main.post(() -> service.finishSession("", true));
    }
    public static void toggleMute(Context context) {
        Z0LiveService service = current;
        if (service != null) service.main.post(() -> service.mute());
    }
    public static void retry(Context context) {
        Z0LiveService service = current;
        if (service != null) service.main.post(() -> {
            if (service.running && service.pendingNetwork && !service.connecting) {
                service.main.removeCallbacks(service.healthRetry);
                service.checkBackend();
            }
        });
    }

    @Override public void onCreate() {
        super.onCreate();
        current = this;
        status = getString(R.string.z0live_service_connecting);
        NotificationChannel channel = new NotificationChannel(CHANNEL, getString(R.string.z0live_notification_channel), NotificationManager.IMPORTANCE_LOW);
        channel.setDescription(getString(R.string.z0live_notification_description));
        getSystemService(NotificationManager.class).createNotificationChannel(channel);
    }
    @Override public IBinder onBind(Intent intent) { return null; }
    @Override public int onStartCommand(Intent intent, int flags, int startId) {
        String action = intent == null ? "" : intent.getAction();
        if (END.equals(action)) { finishSession("", true); return START_NOT_STICKY; }
        if (MUTE.equals(action)) { if (running) mute(); else stopSelf(); return START_NOT_STICKY; }
        if (!START.equals(action)) { stopSelf(); return START_NOT_STICKY; }
        if (running || ending) return START_NOT_STICKY;
        // This must precede crypto initialization, backend calls and native audio startup.
        try {
            if (Build.VERSION.SDK_INT >= 30) {
                startForeground(NOTIFICATION, notification(), ServiceInfo.FOREGROUND_SERVICE_TYPE_MICROPHONE);
            } else {
                startForeground(NOTIFICATION, notification());
            }
        } catch (RuntimeException e) {
            error = getString(R.string.z0live_service_foreground_failed);
            status = getString(R.string.z0live_service_error);
            publish();
            stopSelf();
            return START_NOT_STICKY;
        }
        running = true;
        generation++;
        sessionId = "";
        try {
            store = new Z0LiveStore(this);
            PairingValidation.Pairing pairing = store.pairing();
            inboxCursor = store.inboxCursor();
            if (pairing == null) { fail(getString(R.string.z0live_service_pair_required)); return START_NOT_STICKY; }
            backend = new Z0LiveProtocol(http, pairing.backendUrl, pairing.token);
            event("session_start", "", object("client", "lawnchair"));
            main.post(telemetryTick);
            checkBackend();
        } catch (RuntimeException e) {
            fail(getString(R.string.z0live_service_pairing_unreadable));
        }
        return START_NOT_STICKY;
    }

    private Notification notification() {
        PendingIntent open = PendingIntent.getActivity(this, 8791, Z0LiveActivity.intent(this, false),
                PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE);
        PendingIntent end = PendingIntent.getService(this, 8792,
                new Intent(this, Z0LiveService.class).setAction(END), PendingIntent.FLAG_IMMUTABLE);
        PendingIntent mute = PendingIntent.getService(this, 8793,
                new Intent(this, Z0LiveService.class).setAction(MUTE), PendingIntent.FLAG_IMMUTABLE);
        return new Notification.Builder(this, CHANNEL)
                .setSmallIcon(R.drawable.z0live_mic)
                .setContentTitle(getString(R.string.z0live_notification_title, status, audio.isMuted() ? getString(R.string.z0live_notification_muted) : ""))
                .setContentText(error.isEmpty() ? getString(R.string.z0live_notification_detail) : error)
                .setContentIntent(open).setOngoing(true).setOnlyAlertOnce(true)
                .setCategory(Notification.CATEGORY_SERVICE)
                .addAction(new Notification.Action.Builder(null, getString(audio.isMuted() ? R.string.z0live_unmute : R.string.z0live_mute), mute).build())
                .addAction(new Notification.Action.Builder(null, getString(R.string.z0live_end), end).build()).build();
    }
    private void publish() {
        if (current != null && current != this) return;
        Z0LiveStatus.publish(new Z0LiveStatus.Snapshot(status, caption, error, audio.isMuted(),
                running || closingResources, pendingNetwork, telemetryFailed.get() + telemetry.failedCount()));
        if (running) getSystemService(NotificationManager.class).notify(NOTIFICATION, notification());
    }
    private boolean valid(int id) { return running && generation == id; }
    private void mute() {
        if (!running) return;
        audio.toggleMute();
        applyAudio();
        event("mute", "", object("muted", audio.isMuted()));
        publish();
    }
    private void applyAudio() {
        if (peer != null) peer.audio(audio.microphoneEnabled(), audio.realtimeSpeakerEnabled());
    }

    private void checkBackend() {
        if (!running || connecting || reachabilityAttempts >= 6) return;
        int id = generation;
        reachabilityAttempts++;
        connecting = true;
        status = getString(R.string.z0live_service_connecting);
        publish();
        network.execute(() -> {
            try {
                JSONObject health = backend.health();
                if (!health.optBoolean("ok")) throw new IOException("Backend is not healthy");
                main.post(() -> {
                    if (!valid(id)) return;
                    connecting = false;
                    pendingNetwork = false;
                    error = "";
                    beginRealtime(id);
                });
            } catch (IOException e) {
                main.post(() -> {
                    if (!valid(id)) return;
                    connecting = false;
                    pendingNetwork = true;
                    status = getString(R.string.z0live_service_error);
                    error = getString(R.string.z0live_service_backend_retry) + reachabilityAttempts + "/6.";
                    publish();
                    if (reachabilityAttempts < 6) main.postDelayed(healthRetry, Math.min(30000, 3000L << (reachabilityAttempts - 1)));
                    else fail(getString(R.string.z0live_service_backend_unreachable));
                });
            }
        });
    }

    private void beginRealtime(int id) {
        if (!valid(id)) return;
        main.postDelayed(connectionTimeout, 45000);
        network.execute(() -> {
            try {
                Z0LiveProtocol.Session session = backend.session(object("app", getPackageName(), "screen", "z0live voice"), Z0LivePhoneTools.schemas(this));
                brokerSessionToEnd = session.sessionId;
                main.post(() -> {
                    if (!valid(id)) return;
                    if (session.sessionId == null || session.sessionId.isEmpty()) { fail(getString(R.string.z0live_service_missing_session)); return; }
                    sessionId = session.sessionId;
                    event("session_ready", "", object("model", session.model, "voice", session.voice));
                    try {
                        if (!requestAudioFocus()) { fail(getString(R.string.z0live_service_audio_focus_unavailable)); return; }
                        createTts(id);
                        peer = new Z0LivePeer(this, main, new Z0LivePeer.Listener() {
                            public void onOffer(String sdp) {
                                if (!valid(id)) return;
                                network.execute(() -> {
                                    try {
                                        String answer = backend.exchangeSdp(session.secret, sdp);
                                        main.post(() -> { if (valid(id) && peer != null) peer.answer(answer); });
                                    } catch (IOException e) {
                                        main.post(() -> { if (valid(id)) fail(getString(R.string.z0live_service_sdp_failed)); });
                                    }
                                });
                            }
                            public void onReady() {
                                if (!valid(id)) return;
                                main.removeCallbacks(connectionTimeout);
                                audio.setConnected(true);
                                status = getString(R.string.z0live_service_listening);
                                error = "";
                                applyAudio();
                                Log.i("z0live", "connected model=" + session.model + " voice=" + session.voice);
                                event("connected", "", object("model", session.model, "voice", session.voice));
                                publish();
                                main.post(inboxTick);
                            }
                            public void onEvent(String json) { if (valid(id)) realtimeEvent(json); }
                            public void onError(String message) { if (valid(id)) fail(message); }
                        });
                    } catch (RuntimeException e) { fail(getString(R.string.z0live_service_audio_start_failed)); }
                });
            } catch (IOException e) {
                main.post(() -> { if (valid(id)) fail(getString(R.string.z0live_service_session_failed)); });
            }
        });
    }

    private boolean requestAudioFocus() {
        audioManager = getSystemService(AudioManager.class);
        focusRequest = new AudioFocusRequest.Builder(AudioManager.AUDIOFOCUS_GAIN_TRANSIENT)
                .setAudioAttributes(new AudioAttributes.Builder().setUsage(AudioAttributes.USAGE_VOICE_COMMUNICATION)
                        .setContentType(AudioAttributes.CONTENT_TYPE_SPEECH).build())
                .setOnAudioFocusChangeListener(change -> {
                    if (!running) return;
                    boolean gained = change == AudioManager.AUDIOFOCUS_GAIN;
                    audio.setFocus(gained);
                    if (!gained && inboxItem != null) {
                        if (tts != null) tts.stop();
                        finishInbox(false);
                    }
                    applyAudio();
                    if (!gained) { error = getString(R.string.z0live_service_audio_focus_lost); status = getString(R.string.z0live_service_error); }
                    else { error = ""; status = getString(speaking ? R.string.z0live_service_speaking : R.string.z0live_service_listening); }
                    publish();
                }, main).build();
        boolean granted = audioManager.requestAudioFocus(focusRequest) == AudioManager.AUDIOFOCUS_REQUEST_GRANTED;
        audio.setFocus(granted);
        if (granted) audioManager.setMode(AudioManager.MODE_IN_COMMUNICATION);
        return granted;
    }

    /** Only reachable by the debug source-set receiver; never starts a microphone session. */
    public static void debugAudioStats() {
        Z0LiveService service = current;
        if (service != null && service.peer != null) service.peer.logDebugAudioStats();
    }
    public static boolean debugReady() {
        Z0LiveService service = current;
        return com.android.launcher3.BuildConfig.DEBUG && service != null && service.running
                && service.peer != null && service.audio.isConnected() && service.inboxItem == null
                && service.pendingInbox == null && service.pendingTools == 0 && service.turn.canSpeakInbox();
    }
    public static void debugMute(boolean muted) {
        Z0LiveService service = current;
        if (com.android.launcher3.BuildConfig.DEBUG && service != null && service.running && service.audio.isMuted() != muted) service.mute();
    }
    public static boolean injectDebugText(String text) {
        Z0LiveService service = current;
        if (!debugReady() || Looper.myLooper() != Looper.getMainLooper() || text == null || text.isBlank() || text.length() > 2000) return false;
        service.turn.responseStarted();
        service.caption = "You: " + text;
        service.event("user_transcript", text, object("source", "debug_text"));
        service.peer.send(object("type", "conversation.item.create", "item", object("type", "message", "role", "user", "content", new JSONArray().put(object("type", "input_text", "text", text)))).toString());
        service.peer.send(object("type", "response.create").toString());
        Log.i("z0live", "debug_turn injected");
        service.publish();
        return true;
    }

    private void realtimeEvent(String raw) {
        try {
            JSONObject message = new JSONObject(raw);
            String type = message.optString("type");
            switch (type) {
                case "conversation.item.input_audio_transcription.completed":
                    String input = bounded(message.optString("transcript"), 8000);
                    caption = "You: " + input;
                    event(type, input, new JSONObject());
                    publish();
                    break;
                case "response.audio_transcript.delta":
                case "response.output_audio_transcript.delta":
                    if (!caption.startsWith("z0live: ")) caption = "z0live: ";
                    caption = bounded(caption + message.optString("delta"), 8000);
                    publish();
                    break;
                case "response.audio_transcript.done":
                case "response.output_text.done":
                case "response.output_audio_transcript.done":
                    String output = bounded(message.optString("transcript", message.optString("text")), 8000);
                    caption = "z0live: " + output;
                    event(type, output, new JSONObject());
                    publish();
                    break;
                case "response.created":
                    turn.responseStarted();
                    break;
                case "input_audio_buffer.speech_started":
                    turn.userSpeaking(true);
                    break;
                case "input_audio_buffer.speech_stopped":
                    turn.userSpeaking(false);
                    drainTurn();
                    break;
                case "output_audio_buffer.started":
                    speaking = true;
                    turn.playback(true);
                    status = getString(R.string.z0live_service_speaking);
                    event(type, "", new JSONObject());
                    publish();
                    break;
                case "output_audio_buffer.stopped":
                case "output_audio_buffer.cleared":
                    speaking = false;
                    turn.playback(false);
                    drainTurn();
                    status = getString(inboxItem == null ? R.string.z0live_service_listening : R.string.z0live_service_speaking);
                    event(type, "", new JSONObject());
                    publish();
                    break;
                case "response.function_call_arguments.done":
                    tool(message);
                    break;
                case "response.output_item.done":
                    JSONObject item = message.optJSONObject("item");
                    if (item != null && "function_call".equals(item.optString("type"))) tool(item);
                    break;
                case "conversation.item.input_audio_transcription.failed":
                    error = getString(R.string.z0live_service_transcription_failed);
                    event("transcription_error", "", new JSONObject());
                    publish();
                    break;
                case "error":
                    event("realtime_error", "", new JSONObject());
                    fail(getString(R.string.z0live_service_realtime_failed));
                    break;
                case "response.done":
                    turn.responseDone();
                    drainTurn();
                    JSONObject response = message.optJSONObject("response");
                    if (response != null && ("failed".equals(response.optString("status"))
                            || "incomplete".equals(response.optString("status")))) {
                        error = getString(R.string.z0live_service_response_status) + response.optString("status");
                        event("response_error", error, new JSONObject());
                        publish();
                    }
                    break;
                default:
                    break;
            }
        } catch (JSONException e) { fail(getString(R.string.z0live_service_malformed_event)); }
    }

    private void tool(JSONObject call) {
        String callId = call.optString("call_id"), name = call.optString("name"), arguments = call.optString("arguments", "{}");
        if (callId.isEmpty() || callId.length() > 160) { fail(getString(R.string.z0live_service_invalid_call)); return; }
        if (toolCalls.contains(callId)) return;
        if (toolCalls.size() >= 256) {
            toolOutput(callId, object("error", "Session tool limit reached"));
            return;
        }
        toolCalls.add(callId);
        toolNames.put(callId, name);
        if (toolCalls.size() > 256 || pendingTools >= 4 || arguments.length() > 16384) {
            toolOutput(callId, object("error", "Tool is not allowed or session limit reached"));
            return;
        }
        JSONObject parsed;
        try { parsed = new JSONObject(arguments); } catch (JSONException e) {
            toolOutput(callId, object("ok", false, "error", "Tool arguments must be a JSON object")); return;
        }
        event("tool_call", "", object("call_id", callId, "name", bounded(name, 80), "arguments", parsed));
        Log.i("z0live", "tool_call name=" + name + " call_id=" + callId);
        if (Z0LivePhoneTools.supports(this, name)) {
            JSONObject result = Z0LivePhoneTools.execute(this, name, parsed);
            toolOutput(callId, result);
            if ("z0live_end".equals(name) && result.optBoolean("ok")) main.postDelayed(() -> finishSession("", true), 1000);
            return;
        }
        pendingTools++;
        int id = generation;
        network.execute(() -> {
            try {
                JSONObject result = backend.tool(sessionId, callId, name, arguments);
                main.post(() -> { if (valid(id)) { pendingTools--; toolOutput(callId, result); } });
            } catch (IOException e) {
                main.post(() -> {
                    if (!valid(id)) return;
                    pendingTools--;
                    error = getString(R.string.z0live_service_tool_failed);
                    publish();
                    toolOutput(callId, object("error", "Backend tool unavailable"));
                });
            }
        });
    }
    private void toolOutput(String callId, JSONObject result) {
        if (!running || peer == null) return;
        if (result.toString().length() > 32768) result = object("error", "Backend tool result exceeded the client limit");
        String name = toolNames.getOrDefault(callId, "unknown");
        boolean success = result.optBoolean("ok", !result.has("error"));
        event("tool_result", "", object("call_id", callId, "name", name, "result", result, "success", success));
        Log.i("z0live", "tool_result name=" + name + " call_id=" + callId + " success=" + success + " status=" + result.optString("status"));
        peer.send(object("type", "conversation.item.create", "item", object("type", "function_call_output", "call_id", callId, "output", result.toString())).toString());
        turn.toolCompleted();
        drainTurn();
    }

    private void drainTurn() {
        if (!running || peer == null || inboxItem != null) return;
        if (turn.takeContinuation(pendingTools > 0)) peer.send(object("type", "response.create").toString());
        if (pendingInbox != null && turn.canSpeakInbox()) {
            JSONObject next = pendingInbox;
            pendingInbox = null;
            handleInbox(next);
        }
    }

    private JSONObject pendingInbox;

    private void createTts(int id) {
        tts = new TextToSpeech(this, result -> main.post(() -> {
            if (!valid(id)) return;
            ttsReady = result == TextToSpeech.SUCCESS;
            if (ttsReady) {
                int language = tts.setLanguage(Locale.getDefault());
                ttsReady = language != TextToSpeech.LANG_MISSING_DATA && language != TextToSpeech.LANG_NOT_SUPPORTED;
            }
            if (!ttsReady) { error = getString(R.string.z0live_service_tts_unavailable); publish(); }
        }));
        tts.setAudioAttributes(new AudioAttributes.Builder().setUsage(AudioAttributes.USAGE_VOICE_COMMUNICATION)
                .setContentType(AudioAttributes.CONTENT_TYPE_SPEECH).build());
        tts.setOnUtteranceProgressListener(new UtteranceProgressListener() {
            public void onStart(String utteranceId) {}
            public void onDone(String utteranceId) { complete(utteranceId, true); }
            public void onError(String utteranceId) { complete(utteranceId, false); }
            public void onStop(String utteranceId, boolean interrupted) { complete(utteranceId, false); }
            private void complete(String utteranceId, boolean success) {
                main.post(() -> { if (valid(id) && utteranceId.equals(speakingInboxId)) finishInbox(success); });
            }
        });
    }
    private void pollInbox() {
        if (inboxBusy || inboxItem != null || pendingInbox != null || !running) return;
        inboxBusy = true;
        int id = generation;
        network.execute(() -> {
            try {
                Z0LiveProtocol.InboxPage page = backend.inboxPage(inboxCursor);
                JSONArray items = page.messages;
                main.post(() -> {
                    if (!valid(id)) return;
                    inboxBusy = false;
                    JSONObject next = null;
                    long nextId = Long.MAX_VALUE;
                    for (int i = 0; i < items.length(); i++) {
                        JSONObject item = items.optJSONObject(i);
                        if (item == null || !(item.opt("id") instanceof Number)) {
                            error = getString(R.string.z0live_service_invalid_inbox);
                            publish();
                            return;
                        }
                        long itemId = item.optLong("id", -1);
                        if (itemId > inboxCursor && itemId < nextId) { next = item; nextId = itemId; }
                    }
                    if (next != null) {
                        pendingInbox = next;
                        drainTurn();
                    } else if (page.after > inboxCursor) {
                        inboxCursor = page.after;
                        persistCursor();
                    }
                });
            } catch (IOException e) {
                main.post(() -> {
                    if (!valid(id)) return;
                    inboxBusy = false;
                    error = getString(R.string.z0live_service_inbox_retry);
                    publish();
                });
            }
        });
    }
    private void handleInbox(JSONObject item) {
        inboxItem = item;
        String action = item.isNull("action") ? "" : item.optString("action", "");
        if (!action.isEmpty() && !"none".equals(action) && !"say".equals(action)) {
            // Hermes cannot use a stale/broadcast inbox item as fresh phone authority.
            boolean sameSession = sessionId.equals(item.optString("session_id", ""));
            JSONObject params = item.optJSONObject("params");
            JSONObject result = sameSession && params != null && Z0LivePhoneTools.supports(this, action)
                    ? Z0LivePhoneTools.execute(this, action, params)
                    : object("ok", false, "error", "Unknown tool, invalid params or stale-session action");
            event("tool_result", "", object("id", item.optLong("id"), "name", bounded(action, 80), "success", result.optBoolean("ok"), "result", result));
            if (!result.optBoolean("ok")) { error = getString(R.string.z0live_service_inbox_action_failed) + bounded(action, 80); publish(); }
        }
        String say = item.optString("say", "");
        if (say.isEmpty()) { finishInbox(true); return; }
        if (!ttsReady || say.length() > Math.min(4000, TextToSpeech.getMaxSpeechInputLength())) {
            finishInbox(false); return;
        }
        speakingInboxId = UUID.randomUUID().toString();
        audio.setPlayingInbox(true);
        applyAudio();
        caption = "Inbox: " + say;
        status = getString(R.string.z0live_service_speaking);
        publish();
        main.postDelayed(inboxTimeout, 60000);
        if (tts.speak(say, TextToSpeech.QUEUE_FLUSH, null, speakingInboxId) == TextToSpeech.ERROR) finishInbox(false);
    }
    private void finishInbox(boolean success) {
        if (inboxItem == null) return;
        main.removeCallbacks(inboxTimeout);
        JSONObject handled = inboxItem;
        inboxItem = null;
        speakingInboxId = null;
        if (!success && tts != null) tts.stop();
        audio.setPlayingInbox(false);
        applyAudio();
        Log.i("z0live", "inbox_speech id=" + handled.optLong("id") + " success=" + success);
        event(success ? "inbox_handled" : "inbox_failed", "", object("id", handled.optLong("id"), "action", handled.optString("action", "")));
        // Failure/rejection is handled explicitly and reported before advancing; never skip an unhandled item.
        inboxCursor = handled.optLong("id", inboxCursor);
        persistCursor();
        drainTurn();
        main.post(this::pollInbox);
        status = getString(R.string.z0live_service_listening);
        if (!success) error = getString(R.string.z0live_service_tts_failed);
        publish();
    }

    private void persistCursor() {
        try { if (store != null) store.setInboxCursor(inboxCursor); }
        catch (RuntimeException ignored) { error = getString(R.string.z0live_service_cursor_failed); }
    }

    private void event(String type, String text, JSONObject data) {
        telemetry.offer(Z0LiveEvents.create(sessionId, type, text, data));
    }
    private void flush(boolean finalFlush) {
        if (backend == null || telemetryBusy) return;
        telemetryBusy = true;
        network.execute(() -> {
            int budget = finalFlush ? 6 : 1;
            do {
                JSONArray batch = telemetry.batch(24);
                if (batch.length() == 0) break;
                try {
                    backend.events(batch);
                    telemetry.delivered(batch);
                } catch (IOException e) {
                    telemetryFailed.incrementAndGet();
                    telemetry.failed(batch);
                    if (!finalFlush) break;
                }
            } while (--budget > 0);
            if (finalFlush && !brokerSessionToEnd.isEmpty()) {
                try { backend.endSession(brokerSessionToEnd); }
                catch (IOException ignored) { telemetryFailed.incrementAndGet(); }
            }
            main.post(() -> {
                telemetryBusy = false;
                if (finalFlush) {
                    telemetryFailed.addAndGet(telemetry.size());
                    network.shutdown();
                } else if (ending) flush(true);
                publish();
            });
        });
    }
    private void fail(String message) { finishSession(message, false); }
    private void finishSession(String reason, boolean userEnd) {
        if (ending) return;
        ending = true;
        boolean wasRunning = running;
        running = false;
        pendingNetwork = false;
        generation++;
        main.removeCallbacks(healthRetry);
        main.removeCallbacks(connectionTimeout);
        main.removeCallbacks(inboxTimeout);
        main.removeCallbacks(inboxTick);
        main.removeCallbacks(telemetryTick);
        http.dispatcher().cancelAll();
        audio.setConnected(false);
        Z0LivePeer oldPeer = peer;
        peer = null;
        closingResources = oldPeer != null;
        if (tts != null) { tts.stop(); tts.shutdown(); tts = null; }
        if (audioManager != null && focusRequest != null) {
            audioManager.abandonAudioFocusRequest(focusRequest);
            audioManager.setMode(AudioManager.MODE_NORMAL);
        }
        if (wasRunning) event("session_end", "", object("reason", userEnd ? "user" : "error", "telemetry_failed", telemetryFailed.get()));
        status = getString(closingResources ? R.string.z0live_service_ending : (reason.isEmpty() ? R.string.z0live_service_ended : R.string.z0live_service_error));
        error = reason;
        publish();
        if (oldPeer != null) oldPeer.close(() -> {
            closingResources = false;
            status = getString(reason.isEmpty() ? R.string.z0live_service_ended : R.string.z0live_service_error);
            publish();
            stopForeground(STOP_FOREGROUND_REMOVE);
            stopSelf();
        });
        if (telemetryBusy) { /* The in-flight flush schedules the final bounded flush. */ }
        else if (backend != null) flush(true);
        else network.shutdown();
        if (!closingResources) {
            stopForeground(STOP_FOREGROUND_REMOVE);
            stopSelf();
        }
    }
    @Override public void onDestroy() {
        if (!ending) finishSession("Voice service stopped by Android", false);
        if (current == this) current = null;
        // Closing pooled TLS sockets can perform network writes; never do it on Android's main thread.
        new Thread(() -> http.connectionPool().evictAll(), "z0live-http-close").start();
        super.onDestroy();
    }
    private static String bounded(String value, int max) { return value.length() > max ? value.substring(0, max) : value; }
    static JSONObject object(Object... fields) {
        JSONObject result = new JSONObject();
        try { for (int i = 0; i < fields.length; i += 2) result.put((String) fields[i], fields[i + 1]); }
        catch (JSONException e) { throw new IllegalArgumentException("Invalid JSON fields", e); }
        return result;
    }
}
