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
    private String sessionId = "", status = "Connecting", caption = "", error = "";
    private String speakingInboxId;
    private volatile String brokerSessionToEnd = "";
    private JSONObject inboxItem;
    private final Runnable healthRetry = () -> checkBackend();
    private final Runnable connectionTimeout = () -> fail("Voice connection timed out. End and start again.");
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
    /** Bound to one manually started voice generation; disarm on End/restart. */
    static int gestureSessionGeneration() {
        Z0LiveService s = current;
        return s != null && s.running && !s.ending && !s.closingResources ? s.generation : -1;
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
        NotificationChannel channel = new NotificationChannel(CHANNEL, "z0live microphone", NotificationManager.IMPORTANCE_LOW);
        channel.setDescription("Ongoing native voice session with mute and end controls");
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
            error = "Android could not start the microphone foreground service. Open z0live and try again.";
            status = "Error";
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
            if (pairing == null) { fail("Pair this device before starting voice"); return START_NOT_STICKY; }
            backend = new Z0LiveProtocol(http, pairing.backendUrl, pairing.token);
            event("session_start", "", object("client", "lawnchair"));
            main.post(telemetryTick);
            checkBackend();
        } catch (RuntimeException e) {
            fail("Encrypted pairing could not be read. Open settings and pair again.");
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
                .setSmallIcon(android.R.drawable.ic_btn_speak_now)
                .setContentTitle("z0live · " + status + (audio.isMuted() ? " · muted" : ""))
                .setContentText(error.isEmpty() ? "Microphone session · tap to view captions" : error)
                .setContentIntent(open).setOngoing(true).setOnlyAlertOnce(true)
                .setCategory(Notification.CATEGORY_SERVICE)
                .addAction(new Notification.Action.Builder(null, audio.isMuted() ? "Unmute" : "Mute", mute).build())
                .addAction(new Notification.Action.Builder(null, "End", end).build()).build();
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
        status = "Connecting";
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
                    status = "Error";
                    error = "Backend unreachable or unhealthy. Check Tailscale and pairing. Attempt " + reachabilityAttempts + "/6.";
                    publish();
                    if (reachabilityAttempts < 6) main.postDelayed(healthRetry, Math.min(30000, 3000L << (reachabilityAttempts - 1)));
                    else fail("Backend did not become reachable after 6 attempts. Check Tailscale and pairing, then Start again.");
                });
            }
        });
    }

    private void beginRealtime(int id) {
        if (!valid(id)) return;
        main.postDelayed(connectionTimeout, 45000);
        network.execute(() -> {
            try {
                // No screen capture, hidden app names, remote phone commands, or local tools.
                Z0LiveProtocol.Session session = backend.session(object("app", getPackageName(), "screen", "z0live voice"), new JSONArray());
                brokerSessionToEnd = session.sessionId;
                main.post(() -> {
                    if (!valid(id)) return;
                    if (session.sessionId == null || session.sessionId.isEmpty()) { fail("Backend did not return a broker session identity"); return; }
                    sessionId = session.sessionId;
                    event("session_ready", "", object("model", session.model, "voice", session.voice));
                    try {
                        if (!requestAudioFocus()) { fail("Audio focus is unavailable. End other audio and try again."); return; }
                        createTts(id);
                        peer = new Z0LivePeer(this, main, new Z0LivePeer.Listener() {
                            public void onOffer(String sdp) {
                                if (!valid(id)) return;
                                network.execute(() -> {
                                    try {
                                        String answer = backend.exchangeSdp(session.secret, sdp);
                                        main.post(() -> { if (valid(id) && peer != null) peer.answer(answer); });
                                    } catch (IOException e) {
                                        main.post(() -> { if (valid(id)) fail("Realtime SDP exchange failed. Check backend credentials and connectivity."); });
                                    }
                                });
                            }
                            public void onReady() {
                                if (!valid(id)) return;
                                main.removeCallbacks(connectionTimeout);
                                audio.setConnected(true);
                                status = "Listening";
                                error = "";
                                applyAudio();
                                event("connected", "", object("model", session.model, "voice", session.voice));
                                publish();
                                main.post(inboxTick);
                            }
                            public void onEvent(String json) { if (valid(id)) realtimeEvent(json); }
                            public void onError(String message) { if (valid(id)) fail(message); }
                        });
                    } catch (RuntimeException e) { fail("Native audio could not start"); }
                });
            } catch (IOException e) {
                main.post(() -> { if (valid(id)) fail("Backend session request failed. Check pairing, server version and credentials."); });
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
                    if (!gained) { error = "Voice paused because another app has audio focus"; status = "Error"; }
                    else { error = ""; status = speaking ? "Speaking" : "Listening"; }
                    publish();
                }, main).build();
        boolean granted = audioManager.requestAudioFocus(focusRequest) == AudioManager.AUDIOFOCUS_REQUEST_GRANTED;
        audio.setFocus(granted);
        if (granted) audioManager.setMode(AudioManager.MODE_IN_COMMUNICATION);
        return granted;
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
                    status = "Speaking";
                    event(type, "", new JSONObject());
                    publish();
                    break;
                case "output_audio_buffer.stopped":
                case "output_audio_buffer.cleared":
                    speaking = false;
                    turn.playback(false);
                    drainTurn();
                    status = inboxItem == null ? "Listening" : "Speaking";
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
                    error = "Input transcription failed";
                    event("transcription_error", "", new JSONObject());
                    publish();
                    break;
                case "error":
                    event("realtime_error", "", new JSONObject());
                    fail("Realtime reported an error. End and start again.");
                    break;
                case "response.done":
                    turn.responseDone();
                    drainTurn();
                    JSONObject response = message.optJSONObject("response");
                    if (response != null && ("failed".equals(response.optString("status"))
                            || "incomplete".equals(response.optString("status")))) {
                        error = "Realtime response " + response.optString("status");
                        event("response_error", error, new JSONObject());
                        publish();
                    }
                    break;
                default:
                    break;
            }
        } catch (JSONException e) { fail("Malformed realtime event"); }
    }

    private void tool(JSONObject call) {
        String callId = call.optString("call_id"), name = call.optString("name"), arguments = call.optString("arguments", "{}");
        if (callId.isEmpty() || callId.length() > 160) { fail("Invalid realtime tool call ID"); return; }
        if (toolCalls.contains(callId)) return;
        if (toolCalls.size() >= 256) {
            toolOutput(callId, object("error", "Session tool limit reached"));
            return;
        }
        toolCalls.add(callId);
        event("tool_call", "", object("call_id", callId, "name", bounded(name, 80)));
        if (toolCalls.size() > 256 || pendingTools >= 4 || arguments.length() > 16384
                || !("tell_hermes".equals(name) || "get_context".equals(name) || "summarize".equals(name))) {
            toolOutput(callId, object("error", "Tool is not allowed or session limit reached"));
            return;
        }
        try { new JSONObject(arguments); } catch (JSONException e) {
            toolOutput(callId, object("error", "Tool arguments must be a JSON object")); return;
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
                    error = "Server tool failed. Check the documented builtin tool routes.";
                    publish();
                    toolOutput(callId, object("error", "Backend tool unavailable"));
                });
            }
        });
    }
    private void toolOutput(String callId, JSONObject result) {
        if (!running || peer == null) return;
        if (result.toString().length() > 32768) result = object("error", "Backend tool result exceeded the client limit");
        event("tool_result", "", object("call_id", callId, "result", result));
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
            if (!ttsReady) { error = "Android TextToSpeech is unavailable; inbox speech will be reported as failed"; publish(); }
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
                            error = "Inbox returned an invalid item; cursor has not advanced";
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
                    error = "Inbox polling failed; retrying while the session is open";
                    publish();
                });
            }
        });
    }
    private void handleInbox(JSONObject item) {
        inboxItem = item;
        String action = item.isNull("action") ? "" : item.optString("action", "");
        if (!action.isEmpty() && !"none".equals(action) && !"say".equals(action)) {
            event("tool_result", "", object("id", item.optLong("id"), "name", bounded(action, 80), "success", false, "result", object("error", "No local phone tools are declared by this client")));
            error = "Rejected unknown inbox action: " + bounded(action, 80);
            publish();
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
        status = "Speaking";
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
        event(success ? "inbox_handled" : "inbox_failed", "", object("id", handled.optLong("id"), "action", handled.optString("action", "")));
        // Failure/rejection is handled explicitly and reported before advancing; never skip an unhandled item.
        inboxCursor = handled.optLong("id", inboxCursor);
        persistCursor();
        drainTurn();
        main.post(this::pollInbox);
        status = "Listening";
        if (!success) error = "Android could not speak an inbox item; failure reported";
        publish();
    }

    private void persistCursor() {
        try { if (store != null) store.setInboxCursor(inboxCursor); }
        catch (RuntimeException ignored) { error = "Inbox cursor could not be saved; replies may repeat after restart"; }
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
        status = closingResources ? "Ending" : (reason.isEmpty() ? "Ended" : "Error");
        error = reason;
        publish();
        if (oldPeer != null) oldPeer.close(() -> {
            closingResources = false;
            status = reason.isEmpty() ? "Ended" : "Error";
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
        http.connectionPool().evictAll();
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
