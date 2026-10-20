package app.lawnchair.zer0.voice.z0live;

import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpServer;
import java.io.IOException;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import okhttp3.OkHttpClient;
import org.json.JSONArray;
import org.json.JSONObject;

/** Regression fixtures mirror sibling z0live/docs/API.md, not client-invented routes. */
public final class Z0LiveBackendContractTest {
    private static final String DEVICE = "mock-device-credential";
    private static final List<String> paths = new ArrayList<>();
    private static int assertions;

    public static void main(String[] args) throws Exception {
        HttpServer server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        server.createContext("/", Z0LiveBackendContractTest::handle);
        server.start();
        try {
            String base = "http://127.0.0.1:" + server.getAddress().getPort();
            Z0LiveProtocol client = new Z0LiveProtocol(new OkHttpClient(), base, DEVICE, base + "/mock-openai");
            check(client.health().getBoolean("ok"), "authenticated canonical health route");
            Z0LiveProtocol.Session session = client.session(new JSONObject().put("screen", "voice"), new JSONArray());
            check("broker-session-1".equals(session.sessionId), "preserve broker session identity");
            check("mock-ephemeral".equals(session.secret), "parse ephemeral credential");
            Z0LiveProtocol.InboxPage page = client.inboxPage(7);
            check(page.messages.getJSONObject(0).getLong("id") == 8, "canonical inbox messages wrapper");
            check(page.after == 8 && !page.hasMore, "canonical page cursor and pagination");
            for (String tool : new String[] {"tell_hermes", "get_context", "summarize"}) {
                JSONObject arguments = "get_context".equals(tool) ? new JSONObject() : new JSONObject().put("text", "mock transcript");
                check(client.tool(session.sessionId, "call-" + tool, tool, arguments.toString()).getBoolean("ok"), "builtin route " + tool);
            }
            JSONArray batch = new JSONArray().put(new JSONObject().put("session_id", session.sessionId)
                    .put("ts", System.currentTimeMillis() / 1000.0).put("type", "user_transcript")
                    .put("text", "fixture transcript").put("data", new JSONObject().put("event_id", "mock-event-1")));
            for (String source : new String[] {"conversation.item.input_audio_transcription.completed",
                    "response.output_audio_transcript.done", "response.audio_transcript.done", "session_end"}) {
                JSONObject event = Z0LiveEvents.create(session.sessionId, source, "fixture", new JSONObject());
                check(event.getDouble("ts") < System.currentTimeMillis() / 1000.0 + 1, "Unix seconds");
                check(event.getJSONObject("data").getString("event_id").length() == 36, "stable retry dedupe identity");
                if ("session_end".equals(source)) check(event.getJSONObject("data").getBoolean("session_end"), "end marker");
                batch.put(event);
            }
            check(Z0LiveEvents.create("", "session_start", "", new JSONObject()) == null, "no fabricated broker identity");
            client.events(batch);
            client.endSession(session.sessionId);
            check(paths.contains("/v1/sessions/broker-session-1/end"), "explicit broker session end");
            check(paths.contains("/v1/events"), "canonical batched events route");
            check(!paths.contains("/health") && !paths.contains("/v1/session") && !paths.contains("/v1/tools"), "no invented routes");
            System.out.println("PASS: " + assertions + " canonical backend contract assertions");
        } finally {
            server.stop(0);
        }
    }

    private static synchronized void handle(HttpExchange exchange) throws IOException {
        String path = exchange.getRequestURI().getPath();
        paths.add(path);
        int status = 200;
        String response;
        try {
            check(("Bearer " + DEVICE).equals(exchange.getRequestHeaders().getFirst("Authorization")), "device auth on " + path);
            String body = new String(exchange.getRequestBody().readAllBytes(), StandardCharsets.UTF_8);
            switch (path) {
                case "/v1/health":
                    check("GET".equals(exchange.getRequestMethod()), "health GET");
                    response = "{\"ok\":true,\"version\":\"1.0.0\"}";
                    break;
                case "/v1/realtime/session":
                    check("POST".equals(exchange.getRequestMethod()), "session POST");
                    JSONObject input = new JSONObject(body);
                    check("lawnchair".equals(input.getString("client")), "explicit client identifier");
                    check(input.getJSONArray("tools").length() == 0, "explicit tool declarations");
                    response = new JSONObject().put("value", "mock-ephemeral").put("expires_at", System.currentTimeMillis() / 1000 + 120)
                            .put("model", "mock-realtime").put("voice", "mock-voice").put("session_id", "broker-session-1").toString();
                    break;
                case "/v1/inbox":
                    check("after=7".equals(exchange.getRequestURI().getQuery()), "inbox cursor");
                    response = "{\"messages\":[{\"id\":8,\"say\":\"fixture reply\",\"action\":null,\"params\":{},\"session_id\":\"broker-session-1\"}],\"after\":8,\"has_more\":false}";
                    break;
                case "/v1/tools/tell_hermes":
                case "/v1/tools/get_context":
                case "/v1/tools/summarize":
                    JSONObject request = new JSONObject(body);
                    check("broker-session-1".equals(request.getString("session_id")), "tool broker session");
                    check(request.get("arguments") instanceof JSONObject, "tool arguments are parsed JSON");
                    response = "{\"ok\":true,\"status\":\"queued\"}";
                    break;
                case "/v1/sessions/broker-session-1/end":
                    check("POST".equals(exchange.getRequestMethod()), "end POST");
                    check(new JSONObject(body).length() == 0, "explicit empty end body");
                    response = "{\"ok\":true}";
                    break;
                case "/v1/events":
                    JSONArray events = new JSONArray(body);
                    for (int i = 0; i < events.length(); i++) {
                        JSONObject event = events.getJSONObject(i);
                        check(event.getJSONObject("data").has("event_id"), "bare event array and dedupe identity");
                        check(java.util.Set.of("user_transcript", "assistant_transcript", "tool_call", "tool_result", "summary", "feedback", "app_state").contains(event.getString("type")), "canonical event vocabulary");
                    }
                    response = "{\"ok\":true,\"accepted\":1}";
                    break;
                default:
                    status = 404;
                    response = "{\"error\":\"noncanonical route\"}";
            }
        } catch (Throwable error) {
            status = 500;
            response = "{\"error\":\"mock assertion failed\"}";
        }
        byte[] bytes = response.getBytes(StandardCharsets.UTF_8);
        exchange.getResponseHeaders().set("Content-Type", "application/json");
        exchange.sendResponseHeaders(status, bytes.length);
        exchange.getResponseBody().write(bytes);
        exchange.close();
    }

    private static void check(boolean condition, String message) {
        if (!condition) throw new AssertionError(message);
        assertions++;
    }
}
