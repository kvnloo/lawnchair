package app.lawnchair.zer0.voice.z0live;

import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpServer;
import java.io.IOException;
import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.List;
import java.util.Queue;
import java.util.concurrent.TimeUnit;
import okhttp3.MediaType;
import okhttp3.OkHttpClient;
import okhttp3.Protocol;
import okhttp3.Response;
import okhttp3.ResponseBody;
import org.json.JSONArray;
import org.json.JSONObject;

/** Real HTTP protocol checks, independent of Android, credentials, and hosted services. */
public final class Z0LiveProtocolTest {
    private static int checked;
    private static final String TOKEN = "pairing-test-secret";
    private static final String SECRET = "ephemeral-test-secret";

    public static void main(String[] args) throws Exception {
        pairingValidation();
        qrAndDeeplinkPairing();
        routesAndAuthorization();
        sessionValidation();
        mintedBuiltinCatalog();
        inboxHistoryHead();
        inboxValidation();
        errorsAndRedirects();
        boundedResponses();
        productionEndpointAndTimeouts();
        System.out.println("PASS: " + checked + " z0live protocol and pairing checks");
    }

    private static void pairingValidation() throws Exception {
        PairingValidation.Pairing pairing = PairingValidation.parse(
                "z0live://pair?url=https%3A%2F%2Fvoice.example%3A8443%2F&token=a%2Bb%3D");
        equal(pairing.backendUrl, "https://voice.example:8443", "pairing origin normalization");
        equal(pairing.token, "a+b=", "decode token exactly once");
        equal(new PairingValidation.Pairing("https://voice.example/", "abc").backendUrl,
                "https://voice.example", "direct pairing validates");
        equal(PairingValidation.validatePanel("https://panel.example/ripple/index.html?q=x#open"),
                "https://panel.example/ripple/index.html?q=x#open", "panel retains path");
        String[] invalidOrigins = {null, "", "http://voice.example", "ftp://voice.example",
                "https://user:secret@voice.example", "https://voice.example/api",
                "https://voice.example/?x=1", "https://voice.example/#x", "https://voice.example:0",
                "https://voice.example:65536", "https://voice.example\\@other.example",
                "https://voice.example/%2F", "https://voice.example/\n", "https:///missing"};
        for (String invalid : invalidOrigins) {
            illegal(() -> PairingValidation.validateBackend(invalid), "invalid backend");
        }
        illegal(() -> PairingValidation.validatePanel("https://user@panel.example/x"), "panel credentials");
        illegal(() -> PairingValidation.validatePanel("http://panel.example/x"), "panel cleartext");
        String valid = "url=" + encode("https://voice.example") + "&token=abc";
        String[] invalidLinks = {null, "", "https://pair?" + valid, "z0live://other?" + valid,
                "z0live://pair/?" + valid, "z0live://pair:2?" + valid,
                "z0live://user@pair?" + valid, "z0live://pair?" + valid + "#x",
                "z0live://pair?" + valid + "&token=other", "z0live://pair?" + valid + "&url=x",
                "z0live://pair?" + valid + "&unknown=x", "z0live://pair?url=https%3A%2F%2Fvoice.example",
                "z0live://pair?" + valid + "&", "z0live://pair?url=%GG&token=abc",
                "z0live://pair?url=https%3A%2F%2Fvoice.example&token="};
        for (String invalid : invalidLinks) {
            illegal(() -> PairingValidation.parse(invalid), "invalid pairing route/query");
        }
        for (String token : new String[] {null, "", "a b", "a\nb", "a\tb", "a\u007fb", "a\u200bb",
                "x".repeat(4097)}) {
            illegal(() -> new PairingValidation.Pairing("https://voice.example", token), "invalid bearer token");
        }
        illegal(() -> new Z0LiveProtocol(new OkHttpClient(), "http://127.0.0.1:1", TOKEN),
                "production never permits loopback cleartext");
        illegal(() -> new Z0LiveProtocol(new OkHttpClient(), "https://voice.example/path", TOKEN),
                "production validates backend origin");
        illegal(() -> new Z0LiveProtocol(new OkHttpClient(), "http://evil.example", TOKEN,
                "http://127.0.0.1:1/v1/realtime/calls"), "test constructor only admits loopback");
    }

    private static void qrAndDeeplinkPairing() throws Exception {
        String scanned = "z0live://pair?url=https%3A%2F%2Fvoice.example%3A8443%2F&token=a%2Bb%3D";
        PendingPairing qr = new PendingPairing();
        PendingPairing deeplink = new PendingPairing();
        qr.offer(scanned);
        deeplink.offer(scanned);
        PairingValidation.Pairing fromQr = qr.take();
        PairingValidation.Pairing fromLink = deeplink.take();
        equal(fromQr.backendUrl, fromLink.backendUrl, "QR and VIEW origins identical");
        equal(fromQr.token, fromLink.token, "QR and VIEW tokens identical");
        equal(qr.take() == null, true, "scanned request consumed only once");
        equal(deeplink.take() == null, true, "VIEW request consumed only once");
        deeplink.offer(scanned);
        illegal(() -> deeplink.offer("not a pairing QR"), "invalid scan rejected");
        equal(deeplink.take().token, fromQr.token, "invalid input preserves validated pending request");
    }

    private static void routesAndAuthorization() throws Exception {
        try (Server backend = new Server(); Server openai = new Server()) {
            Z0LiveProtocol protocol = protocol(backend, openai);
            backend.reply(200, "{\"status\":\"ok\"}");
            equal(protocol.health().getString("status"), "ok", "health response");
            Request health = backend.last();
            equal(health.method, "GET", "health method");
            equal(health.path, "/v1/health", "health route");
            equal(health.authorization, "Bearer " + TOKEN, "backend bearer authorization");

            long expires = System.currentTimeMillis() / 1000 + 300;
            backend.reply(200, sessionJson(expires, true));
            Z0LiveProtocol.Session session = protocol.session(new JSONObject().put("screen", "home"),
                    new JSONArray().put(new JSONObject().put("name", "hermes")));
            equal(session.secret, SECRET, "ephemeral secret");
            equal(session.expiresAt, expires, "session expiration seconds");
            equal(session.model, "gpt-realtime", "model");
            equal(session.voice, "marin", "voice");
            equal(session.sessionId, "sess-1", "optional session id");
            Request sessionRequest = backend.last();
            equal(sessionRequest.path, "/v1/realtime/session", "session route");
            equal(sessionRequest.method, "POST", "session method");
            JSONObject sessionBody = new JSONObject(sessionRequest.body);
            equal(sessionBody.getString("client"), "lawnchair", "session client");
            equal(sessionBody.getJSONObject("context").getString("screen"), "home", "session context");
            equal(sessionBody.getJSONArray("tools").getJSONObject(0).getString("name"), "hermes", "tools");

            String offer = "v=0\r\ns=offer\r\n";
            String answer = "v=0\r\ns=answer\r\n";
            openai.reply(201, answer);
            equal(protocol.exchangeSdp(session.secret, offer), answer, "raw SDP answer");
            Request sdp = openai.last();
            equal(sdp.method, "POST", "SDP method");
            equal(sdp.path, "/v1/realtime/calls", "GA SDP endpoint");
            equal(sdp.authorization, "Bearer " + SECRET, "SDP uses ephemeral credential");
            equal(sdp.contentType, "application/sdp; charset=utf-8", "SDP content type");
            equal(sdp.body, offer, "raw SDP offer body");

            backend.reply(204, "");
            protocol.events(new JSONArray().put(new JSONObject().put("type", "one"))
                    .put(new JSONObject().put("type", "two")));
            Request telemetry = backend.last();
            equal(telemetry.path, "/v1/events", "events route");
            equal(telemetry.method, "POST", "events method");
            JSONArray batch = new JSONArray(telemetry.body);
            equal(batch.length(), 2, "telemetry sends an array batch");
            equal(batch.getJSONObject(1).getString("type"), "two", "batch order");

            backend.reply(200, "{\"messages\":[{\"id\":19,\"text\":\"ready\"}],\"after\":19,\"has_more\":false}");
            equal(protocol.inbox(17).getJSONObject(0).getString("text"), "ready", "inbox contents");
            equal(backend.last().path, "/v1/inbox?after=17", "inbox cursor");
            equal(backend.last().method, "GET", "inbox method");

            backend.reply(200, "{\"output\":\"done\"}");
            equal(protocol.tool("sess-1", "call-1", "tell_hermes", "{\"task\":\"hello\"}")
                    .getString("output"), "done", "tool output");
            Request tool = backend.last();
            equal(tool.path, "/v1/tools/tell_hermes", "documented builtin endpoint");
            equal(tool.method, "POST", "tool method");
            JSONObject toolBody = new JSONObject(tool.body);
            equal(toolBody.getString("session_id"), "sess-1", "tool session id");
            equal(toolBody.getString("call_id"), "call-1", "tool call id");
            equal(toolBody.getJSONObject("arguments").getString("task"), "hello", "tool argument object");
            for (String builtin : new String[] {"get_context", "summarize", "ask_hermes", "work_search", "start_task", "task_status", "what_changed"}) {
                backend.reply(200, "{\"ok\":true}");
                equal(protocol.tool("sess-1", "call-extended", builtin, "{}").getBoolean("ok"), true, "extended builtin result");
                equal(backend.last().path, "/v1/tools/" + builtin, "extended builtin route");
                equal(new JSONObject(backend.last().body).getJSONObject("arguments").length(), 0, "extended parsed argument object");
            }
            for (String raw : new String[] {"not JSON", "[]", "{\"x\":1} trailing"}) {
                int before = backend.requests.size();
                io(() -> protocol.tool("sess-1", "call-2", "tell_hermes", raw), "non-object arguments rejected");
                equal(backend.requests.size(), before, "invalid arguments never reach server");
            }
            for (Request request : backend.requests) {
                equal(request.authorization, "Bearer " + TOKEN, "every backend request authenticated");
                check(!request.body.contains(SECRET), "ephemeral credential stays out of backend payloads");
            }
        }
    }

    private static void inboxHistoryHead() throws Exception {
        try (Server backend = new Server(); Server openai = new Server()) {
            Z0LiveProtocol protocol = protocol(backend, openai);
            backend.reply(200, "{\"messages\":[{\"id\":41}],\"after\":41,\"has_more\":true}");
            backend.reply(200, "{\"messages\":[{\"id\":80}],\"after\":80,\"has_more\":false}");
            equal(protocol.inboxHead(), 80L, "first connect skips to actual latest inbox id");
            equal(backend.last().path, "/v1/inbox?after=41", "history head follows real page cursor");
            backend.reply(200, "{\"messages\":[],\"after\":0,\"has_more\":false}");
            equal(protocol.inboxHead(), 0L, "empty first inbox starts at zero, not infinity");
        }
    }

    private static void mintedBuiltinCatalog() throws Exception {
        try (Server backend = new Server(); Server openai = new Server()) {
            Z0LiveProtocol protocol = protocol(backend, openai);
            long expires = System.currentTimeMillis() / 1000 + 300;
            String valid = sessionJson(expires, true);
            JSONObject minted = new JSONObject(valid).put("builtin_tools", new JSONArray().put("future_tool"));
            backend.reply(200, minted.toString());
            Z0LiveProtocol.Session session = protocol.session(new JSONObject(), new JSONArray());
            check(session.builtinTools.contains("future_tool"), "mint-time builtin catalog consumed");
            backend.reply(200, "{\"ok\":true}");
            check(protocol.tool(session.sessionId, "call-future", "future_tool", "{}").getBoolean("ok"), "new minted builtin dispatched without app release");
            equal(backend.last().path, "/v1/tools/future_tool", "minted builtin route");
            int before = backend.requests.size();
            io(() -> protocol.tool(session.sessionId, "call-old", "tell_hermes", "{}"), "unminted builtin rejected");
            equal(backend.requests.size(), before, "unminted tool makes no request");
            for (Object invalid : new Object[] {"not an array", JSONObject.NULL, new JSONArray().put("../route"), new JSONArray().put("same").put("same"), new JSONArray().put(17)}) {
                backend.reply(200, new JSONObject(valid).put("builtin_tools", invalid).toString());
                io(() -> protocol.session(new JSONObject(), new JSONArray()), "invalid builtin catalog rejected");
            }
        }
    }

    private static void sessionValidation() throws Exception {
        try (Server backend = new Server(); Server openai = new Server()) {
            Z0LiveProtocol protocol = protocol(backend, openai);
            long expires = System.currentTimeMillis() / 1000 + 300;
            backend.reply(200, sessionJson(expires, false));
            equal(protocol.session(new JSONObject(), new JSONArray()).sessionId, "", "session id optional");
            String valid = sessionJson(expires, false);
            String[] invalid = {"{}", "[]", "not JSON", "null", valid + " garbage",
                    valid.replace(SECRET, ""), valid.replace("gpt-realtime", ""), valid.replace("marin", ""),
                    valid.replace(Long.toString(expires), "0"),
                    valid.replace(Long.toString(expires), Long.toString(expires - 600)),
                    valid.replace(Long.toString(expires), "\"" + expires + "\""),
                    valid.replace(Long.toString(expires), "1.5"),
                    valid.replace("\"" + SECRET + "\"", "42")};
            for (String body : invalid) {
                backend.reply(200, body);
                io(() -> protocol.session(new JSONObject(), new JSONArray()), "invalid session metadata");
            }
        }
    }

    private static void inboxValidation() throws Exception {
        try (Server backend = new Server(); Server openai = new Server()) {
            Z0LiveProtocol protocol = protocol(backend, openai);
            backend.reply(200, "{\"messages\":[{\"id\":18}],\"after\":18,\"has_more\":true}");
            Z0LiveProtocol.InboxPage first = protocol.inboxPage(17);
            check(first.hasMore && first.after == 18, "first inbox page preserves pagination");
            backend.reply(200, "{\"messages\":[],\"after\":20,\"has_more\":false}");
            Z0LiveProtocol.InboxPage second = protocol.inboxPage(first.after);
            equal(backend.last().path, "/v1/inbox?after=18", "pagination uses returned cursor");
            check(second.after == 20 && second.messages.length() == 0, "empty page retains server cursor");
            for (String body : new String[] {"[]", "{}", "{\"messages\":[],\"after\":16,\"has_more\":false}",
                    "{\"messages\":[],\"after\":17,\"has_more\":true}",
                    "{\"messages\":[{\"id\":18},{\"id\":18}],\"after\":18,\"has_more\":false}",
                    "{\"messages\":[{\"id\":19}],\"after\":18,\"has_more\":false}",
                    "{\"messages\":[],\"after\":17.5,\"has_more\":false}"}) {
                backend.reply(200, body);
                io(() -> protocol.inboxPage(17), "malformed/regressing inbox rejected");
            }
            int before = backend.requests.size();
            io(() -> protocol.tool("sess-1", "call-1", "open_app", "{}"), "unknown tool rejected");
            io(() -> protocol.endSession("../another/session"), "unsafe end identity rejected");
            equal(backend.requests.size(), before, "invalid tool and end make no network calls");
        }
    }

    private static void errorsAndRedirects() throws Exception {
        try (Server backend = new Server(); Server openai = new Server(); Server redirect = new Server()) {
            Z0LiveProtocol protocol = protocol(backend, openai);
            backend.reply(401, "RESPONSE_PRIVATE " + TOKEN + " " + SECRET);
            IOException http = io(protocol::health, "HTTP failure");
            check(http.getMessage().contains("401"), "status code remains diagnosable");
            for (String body : new String[] {"bad JSON RESPONSE_PRIVATE", "[]", "{} trailing"}) {
                backend.reply(200, body);
                io(protocol::health, "malformed health response");
            }
            backend.redirect(redirect.base() + "/stolen");
            io(protocol::health, "backend redirects rejected");
            equal(redirect.requests.size(), 0, "backend token never follows redirect");
            openai.redirect(redirect.base() + "/stolen");
            io(() -> protocol.exchangeSdp(SECRET, "v=0\r\n"), "SDP redirects rejected");
            equal(redirect.requests.size(), 0, "ephemeral token never follows redirect");
            openai.reply(200, "");
            io(() -> protocol.exchangeSdp(SECRET, "v=0\r\n"), "empty SDP rejected");
            backend.reply(200, "{\"events\":[]}");
            io(() -> protocol.inbox(0), "inbox requires the canonical page wrapper");
            int before = backend.requests.size();
            io(() -> protocol.inbox(-1), "negative inbox cursor rejected");
            equal(backend.requests.size(), before, "invalid cursor sends no request");
            io(() -> protocol.exchangeSdp("bad\nsecret", "v=0\r\n"), "bad ephemeral secret rejected");
        }
    }

    private static void boundedResponses() throws Exception {
        try (Server backend = new Server(); Server openai = new Server()) {
            Z0LiveProtocol protocol = protocol(backend, openai);
            backend.reply(200, "{\"text\":\"" + "x".repeat(1_048_577) + "\"}");
            io(protocol::health, "large known-length response rejected");
            backend.replyChunked(200, " ".repeat(1_048_577));
            io(protocol::health, "large streaming response rejected");
            int before = backend.requests.size();
            io(() -> protocol.events(new JSONArray().put("x".repeat(1_048_577))),
                    "large event batch rejected before send");
            equal(backend.requests.size(), before, "oversized payload sends no request");
        }
    }

    private static void productionEndpointAndTimeouts() throws Exception {
        for (int originalSeconds : new int[] {0, 60}) {
            List<okhttp3.Request> requests = new ArrayList<>();
            OkHttpClient original = new OkHttpClient.Builder()
                    .connectTimeout(originalSeconds, TimeUnit.SECONDS)
                    .readTimeout(originalSeconds, TimeUnit.SECONDS)
                    .writeTimeout(originalSeconds, TimeUnit.SECONDS)
                    .callTimeout(originalSeconds, TimeUnit.SECONDS)
                    .addInterceptor(chain -> {
                        check(chain.connectTimeoutMillis() > 0 && chain.connectTimeoutMillis() <= 30_000,
                                "connection deadline bounded");
                        check(chain.readTimeoutMillis() > 0 && chain.readTimeoutMillis() <= 30_000,
                                "read deadline bounded");
                        check(chain.writeTimeoutMillis() > 0 && chain.writeTimeoutMillis() <= 30_000,
                                "write deadline bounded");
                        long callMillis = TimeUnit.NANOSECONDS.toMillis(chain.call().timeout().timeoutNanos());
                        check(callMillis > 0 && callMillis <= 30_000, "complete call deadline bounded");
                        requests.add(chain.request());
                        return new Response.Builder().request(chain.request()).protocol(Protocol.HTTP_1_1)
                                .code(200).message("OK")
                                .body(ResponseBody.create("v=0\r\n", MediaType.get("application/sdp"))).build();
                    }).build();
            Z0LiveProtocol protocol = new Z0LiveProtocol(original, "https://voice.example", TOKEN);
            protocol.exchangeSdp(SECRET, "v=0\r\n");
            equal(requests.get(0).url().toString(), "https://api.openai.com/v1/realtime/calls",
                    "production uses OpenAI GA SDP origin");
            equal(requests.get(0).header("Authorization"), "Bearer " + SECRET,
                    "production SDP uses ephemeral authorization");
        }
    }

    private static Z0LiveProtocol protocol(Server backend, Server openai) {
        return new Z0LiveProtocol(new OkHttpClient.Builder().followRedirects(true)
                .followSslRedirects(true).callTimeout(5, TimeUnit.SECONDS).build(),
                backend.base(), TOKEN, openai.base() + "/v1/realtime/calls");
    }

    private static String sessionJson(long expires, boolean withId) {
        return "{\"value\":\"" + SECRET + "\",\"expires_at\":" + expires
                + ",\"model\":\"gpt-realtime\",\"voice\":\"marin\""
                + (withId ? ",\"session_id\":\"sess-1\"" : "") + "}";
    }

    private static String encode(String value) {
        return URLEncoder.encode(value, StandardCharsets.UTF_8);
    }

    private static void equal(Object actual, Object expected, String label) {
        check(expected.equals(actual), label + ": expected " + expected + ", got " + actual);
    }

    private static void check(boolean condition, String label) {
        if (!condition) throw new AssertionError(label);
        checked++;
    }

    private static void illegal(Action action, String label) throws Exception {
        try {
            action.run();
            throw new AssertionError(label + ": expected IllegalArgumentException");
        } catch (IllegalArgumentException expected) {
            check(true, label);
        }
    }

    private static IOException io(Action action, String label) throws Exception {
        try {
            action.run();
            throw new AssertionError(label + ": expected IOException");
        } catch (IOException expected) {
            for (Throwable error = expected; error != null; error = error.getCause()) {
                String message = String.valueOf(error.getMessage());
                check(!message.contains(TOKEN) && !message.contains(SECRET)
                        && !message.contains("RESPONSE_PRIVATE"), "errors exclude credentials and response bodies");
            }
            checked++;
            return expected;
        }
    }

    private interface Action {
        void run() throws Exception;
    }

    private static final class Request {
        final String method;
        final String path;
        final String authorization;
        final String contentType;
        final String body;

        Request(HttpExchange exchange) throws IOException {
            method = exchange.getRequestMethod();
            path = exchange.getRequestURI().toString();
            authorization = exchange.getRequestHeaders().getFirst("Authorization");
            contentType = exchange.getRequestHeaders().getFirst("Content-Type");
            body = new String(exchange.getRequestBody().readAllBytes(), StandardCharsets.UTF_8);
        }
    }

    private static final class Reply {
        final int status;
        final String body;
        final String location;
        final boolean chunked;

        Reply(int status, String body, String location, boolean chunked) {
            this.status = status;
            this.body = body;
            this.location = location;
            this.chunked = chunked;
        }
    }

    private static final class Server implements AutoCloseable {
        final HttpServer server;
        final List<Request> requests = new ArrayList<>();
        final Queue<Reply> replies = new ArrayDeque<>();

        Server() throws IOException {
            server = HttpServer.create(new InetSocketAddress(InetAddress.getByName("127.0.0.1"), 0), 0);
            server.createContext("/", this::handle);
            server.start();
        }

        String base() {
            return "http://127.0.0.1:" + server.getAddress().getPort();
        }

        synchronized void reply(int status, String body) {
            replies.add(new Reply(status, body, null, false));
        }

        synchronized void replyChunked(int status, String body) {
            replies.add(new Reply(status, body, null, true));
        }

        synchronized void redirect(String target) {
            replies.add(new Reply(307, "", target, false));
        }

        synchronized Request last() {
            return requests.get(requests.size() - 1);
        }

        private synchronized void handle(HttpExchange exchange) throws IOException {
            try {
                requests.add(new Request(exchange));
                Reply reply = replies.poll();
                if (reply == null) reply = new Reply(500, "unexpected request", null, false);
                if (reply.location != null) exchange.getResponseHeaders().add("Location", reply.location);
                byte[] bytes = reply.body.getBytes(StandardCharsets.UTF_8);
                exchange.sendResponseHeaders(reply.status,
                        reply.status == 204 || bytes.length == 0 ? -1 : reply.chunked ? 0 : bytes.length);
                if (bytes.length > 0) exchange.getResponseBody().write(bytes);
            } finally {
                exchange.close();
            }
        }

        @Override
        public void close() {
            server.stop(0);
        }
    }
}
