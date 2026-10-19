package app.lawnchair.zer0.voice.z0live;

import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpServer;
import java.io.IOException;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.concurrent.atomic.AtomicLong;
import okhttp3.OkHttpClient;
import org.json.JSONArray;
import org.json.JSONObject;

/** Actual client + portable service state against PR #27's strict HTTP/floor/usage contract. */
public final class Z0LiveMultiAgentContractTest {
    private static int checks;
    private static final String[] BUILTINS = {"tell_hermes", "get_context", "summarize", "work_search", "ask_hermes", "start_task", "task_status", "what_changed", "ask_z0", "cancel_work", "future_builtin"};
    private static final Set<String> TYPES = Set.of("user_transcript", "assistant_transcript", "tool_call", "tool_result", "summary", "feedback", "app_state", "floor", "barge_in", "cancel", "model_usage");
    private static final List<JSONObject> received = new ArrayList<>();
    private static final Set<String> eventIds = new HashSet<>(), turns = new HashSet<>(), paths = new HashSet<>();
    private static String floor = "quiet";
    private static boolean ended, workersCancelled;

    public static void main(String[] args) throws Exception {
        HttpServer server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        server.createContext("/", Z0LiveMultiAgentContractTest::handle);
        server.start();
        try {
            String base = "http://127.0.0.1:" + server.getAddress().getPort();
            Z0LiveProtocol client = new Z0LiveProtocol(new OkHttpClient(), base, "fixture-device", base + "/provider");
            Z0LiveProtocol.Session mint = client.session(new JSONObject(), new JSONArray());
            check(mint.builtinTools.size() == BUILTINS.length, "all mint tools retained including future additive tool");
            for (String name : BUILTINS) {
                check(client.isBuiltin(mint.sessionId, name), "mint routes before local dispatch: " + name);
                check(client.tool(mint.sessionId, "call-" + name, name, toolArguments(name).toString()).getBoolean("ok"), "POST named builtin " + name);
                check(paths.contains("/v1/tools/" + name), "exact route " + name);
            }
            try { client.tool(mint.sessionId, "bad", "not_minted", "{}"); throw new AssertionError("unminted builtin accepted"); }
            catch (IOException expected) { checks++; }
            check(!client.isBuiltin("other-session", "ask_z0"), "mint catalog is session-scoped, not global");

            workersCancelled = false;
            Z0LiveTelemetry queue = new Z0LiveTelemetry();
            AtomicLong time = new AtomicLong(1000);
            Z0LiveBrokerSession state = new Z0LiveBrokerSession(queue::offer, time::get);
            state.heartbeat();
            check(queue.size() == 0, "no heartbeat without human-started minted session");
            state.start(mint.sessionId);
            send(client, queue);
            check(floor.equals("quiet"), "initial quiet floor");
            int initial = received.size();
            time.addAndGet(19999); state.heartbeat(); send(client, queue);
            check(received.size() == initial, "bounded heartbeat interval");
            time.incrementAndGet(); state.heartbeat(); send(client, queue);
            check(last("app_state").getJSONObject("data").getBoolean("active"), "quiet live session heartbeat accepted");
            check(last("app_state").getJSONObject("data").getString("floor").equals("quiet"), "heartbeat preserves floor");

            state.realtime(event("input_audio_buffer.speech_started"), true); send(client, queue);
            check(floor.equals("user"), "VAD start forwards user floor");
            check(!last("floor").getJSONObject("data").getBoolean("barge_in") && !workersCancelled, "idle start is not barge-in and leaves desktop work alone");
            state.realtime(event("input_audio_buffer.speech_stopped"), true); send(client, queue);
            check(floor.equals("quiet"), "VAD stop releases quiet");
            state.realtime(response("response.created", "resp-1"), true); send(client, queue);
            check(floor.equals("assistant"), "response.created takes assistant floor");
            state.realtime(event("output_audio_buffer.started"), true);
            state.realtime(response("response.done", "resp-1"), true); send(client, queue);
            check(floor.equals("assistant"), "response.done cannot release audible buffered audio");
            state.realtime(event("input_audio_buffer.speech_started"), true);
            state.realtime(event("output_audio_buffer.cleared"), true); send(client, queue);
            check(floor.equals("user") && workersCancelled, "barge event reaches worker cancellation and keeps user floor");
            check(last("floor").getJSONObject("data").getBoolean("barge_in"), "barge_in flag is on floor event");
            state.realtime(event("input_audio_buffer.speech_stopped"), true); send(client, queue);
            check(floor.equals("quiet"), "quiet after user and actual audio stop");
            int count = received.size();
            state.realtime(event("input_audio_buffer.speech_started"), false); send(client, queue);
            check(received.size() == count && floor.equals("quiet"), "muted/focus-lost VAD cannot claim floor or cancel work");
            state.realtime(event("input_audio_buffer.speech_started"), true);
            state.inputMuted(); send(client, queue);
            check(floor.equals("quiet"), "mute releases unfinished user floor");

            JSONObject admitted = client.tool(mint.sessionId, "work-1", "ask_z0", "{\"question\":\"fixture\"}");
            state.realtime(response("response.created", "work-response"), true);
            state.toolCalled("work-1", obj("response_id", "work-response"));
            JSONObject done = response("response.done", "work-response");
            done.getJSONObject("response").put("usage", obj("input_tokens", 12, "output_tokens", 5, "input_token_details", obj("cached_tokens", 4)));
            state.realtime(done, true); send(client, queue);
            check(last("model_usage") == null, "usage waits for broker-owned turn, never sends invalid target");
            check(state.toolResult("work-1", admitted).isEmpty(), "normal admission not stale"); send(client, queue);
            JSONObject observation = last("model_usage").getJSONObject("data");
            check(observation.getString("turn_id").equals(admitted.getString("turn_id")), "usage associates actual broker turn");
            check(observation.getString("response_id").equals("work-response"), "provider response id preserved");
            check(observation.getJSONObject("usage").getJSONObject("input_token_details").getInt("cached_tokens") == 4, "full supplied usage forwarded without fabricated cost");
            count = received.size(); state.realtime(done, true); send(client, queue);
            check(received.size() == count, "duplicate response.done usage not double-counted");
            state.realtime(response("response.created", "continuation"), true);
            JSONObject continuation = response("response.done", "continuation");
            continuation.getJSONObject("response").put("usage", obj("input_tokens", 3));
            state.realtime(continuation, true); send(client, queue);
            check(last("model_usage").getJSONObject("data").getString("response_id").equals("continuation"), "tool continuation usage retains work turn");

            JSONObject queued = inbox(41, admitted.getString("turn_id"));
            state.inbox(queued);
            state.speakingInbox(queued);
            state.realtime(response("response.created", "inbox-relay"), true);
            JSONObject relay = response("response.done", "inbox-relay");
            relay.getJSONObject("response").put("usage", obj("input_tokens", 17, "output_tokens", 9));
            state.realtime(relay, true); send(client, queue);
            check(last("model_usage").getJSONObject("data").getString("response_id").equals("inbox-relay"), "Realtime inbox relay usage forwarded");
            check(last("model_usage").getJSONObject("data").getString("turn_id").equals(admitted.getString("turn_id")), "inbox relay usage scoped to originating turn");
            check(!state.discard(queued), "uncancelled local queued speech allowed");
            workersCancelled = false;
            check(state.realtime(event("input_audio_buffer.speech_started"), true), "barge-in detects outstanding work even while audio quiet");
            send(client, queue);
            check(workersCancelled && state.discard(queued), "barge cancels desktop work and drops queued interrupted-turn speech");
            check(state.discard(inbox(42, admitted.getString("turn_id"))), "late inbox result for interrupted turn cannot replay");
            check(!state.discard(inbox(43, "different-turn")), "unrelated turn is not dropped");
            JSONObject foreign = inbox(44, admitted.getString("turn_id")).put("session_id", "other-session");
            check(!state.discard(foreign), "drop policy scoped to human session");
            state.realtime(event("input_audio_buffer.speech_stopped"), true); send(client, queue);

            for (String text : new String[] {"never mind", "Nevermind!", "cancel that.", "stop that"}) {
                workersCancelled = false;
                check(state.transcript(text), "literal topic-change detected: " + text); send(client, queue);
                check(workersCancelled && last("cancel").getJSONObject("data").getBoolean("topic_changed"), "topic-change schema reaches broker cancellation");
            }
            check(!state.transcript("explain what never mind means"), "quoted/embedded words do not cancel");
            workersCancelled = false; state.cancelWork(); send(client, queue);
            check(workersCancelled, "explicit local cancel event reaches workers alongside cancel_work dispatch");

            state.realtime(response("response.created", "racing-response"), true);
            state.realtime(event("input_audio_buffer.speech_started"), true); send(client, queue);
            state.toolCalled("racing-call", obj("response_id", "racing-response"));
            JSONObject late = client.tool(mint.sessionId, "racing-call", "ask_z0", "{\"question\":\"late\"}");
            String lateTurn = state.toolResult("racing-call", late);
            check(!lateTurn.isEmpty() && state.discard(inbox(45, lateTurn)), "barge/admission race suppresses late local speech");
            client.cancelTurn(mint.sessionId, lateTurn);
            check(paths.contains("/v1/cancel"), "racing admission cancelled by exact turn, not newer work tree");
            time.addAndGet(20000); state.heartbeat(); send(client, queue);
            check(last("app_state").getJSONObject("data").getString("floor").equals("user"), "heartbeat never releases held floor");
            state.realtime(event("input_audio_buffer.speech_stopped"), true); send(client, queue);
            state.realtime(response("response.created", "old-response"), true);
            state.realtime(response("response.created", "new-response"), true);
            state.realtime(response("response.done", "old-response"), true); send(client, queue);
            check(floor.equals("assistant"), "late old response.done cannot release newer assistant floor");
            state.realtime(event("input_audio_buffer.speech_started"), true);
            state.realtime(response("response.done", "new-response"), true); send(client, queue);
            check(floor.equals("user"), "response.done cannot release user floor");
            state.realtime(event("input_audio_buffer.speech_stopped"), true); send(client, queue);
            JSONObject finished = client.tool(mint.sessionId, "finished-call", "ask_z0", "{\"question\":\"finished\"}");
            state.toolCalled("finished-call", obj("response_id", "finished-response"));
            state.toolResult("finished-call", finished);
            JSONObject completedInbox = inbox(50, finished.getString("turn_id"));
            state.inbox(completedInbox);
            state.deliveredInbox(completedInbox, true);
            check(!state.realtime(event("input_audio_buffer.speech_started"), true), "fully heard final result is not outstanding work on next utterance");
            send(client, queue);
            state.realtime(event("input_audio_buffer.speech_stopped"), true); send(client, queue);
            String previousUsage = last("model_usage").getJSONObject("data").getString("response_id");
            state.realtime(response("response.created", "ordinary-chat"), true);
            JSONObject chat = response("response.done", "ordinary-chat");
            chat.getJSONObject("response").put("usage", obj("input_tokens", 2));
            state.realtime(chat, true); send(client, queue);
            check(last("model_usage").getJSONObject("data").getString("response_id").equals(previousUsage), "ordinary chat never charged to an earlier work turn");
            time.addAndGet(20000); state.heartbeat(); send(client, queue);
            check(last("app_state").getJSONObject("data").getString("floor").equals("quiet"), "heartbeat preserves recovered quiet floor");
            state.end();
            client.endSession(mint.sessionId);
            count = received.size(); time.addAndGet(120000); state.heartbeat(); state.cancelWork(); send(client, queue);
            check(ended && received.size() == count, "end stops heartbeat and cancellation observations");
            state.start("new-human-session");
            check(!state.discard(inbox(46, admitted.getString("turn_id"))), "new human session has independent cancellation state");
            System.out.println("PASS: " + checks + " multi-agent broker contract assertions (strict PR #27 fake, no device/provider)");
        } finally { server.stop(0); }
    }

    private static void send(Z0LiveProtocol client, Z0LiveTelemetry queue) throws IOException {
        JSONArray batch = queue.batch(100);
        if (batch.length() == 0) return;
        client.events(batch);
        // Retry identical batch: server-side IDs must deduplicate floor/cancel/usage observations.
        client.events(batch);
        queue.delivered(batch);
    }
    private static JSONObject last(String type) {
        for (int i = received.size() - 1; i >= 0; i--) if (received.get(i).optString("type").equals(type)) return received.get(i);
        return null;
    }
    private static JSONObject inbox(long id, String turn) { return obj("id", id, "session_id", "broker-session", "say", "fixture speech", "action", JSONObject.NULL, "params", obj("turn_id", turn, "event", "result")); }
    private static JSONObject toolArguments(String name) {
        switch (name) {
            case "ask_z0": case "ask_hermes": return obj("question", "fixture work");
            case "tell_hermes": return obj("text", "fixture request", "kind", "request");
            case "summarize": return obj("text", "fixture summary");
            case "work_search": return obj("query", "fixture evidence");
            case "start_task": return obj("title", "fixture title", "details", "fixture details");
            default: return new JSONObject();
        }
    }
    private static JSONObject event(String type) { return obj("type", type); }
    private static JSONObject response(String type, String id) { return obj("type", type, "response", obj("id", id, "status", "completed")); }
    private static JSONObject obj(Object... fields) { return Z0LiveEventsObject.object(fields); }
    private static void check(boolean value, String message) { if (!value) throw new AssertionError(message); checks++; }

    private static synchronized void handle(HttpExchange exchange) throws IOException {
        String path = exchange.getRequestURI().getPath(); paths.add(path);
        int status = 200;
        JSONObject result;
        try {
            if (!"Bearer fixture-device".equals(exchange.getRequestHeaders().getFirst("Authorization"))) throw new IllegalArgumentException("auth");
            if (!"POST".equals(exchange.getRequestMethod())) throw new IllegalArgumentException("method");
            String body = new String(exchange.getRequestBody().readAllBytes(), StandardCharsets.UTF_8);
            if (path.equals("/v1/realtime/session")) {
                JSONObject input = new JSONObject(body);
                if (!input.getString("client").equals("lawnchair") || input.optJSONArray("tools") == null) throw new IllegalArgumentException("mint");
                result = obj("value", "fixture-ephemeral", "expires_at", System.currentTimeMillis() / 1000 + 120, "model", "gpt-realtime", "voice", "marin", "instructions_version", "z0live.persona.v2", "session_id", "broker-session", "builtin_tools", new JSONArray(BUILTINS));
            } else if (path.equals("/v1/events")) {
                JSONArray batch = new JSONArray(body);
                if (batch.length() < 1 || batch.length() > 100 || ended) throw new IllegalArgumentException("batch");
                // Validate the whole batch first, as the real broker rejects atomically.
                for (int i = 0; i < batch.length(); i++) {
                    JSONObject e = batch.getJSONObject(i), data = e.getJSONObject("data");
                    String type = e.getString("type");
                    if (!e.getString("session_id").equals("broker-session") || !TYPES.contains(type) || e.getDouble("ts") > System.currentTimeMillis() / 1000.0 + 1 || data.getString("event_id").length() != 36) throw new IllegalArgumentException("event");
                    if (type.equals("floor") && !Set.of("user", "assistant", "quiet").contains(data.getString("state"))) throw new IllegalArgumentException("floor");
                    if (type.equals("model_usage") && (!turns.contains(data.getString("turn_id")) || data.optJSONObject("usage") == null)) throw new IllegalArgumentException("usage scope");
                }
                int accepted = 0;
                for (int i = 0; i < batch.length(); i++) {
                    JSONObject e = batch.getJSONObject(i), data = e.getJSONObject("data");
                    if (!eventIds.add(data.getString("event_id"))) continue;
                    accepted++; received.add(e);
                    String type = e.getString("type");
                    if (type.equals("floor")) { floor = data.getString("state"); if (data.optBoolean("barge_in")) workersCancelled = true; }
                    else if (type.equals("barge_in") || type.equals("cancel") || data.optBoolean("topic_changed")) workersCancelled = true;
                    else if (type.equals("app_state") && data.has("floor")) floor = data.getString("floor");
                }
                result = obj("ok", true, "accepted", accepted);
            } else if (path.equals("/v1/sessions/broker-session/end")) {
                if (new JSONObject(body).length() != 0) throw new IllegalArgumentException("end");
                ended = true; result = obj("ok", true);
            } else {
                JSONObject input = new JSONObject(body);
                if (!input.getString("session_id").equals("broker-session")) throw new IllegalArgumentException("owner");
                if (path.equals("/v1/cancel")) {
                    if (!turns.contains(input.getString("turn_id"))) throw new IllegalArgumentException("cancel target");
                    workersCancelled = true; result = obj("ok", true, "status", "cancelling", "turn_ids", new JSONArray().put(input.getString("turn_id")), "say", "Cancellation requested.");
                } else {
                    String name = path.substring("/v1/tools/".length());
                    if (!path.startsWith("/v1/tools/") || !Set.of(BUILTINS).contains(name) || input.optJSONObject("arguments") == null || !input.has("call_id")) throw new IllegalArgumentException("tool");
                    JSONObject arguments = input.getJSONObject("arguments");
                    for (String required : toolArguments(name).keySet()) {
                        if (arguments.optString(required).isEmpty()) throw new IllegalArgumentException("missing tool argument " + required);
                    }
                    if (name.equals("cancel_work") && arguments.length() != 0) throw new IllegalArgumentException("cancel_work arguments");
                    if (name.equals("ask_z0")) {
                        if (arguments.optString("question").isEmpty()) throw new IllegalArgumentException("question");
                        String turn = "turn-" + input.getString("call_id");
                        turns.add(turn);
                        result = obj("ok", true, "id", turn, "status", "queued", "turn_id", turn, "say", "Checking.");
                    } else if (name.equals("cancel_work")) {
                        workersCancelled = true;
                        result = obj("ok", true, "status", "cancelling", "turn_ids", new JSONArray(turns), "say", "Stopping work.");
                    } else result = obj("ok", true, "status", "queued");
                }
            }
        } catch (Exception error) {
            status = 400; result = obj("error", error.toString());
        }
        byte[] bytes = result.toString().getBytes(StandardCharsets.UTF_8);
        exchange.getResponseHeaders().set("Content-Type", "application/json");
        exchange.sendResponseHeaders(status, bytes.length); exchange.getResponseBody().write(bytes); exchange.close();
    }
}
