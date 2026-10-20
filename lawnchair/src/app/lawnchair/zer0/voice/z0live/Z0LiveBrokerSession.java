package app.lawnchair.zer0.voice.z0live;

import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import java.util.function.Consumer;
import java.util.function.LongSupplier;
import org.json.JSONObject;

/** Main-thread desktop contract: actual audio floor, cancellation generations and scoped L0 usage. */
public final class Z0LiveBrokerSession {
    public static final long HEARTBEAT_MS = 20000;
    private final Consumer<JSONObject> events;
    private final LongSupplier clock;
    private final Set<String> outstanding = new HashSet<>(), cancelled = new HashSet<>(), usageSent = new HashSet<>(), pendingCalls = new HashSet<>();
    private final Map<String, String> callResponses = new HashMap<>(), responseTurns = new HashMap<>();
    private final Map<String, Integer> callEpochs = new HashMap<>(), responseEpochs = new HashMap<>();
    private final Map<String, JSONObject> usage = new HashMap<>();
    private String sessionId = "", responseId = "", utteranceTurn = "";
    private boolean user, response, playback;
    private long lastHeartbeat;
    private int epoch;

    public Z0LiveBrokerSession(Consumer<JSONObject> events, LongSupplier clock) {
        this.events = events;
        this.clock = clock;
    }

    public void start(String id) {
        sessionId = id;
        outstanding.clear(); cancelled.clear(); pendingCalls.clear(); callResponses.clear(); responseTurns.clear();
        callEpochs.clear(); responseEpochs.clear(); usage.clear(); usageSent.clear();
        responseId = ""; utteranceTurn = "";
        user = response = playback = false;
        epoch = 0;
        lastHeartbeat = clock.getAsLong();
        emit("floor", obj("state", "quiet"));
        emit("active_session_heartbeat", obj("active", true, "floor", floor()));
    }

    public void end() { sessionId = ""; usage.clear(); }
    public String floor() { return user ? "user" : response || playback ? "assistant" : "quiet"; }
    public void heartbeat() {
        if (!sessionId.isEmpty() && clock.getAsLong() - lastHeartbeat >= HEARTBEAT_MS) {
            lastHeartbeat = clock.getAsLong();
            emit("active_session_heartbeat", obj("active", true, "floor", floor()));
        }
    }

    /** Returns true when local queued/playing speech must be discarded before draining the turn. */
    public boolean realtime(JSONObject message, boolean inputEnabled) {
        String type = message.optString("type");
        String before = floor();
        boolean interrupted = false;
        switch (type) {
            case "input_audio_buffer.speech_started":
                if (!inputEnabled) return false;
                interrupted = response || playback || !outstanding.isEmpty() || !pendingCalls.isEmpty();
                user = true;
                utteranceTurn = "";
                if (interrupted) cancel("barge_in", false);
                // Always send this observation, even when already on user floor.
                emit("floor", obj("state", "user", "barge_in", interrupted));
                return interrupted;
            case "input_audio_buffer.speech_stopped":
            case "input_audio_buffer.cleared":
                user = false;
                break;
            case "response.created":
                response = true;
                JSONObject created = message.optJSONObject("response");
                responseId = created == null ? "" : created.optString("id");
                responseEpochs.put(responseId, epoch);
                if (!utteranceTurn.isEmpty()) responseTurns.put(responseId, utteranceTurn);
                break;
            case "response.done":
                JSONObject done = message.optJSONObject("response");
                if (done == null || responseId.equals(done.optString("id"))) response = false;
                if (done != null && done.optJSONObject("usage") != null && !done.optString("id").isEmpty() && !usageSent.contains(done.optString("id"))) {
                    String id = done.optString("id");
                    usage.put(id, obj("response_id", id, "usage", done.optJSONObject("usage")));
                    forwardUsage(id);
                    // Non-work responses have no valid broker turn. Bound unmatched observations.
                    if (usage.size() > 64) usage.remove(usage.keySet().iterator().next());
                }
                break;
            case "output_audio_buffer.started": playback = true; break;
            case "output_audio_buffer.stopped":
            case "output_audio_buffer.cleared": playback = false; break;
            default: return false;
        }
        if (!before.equals(floor())) emit("floor", obj("state", floor()));
        return interrupted;
    }

    public void inputMuted() {
        String before = floor();
        user = false;
        if (!before.equals(floor())) emit("floor", obj("state", floor()));
    }

    public boolean transcript(String text) {
        if (!text.matches("(?i)\\s*(?:never\\s?mind|cancel(?: that)?|stop(?: that)?)[.!? ]*")) return false;
        cancel("cancel", true);
        return true;
    }

    public void cancelWork() { cancel("cancel", false); }
    private void cancel(String type, boolean topicChanged) {
        epoch++;
        cancelled.addAll(outstanding);
        outstanding.clear();
        emit(type, obj("topic_changed", topicChanged));
    }

    public void toolCalled(String callId, JSONObject call) {
        pendingCalls.add(callId);
        String id = call.optString("response_id", responseId);
        callResponses.put(callId, id);
        callEpochs.put(callId, responseEpochs.getOrDefault(id, epoch));
    }

    /** Returns a late-admitted turn requiring exact-turn cancellation, never a new session-wide cancel. */
    public String toolResult(String callId, JSONObject result) {
        pendingCalls.remove(callId);
        String turnId = result.optString("turn_id");
        if (turnId.isEmpty()) return "";
        boolean stale = callEpochs.getOrDefault(callId, epoch) != epoch;
        if (stale) cancelled.add(turnId);
        else {
            utteranceTurn = turnId;
            if (!"completed".equals(result.optString("status"))) outstanding.add(turnId);
        }
        String id = callResponses.getOrDefault(callId, "");
        if (!id.isEmpty()) {
            responseTurns.put(id, turnId);
            forwardUsage(id);
        }
        return stale ? turnId : "";
    }

    public void inbox(JSONObject item) {
        if (!sessionId.equals(item.optString("session_id"))) return;
        JSONObject params = item.optJSONObject("params");
        String id = params == null ? "" : params.optString("turn_id");
        if (id.isEmpty()) return;
        // Keep completed-but-queued speech interruptible until the next user turn.
        if (!cancelled.contains(id)) outstanding.add(id);
    }

    public boolean discard(JSONObject item) {
        if (item == null || !sessionId.equals(item.optString("session_id"))) return false;
        JSONObject params = item.optJSONObject("params");
        return params != null && cancelled.contains(params.optString("turn_id"));
    }

    public void speakingInbox(JSONObject item) {
        JSONObject params = item.optJSONObject("params");
        utteranceTurn = params == null ? "" : params.optString("turn_id");
    }

    public void deliveredInbox(JSONObject item, boolean success) {
        JSONObject params = item.optJSONObject("params");
        if (params == null || !success) return;
        String event = params.optString("event");
        if (Set.of("result", "clarification", "error", "cancelled").contains(event)) outstanding.remove(params.optString("turn_id"));
    }

    private void forwardUsage(String id) {
        String turnId = responseTurns.get(id);
        JSONObject observation = usage.get(id);
        if (turnId == null || observation == null) return;
        observation = obj("turn_id", turnId, "response_id", id, "usage", observation.optJSONObject("usage"));
        emit("model_usage", observation);
        usage.remove(id);
        usageSent.add(id);
    }

    private void emit(String type, JSONObject data) {
        JSONObject event = Z0LiveEvents.create(sessionId, type, "", data);
        if (event != null) events.accept(event);
    }
    private static JSONObject obj(Object... fields) { return Z0LiveEventsObject.object(fields); }
}
