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
import java.io.IOException;
import java.util.HashSet;
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
    private final ExecutorService control = Executors.newSingleThreadExecutor();
    private final OkHttpClient http = new OkHttpClient.Builder()
            .connectTimeout(8, java.util.concurrent.TimeUnit.SECONDS)
            .readTimeout(15, java.util.concurrent.TimeUnit.SECONDS)
            .callTimeout(20, java.util.concurrent.TimeUnit.SECONDS)
            .followRedirects(false).followSslRedirects(false).build();
    private final Z0LiveTelemetry telemetry = new Z0LiveTelemetry();
    private final Z0LiveBrokerSession broker = new Z0LiveBrokerSession(telemetry::offer, android.os.SystemClock::elapsedRealtime);
    private final Z0LiveTurnState turn = new Z0LiveTurnState();
    private final Z0LiveBargeIn bargeIn = new Z0LiveBargeIn();
    private final Z0LiveSpeechGate speechGate = new Z0LiveSpeechGate();
    private long sessionInboxFloor;
    private final AtomicInteger telemetryFailed = new AtomicInteger();
    private final Z0LiveAudioState audio = new Z0LiveAudioState();
    private final Z0LiveInboxPlayback inboxPlayback = new Z0LiveInboxPlayback();
    private final Set<String> toolCalls = new HashSet<>();
    private final java.util.Map<String, String> toolNames = new java.util.HashMap<>();
    private Z0LiveProtocol backend;
    private Z0LivePeer peer;
    private AudioManager audioManager;
    private AudioFocusRequest focusRequest;
    private boolean running, pendingNetwork, connecting, telemetryBusy, inboxBusy;
    private boolean speaking, ending, closingResources;
    /** True while connected through the broker-signaled Frameless Bidi call (OMP /live architecture). */
    private boolean bidi;
    private final StringBuilder bidiCaption = new StringBuilder();
    private final Z0LiveBidiBarge bidiBarge = new Z0LiveBidiBarge();
    private int generation, reachabilityAttempts, pendingTools;
    private Z0LiveStore store;
    private PairingValidation.Pairing activePairing;
    private long inboxCursor;
    private String sessionId = "", status = "", caption = "", error = "";
    private volatile String brokerSessionToEnd = "";
    private JSONObject inboxItem;
    private String inboxResponseId = "";
    private final Runnable healthRetry = () -> checkBackend();
    private final Runnable connectionTimeout = () -> fail(getString(R.string.z0live_service_connection_timeout));
    private final Runnable inboxTimeout = () -> { interruptRealtime(); finishInbox(false); };
    private final Runnable telemetryTick = new Runnable() {
        public void run() {
            if (!running) return;
            broker.heartbeat();
            flush(false);
            main.postDelayed(this, 2500);
        }
    };
    private final Runnable inboxTick = new Runnable() {
        public void run() {
            if (!running) return;
            pollInbox();
            // Bidi phone actions arrive through the inbox: poll quickly so they feel immediate.
            main.postDelayed(this, bidi ? 600 : 3000);
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
    public static boolean hasActiveSession() { return Z0LiveStatus.get().active; }
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
        Z0LiveInboxNotifications.schedule(this);
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
            activePairing = pairing;
            inboxCursor = pairing == null ? -1 : store.inboxCursor(pairing);
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
        turn.inputMuted(!audio.microphoneEnabled());
        if (audio.isMuted()) {
            // A disabled mic track sends no silence: don't leave a partial VAD utterance blocking tools/inbox forever.
            turn.userSpeaking(false);
            broker.inputMuted();
            flush(false);
            if (audio.isConnected()) sendRealtime(object("type", "input_audio_buffer.clear").toString());
        }
        applyAudio();
        drainTurn();
        event("mute", "", object("muted", audio.isMuted()));
        publish();
    }
    private void applyAudio() {
        if (peer != null) peer.audio(audio.microphoneEnabled(), audio.realtimeSpeakerEnabled() && !(bidi && bidiBarge.ducked()));
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
                // Every explicit connection establishes a live-only floor. Never read old replies aloud.
                long head = backend.inboxHead();
                store.setInboxCursor(activePairing, Math.max(Math.max(0, inboxCursor), head));
                JSONObject context = object("app", getPackageName(), "screen", "z0live voice");
                JSONArray phoneTools = Z0LivePhoneTools.schemas(this);
                Z0LiveProtocol.Session session = backend.session(context, phoneTools, true);
                brokerSessionToEnd = session.sessionId;
                main.post(() -> {
                    if (!valid(id)) return;
                    if (session.sessionId == null || session.sessionId.isEmpty()) { fail(getString(R.string.z0live_service_missing_session)); return; }
                    inboxCursor = Math.max(Math.max(0, inboxCursor), head);
                    sessionInboxFloor = inboxCursor;
                    sessionId = session.sessionId;
                    bidi = "bidi".equals(session.transport);
                    bidiBarge.reset();
                    broker.start(sessionId);
                    flush(false);
                    event("session_ready", "", object("model", session.model, "voice", session.voice, "instructions_version", session.instructionsVersion, "builtin_tools", new JSONArray(session.builtinTools)));
                    try {
                        if (!requestAudioFocus()) { fail(getString(R.string.z0live_service_audio_focus_unavailable)); return; }
                        peer = new Z0LivePeer(this, main, new Z0LivePeer.Listener() {
                            public void onOffer(String sdp) {
                                if (!valid(id)) return;
                                network.execute(() -> {
                                    try {
                                        if ("bidi".equals(session.transport)) {
                                            String answer;
                                            Z0LiveProtocol.Session fallback = null;
                                            try {
                                                answer = backend.liveCall(session.sessionId, sdp);
                                            } catch (IOException bidiFailed) {
                                                // Keep the phone working: the same offer is valid for gpt-realtime.
                                                fallback = backend.session(context, phoneTools, false);
                                                try { backend.endSession(session.sessionId); } catch (IOException ignored) { telemetryFailed.incrementAndGet(); }
                                                answer = backend.exchangeSdp(fallback.secret, sdp);
                                            }
                                            String finalAnswer = answer;
                                            Z0LiveProtocol.Session adopted = fallback;
                                            main.post(() -> {
                                                if (!valid(id) || peer == null) return;
                                                if (adopted != null) adoptFallback(adopted);
                                                peer.answer(finalAnswer);
                                            });
                                            return;
                                        }
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
                                // Frameless Bidi does its own full-duplex turn-taking; only gpt-realtime takes VAD config.
                                sendRealtime(Z0LiveBargeIn.vadConfiguration());
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
            } catch (IOException | RuntimeException e) {
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
                    turn.inputMuted(!audio.microphoneEnabled());
                    if (!gained) {
                        turn.userSpeaking(false);
                        broker.inputMuted();
                        flush(false);
                        if (audio.isConnected()) sendRealtime(object("type", "input_audio_buffer.clear").toString());
                    }
                    if (!gained && inboxItem != null) {
                        interruptRealtime();
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
        if (!debugReady() || service.bidi || Looper.myLooper() != Looper.getMainLooper() || text == null || text.isBlank() || text.length() > 2000) return false;
        service.turn.responseStarted();
        service.caption = "You: " + text;
        if (service.broker.transcript(text)) service.interruptRealtime();
        service.discardInterruptedInbox();
        service.event("user_transcript", text, object("source", "debug_text"));
        service.flush(false);
        service.peer.send(object("type", "conversation.item.create", "item", object("type", "message", "role", "user", "content", new JSONArray().put(object("type", "input_text", "text", text)))).toString());
        service.peer.send(object("type", "response.create").toString());
        Log.i("z0live", "debug_turn injected");
        service.publish();
        return true;
    }

    /** gpt-realtime control messages; Frameless Bidi accepts none from the phone. */
    private void sendRealtime(String json) {
        if (peer != null && !bidi) peer.send(json);
    }

    /** Switch to the gpt-realtime session minted after a failed Frameless Bidi signaling attempt. */
    private void adoptFallback(Z0LiveProtocol.Session session) {
        bidi = false;
        sessionId = session.sessionId;
        brokerSessionToEnd = session.sessionId;
        broker.start(sessionId);
        event("session_ready", "", object("model", session.model, "voice", session.voice, "instructions_version", session.instructionsVersion, "builtin_tools", new JSONArray(session.builtinTools), "fallback", "bidi_signaling_failed"));
        flush(false);
    }

    /** Frameless Bidi events seen on the phone's own data channel: captions and failures only. */
    private void bidiEvent(JSONObject message) {
        switch (message.optString("type")) {
            case "input_transcript.added":
                if (!caption.startsWith("You: ")) { bidiCaption.setLength(0); caption = "You: "; }
                JSONObject heard = message.optJSONObject("item");
                // Real words over an open assistant turn silence playback now; noise never does.
                if (heard != null && bidiBarge.userTranscript(heard.optString("text"))) applyAudio();
                appendCaption(heard);
                break;
            case "turn.created":
                JSONObject created = message.optJSONObject("turn");
                if (created != null && "assistant".equals(created.optString("role"))
                        && bidiBarge.assistantTurnCreated(created.optString("id"))) applyAudio();
                break;
            case "output_transcript.added":
                if (!caption.startsWith("z0live: ")) { bidiCaption.setLength(0); caption = "z0live: "; }
                appendCaption(message.optJSONObject("item"));
                status = getString(R.string.z0live_service_speaking);
                publish();
                break;
            case "turn.done":
                JSONObject done = message.optJSONObject("turn");
                if (done == null) break;
                if ("assistant".equals(done.optString("role"))) { if (bidiBarge.assistantTurnDone(done.optString("id"))) applyAudio(); }
                else bidiBarge.userTurnDone();
                String text = bounded(done.optString("transcript").trim(), 8000);
                if (!text.isEmpty()) caption = ("user".equals(done.optString("role")) ? "You: " : "z0live: ") + text;
                bidiCaption.setLength(0);
                status = getString(R.string.z0live_service_listening);
                publish();
                break;
            case "error":
                // The broker's sideband sees the same error; the phone records and ends the call honestly.
                event("realtime_error", "", new JSONObject());
                fail(getString(R.string.z0live_service_realtime_failed));
                break;
            default:
                break;
        }
    }

    private void appendCaption(JSONObject item) {
        if (item == null) return;
        bidiCaption.append(item.optString("text"));
        String prefix = caption.startsWith("You: ") ? "You: " : "z0live: ";
        caption = prefix + bounded(bidiCaption.toString().trim(), 8000);
        publish();
    }

    private void interruptRealtime() {
        if (peer != null && !bidi) {
            JSONArray commands = bargeIn.interrupt();
            for (int i = 0; i < commands.length(); i++) peer.send(commands.optJSONObject(i).toString());
        }
        turn.playback(false);
        speaking = false;
        if (inboxItem != null) finishInbox(false);
    }

    private void realtimeEvent(String raw) {
        try {
            JSONObject message = new JSONObject(raw);
            String type = message.optString("type");
            if (bidi) { bidiEvent(message); return; }
            broker.realtime(message, audio.microphoneEnabled());
            discardInterruptedInbox();
            // Floor/cancel/usage delivery must not wait for the periodic telemetry tick.
            flush(false);
            switch (type) {
                case "conversation.item.input_audio_transcription.delta":
                    // Interrupt only once the transcript proves the owner is talking, never on bare noise.
                    if (speechGate.transcriptDelta(message.optString("delta"))) interruptRealtime();
                    break;
                case "conversation.item.input_audio_transcription.completed":
                    String input = bounded(message.optString("transcript"), 8000);
                    boolean spoken = speechGate.transcriptCompleted(input);
                    if (!spoken) {
                        // Footsteps, doors, room noise: drop the turn from the conversation and stay quiet.
                        String noiseItem = message.optString("item_id", "");
                        if (!noiseItem.isEmpty()) peer.send(object("type", "conversation.item.delete", "item_id", noiseItem).toString());
                        event("noise_ignored", "", object("chars", input.length()));
                        drainTurn();
                        break;
                    }
                    caption = "You: " + input;
                    if (broker.transcript(input) || !speechGate.interruptedThisTurn() && (speaking || turn.responding())) interruptRealtime();
                    discardInterruptedInbox();
                    event(type, input, new JSONObject());
                    turn.userTurnNeedsReply();
                    drainTurn();
                    flush(false);
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
                    JSONObject createdResponse = message.optJSONObject("response");
                    bargeIn.responseStarted(createdResponse == null ? "" : createdResponse.optString("id"));
                    if (inboxItem != null && inboxResponseId.isEmpty() && createdResponse != null) inboxResponseId = createdResponse.optString("id");
                    turn.responseStarted();
                    break;
                case "input_audio_buffer.speech_started":
                    if (!audio.microphoneEnabled()) break;
                    // Voice activity alone is not an interruption: wait for words (speechGate).
                    turn.userSpeaking(true);
                    speechGate.speechStarted();
                    break;
                case "input_audio_buffer.cleared":
                    turn.userSpeaking(false);
                    drainTurn();
                    break;
                case "input_audio_buffer.speech_stopped":
                    turn.userSpeaking(false);
                    drainTurn();
                    break;
                case "output_audio_buffer.started":
                    speaking = true;
                    bargeIn.playback(true);
                    turn.playback(true);
                    status = getString(R.string.z0live_service_speaking);
                    event(type, "", new JSONObject());
                    publish();
                    break;
                case "output_audio_buffer.stopped":
                case "output_audio_buffer.cleared":
                    speaking = false;
                    bargeIn.playback(false);
                    turn.playback(false);
                    if (inboxItem != null && inboxResponseId.equals(message.optString("response_id"))) finishInbox("output_audio_buffer.stopped".equals(type));
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
                    JSONObject providerError = message.optJSONObject("error");
                    if (providerError != null && "response_cancel_not_active".equals(providerError.optString("code"))) break;
                    event("realtime_error", "", new JSONObject());
                    fail(getString(R.string.z0live_service_realtime_failed));
                    break;
                case "response.done":
                    bargeIn.responseDone();
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
        if (backend.isBuiltin(sessionId, name)) broker.toolCalled(callId, call);
        if ("cancel_work".equals(name) && backend.isBuiltin(sessionId, name)) {
            broker.cancelWork();
            discardInterruptedInbox();
            interruptRealtime();
            flush(false);
        }
        if (!backend.isBuiltin(sessionId, name) && Z0LivePhoneTools.supports(this, name)) {
            JSONObject result = Z0LivePhoneTools.execute(this, name, parsed);
            toolOutput(callId, result);
            if ("z0live_end".equals(name) && result.optBoolean("ok")) main.postDelayed(() -> finishSession("", true), 1000);
            return;
        }
        pendingTools++;
        int id = generation;
        String sid = sessionId;
        network.execute(() -> {
            try {
                JSONObject result = backend.tool(sid, callId, name, arguments);
                main.post(() -> { if (valid(id)) {
                    String lateTurn = broker.toolResult(callId, result);
                    if (!lateTurn.isEmpty()) control.execute(() -> {
                        try { backend.cancelTurn(sid, lateTurn); }
                        catch (IOException ignored) { telemetryFailed.incrementAndGet(); }
                    });
                    pendingTools--;
                    toolOutput(callId, lateTurn.isEmpty() ? result : object("ok", false, "status", "cancelling", "turn_id", lateTurn, "say", "Cancellation was requested for the interrupted work."));
                    flush(false);
                } });
            } catch (IOException e) {
                main.post(() -> {
                    if (!valid(id)) return;
                    pendingTools--;
                    broker.toolResult(callId, new JSONObject());
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
        // An async acceptance is already acknowledged before dispatch. Do not force a second
        // model turn just to announce that the answer has not arrived yet.
        turn.toolCompleted(!"silent".equals(result.optString("speech")));
        drainTurn();
    }

    private void drainTurn() {
        if (!running || peer == null || inboxItem != null) return;
        if (turn.takeUserReply() || turn.takeContinuation(pendingTools > 0)) sendRealtime(object("type", "response.create").toString());
        if (pendingInbox != null && turn.canSpeakInbox()) {
            JSONObject next = pendingInbox;
            pendingInbox = null;
            handleInbox(next);
        }
    }

    private JSONObject pendingInbox;

    private void discardInterruptedInbox() {
        if (broker.discard(pendingInbox)) {
            JSONObject dropped = pendingInbox;
            pendingInbox = null;
            inboxCursor = Math.max(inboxCursor, dropped.optLong("id"));
            persistCursor();
            event("inbox_failed", "", object("id", dropped.optLong("id"), "delivery", "cancelled", "spoken", false));
        }
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
                        broker.inbox(next);
                        pendingInbox = next;
                        discardInterruptedInbox();
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
        inboxPlayback.begin(item.optLong("id"));
        inboxItem = item;
        if (broker.discard(item)) { finishInbox(false, true, "cancelled"); return; }
        if (item.optLong("id") <= sessionInboxFloor || !Z0LiveInboxDelivery.maySpeak(hasActiveSession(), sessionId, item)) {
            Z0LiveInboxNotifications.show(this, item);
            finishInbox(true, true, "notification");
            return;
        }
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
        // Claim the identity before sending speech: interruption/process loss must never replay it.
        inboxCursor = Math.max(inboxCursor, item.optLong("id"));
        persistCursor();
        String say = item.optString("say", "");
        if (say.isEmpty()) { finishInbox(true, true, "none"); return; }
        // Frameless Bidi speaks results through the broker's delegation context, never from the inbox.
        if (bidi || item.optLong("id") <= sessionInboxFloor || !Z0LiveInboxDelivery.maySpeak(hasActiveSession(), sessionId, item)
                || !audio.realtimeSpeakerEnabled()) {
            Z0LiveInboxNotifications.show(this, item);
            finishInbox(true, true, "notification");
            return;
        }
        try {
            broker.speakingInbox(item);
            JSONArray commands = Z0LiveInboxDelivery.realtimeReply(say, item.optString("source", ""));
            turn.responseStarted();
            for (int i = 0; i < commands.length(); i++) peer.send(commands.optJSONObject(i).toString());
            caption = "Hermes: " + say;
            status = getString(R.string.z0live_service_speaking);
            publish();
            main.postDelayed(inboxTimeout, 60000);
        } catch (RuntimeException invalid) {
            Z0LiveInboxNotifications.show(this, item);
            finishInbox(false);
        }
    }
    private void finishInbox(boolean success) { finishInbox(success, true, "realtime"); }
    private void finishInbox(boolean success, boolean continueSession) { finishInbox(success, continueSession, "realtime"); }
    private void finishInbox(boolean success, boolean continueSession, String delivery) {
        if (inboxItem == null) return;
        Z0LiveInboxPlayback.Receipt receipt = inboxPlayback.finish(success);
        if (receipt == null) return;
        main.removeCallbacks(inboxTimeout);
        JSONObject handled = inboxItem;
        inboxItem = null;
        inboxResponseId = "";
        if (!success && !broker.discard(handled)) Z0LiveInboxNotifications.show(this, handled);
        broker.deliveredInbox(handled, success);
        Log.i("z0live", "inbox_delivery id=" + handled.optLong("id") + " success=" + success + " via=" + delivery);
        event(success ? ("realtime".equals(delivery) ? "inbox_handled" : "inbox_notified") : "inbox_failed", "",
                object("id", handled.optLong("id"), "action", handled.optString("action", ""), "delivery", delivery, "spoken", success && "realtime".equals(delivery)));
        // Failure/rejection is handled explicitly and reported before advancing; never skip an unhandled item.
        inboxCursor = Z0LiveInboxPlayback.cursorAfter(inboxCursor, receipt);
        persistCursor();
        if (!continueSession) return;
        drainTurn();
        main.post(this::pollInbox);
        status = getString(R.string.z0live_service_listening);
        if (!success) error = getString(R.string.z0live_service_inbox_speech_failed);
        publish();
    }

    private void persistCursor() {
        try { if (store != null && activePairing != null) store.setInboxCursor(activePairing, inboxCursor); }
        catch (RuntimeException ignored) { error = getString(R.string.z0live_service_cursor_failed); }
    }

    private void event(String type, String text, JSONObject data) {
        telemetry.offer(Z0LiveEvents.create(sessionId, type, text, data));
    }
    private void flush(boolean finalFlush) {
        if (backend == null || telemetryBusy) return;
        telemetryBusy = true;
        Runnable send = () -> {
            boolean failed = false;
            for (int batchIndex = 0; batchIndex < 6; batchIndex++) {
                JSONArray batch = telemetry.batch(24);
                if (batch.length() == 0) {
                    break;
                }
                try {
                    backend.events(batch);
                    telemetry.delivered(batch);
                } catch (IOException e) {
                    telemetryFailed.incrementAndGet();
                    telemetry.failed(batch);
                    failed = true;
                    if (!finalFlush) break;
                }
            }
            if (finalFlush && !brokerSessionToEnd.isEmpty()) {
                try { backend.endSession(brokerSessionToEnd); }
                catch (IOException ignored) { telemetryFailed.incrementAndGet(); }
            }
            boolean retryOnTick = failed;
            main.post(() -> {
                telemetryBusy = false;
                if (finalFlush) {
                    telemetryFailed.addAndGet(telemetry.size());
                    network.shutdown();
                    control.shutdown();
                } else if (ending) flush(true);
                else if (!retryOnTick && telemetry.size() > 0) flush(false);
                publish();
            });
        };
        // Mint can finish after hangup: the final send must wait for its server session ID.
        if (finalFlush) network.execute(() -> control.execute(send));
        else control.execute(send);
    }
    private void fail(String message) { finishSession(message, false); }
    private void finishSession(String reason, boolean userEnd) {
        if (ending) return;
        ending = true;
        // Persist an honest failed receipt before invalidating the session. The claimed inbox
        // identity is already durable, so an interrupted Realtime reply cannot replay on restart.
        finishInbox(false, false);
        broker.end();
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
