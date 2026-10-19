package app.lawnchair.zer0.voice.z0live;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.net.URI;
import java.net.URISyntaxException;
import java.nio.charset.StandardCharsets;
import java.util.concurrent.TimeUnit;
import okhttp3.MediaType;
import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.RequestBody;
import okhttp3.Response;
import okhttp3.ResponseBody;
import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;
import org.json.JSONTokener;

/** Authenticated backend protocol and the ephemeral OpenAI Realtime SDP exchange. */
public final class Z0LiveProtocol {
    private static final String OPENAI_CALLS = "https://api.openai.com/v1/realtime/calls";
    private static final MediaType JSON = MediaType.get("application/json; charset=utf-8");
    private static final MediaType SDP = MediaType.get("application/sdp; charset=utf-8");
    private static final int MAX_BODY_BYTES = 1024 * 1024;

    private final OkHttpClient client;
    private final String backend;
    private final String token;
    private final String callsEndpoint;

    public static final class Session {
        public final String secret;
        public final long expiresAt;
        public final String model;
        public final String voice;
        public final String sessionId;

        private Session(String secret, long expiresAt, String model, String voice, String sessionId) {
            this.secret = secret;
            this.expiresAt = expiresAt;
            this.model = model;
            this.voice = voice;
            this.sessionId = sessionId;
        }
    }

    public Z0LiveProtocol(OkHttpClient client, String backend, String token) {
        this.client = boundedClient(client);
        this.backend = PairingValidation.validateBackend(backend);
        this.token = PairingValidation.validateToken(token);
        callsEndpoint = OPENAI_CALLS;
    }

    /** Loopback-only injection for JVM HTTP tests; production always uses validated HTTPS. */
    Z0LiveProtocol(OkHttpClient client, String backend, String token, String callsEndpoint) {
        this.client = boundedClient(client);
        this.backend = loopbackEndpoint(backend, true);
        this.token = PairingValidation.validateToken(token);
        this.callsEndpoint = loopbackEndpoint(callsEndpoint, false);
    }

    public JSONObject health() throws IOException {
        return object(execute(backendRequest("/v1/health").get().build()));
    }

    public Session session(JSONObject context, JSONArray tools) throws IOException {
        if (context == null || tools == null) throw new IOException("Missing session inputs");
        try {
            JSONObject payload = new JSONObject().put("client", "lawnchair")
                    .put("context", context).put("tools", tools);
            JSONObject result = object(execute(backendRequest("/v1/realtime/session")
                    .post(body(payload.toString(), JSON)).build()));
            String secret = requiredText(result, "value", 4096);
            try {
                PairingValidation.validateToken(secret);
            } catch (IllegalArgumentException ignored) {
                throw new IOException("Invalid session credential");
            }
            Object rawExpiry = result.opt("expires_at");
            if (!(rawExpiry instanceof Number)) throw new IOException("Invalid session expiration");
            Number expiry = (Number) rawExpiry;
            long expiresAt = expiry.longValue();
            if (expiry.doubleValue() != expiresAt || expiresAt <= System.currentTimeMillis() / 1000) {
                throw new IOException("Expired or invalid session credential");
            }
            String sessionId = result.has("session_id") ? requiredText(result, "session_id", 256) : "";
            return new Session(secret, expiresAt, requiredText(result, "model", 256),
                    requiredText(result, "voice", 256), sessionId);
        } catch (JSONException ignored) {
            throw new IOException("Invalid session JSON");
        }
    }

    public String exchangeSdp(String secret, String offer) throws IOException {
        try {
            PairingValidation.validateToken(secret);
        } catch (IllegalArgumentException ignored) {
            throw new IOException("Invalid session credential");
        }
        if (offer == null || offer.trim().isEmpty()) throw new IOException("Missing SDP offer");
        Request request = new Request.Builder().url(callsEndpoint)
                .header("Authorization", "Bearer " + secret)
                .post(body(offer, SDP)).build();
        String answer = execute(request);
        if (answer.trim().isEmpty()) throw new IOException("Empty SDP response");
        return answer;
    }

    public void events(JSONArray batch) throws IOException {
        if (batch == null) throw new IOException("Missing event batch");
        execute(backendRequest("/v1/events").post(body(batch.toString(), JSON)).build());
    }

    public static final class InboxPage {
        public final JSONArray messages;
        public final long after;
        public final boolean hasMore;

        private InboxPage(JSONArray messages, long after, boolean hasMore) {
            this.messages = messages;
            this.after = after;
            this.hasMore = hasMore;
        }
    }

    public JSONArray inbox(long after) throws IOException {
        return inboxPage(after).messages;
    }

    public InboxPage inboxPage(long after) throws IOException {
        if (after < 0) throw new IOException("Invalid inbox cursor");
        JSONObject result = object(execute(backendRequest("/v1/inbox?after=" + after).get().build()));
        JSONArray messages = result.optJSONArray("messages");
        Object cursor = result.opt("after"), more = result.opt("has_more");
        if (messages == null || messages.length() > 100 || !(cursor instanceof Number)
                || !(more instanceof Boolean)) throw new IOException("Invalid inbox page");
        long next = ((Number) cursor).longValue();
        if (((Number) cursor).doubleValue() != next || next < after) throw new IOException("Invalid inbox cursor");
        long previous = after;
        for (int i = 0; i < messages.length(); i++) {
            JSONObject message = messages.optJSONObject(i);
            Object id = message == null ? null : message.opt("id");
            if (!(id instanceof Number) || ((Number) id).doubleValue() != ((Number) id).longValue()
                    || ((Number) id).longValue() <= previous || ((Number) id).longValue() > next) {
                throw new IOException("Invalid inbox message identity");
            }
            previous = ((Number) id).longValue();
        }
        if ((Boolean) more && messages.length() == 0) throw new IOException("Non-progressing inbox page");
        return new InboxPage(messages, next, (Boolean) more);
    }

    public void endSession(String sessionId) throws IOException {
        if (sessionId == null || !sessionId.matches("[A-Za-z0-9_-]{1,256}")) {
            throw new IOException("Invalid session identity");
        }
        execute(backendRequest("/v1/sessions/" + sessionId + "/end").post(body("{}", JSON)).build());
    }

    /** Dispatch only the broker's documented builtins; never grant remote phone authority. */
    public JSONObject tool(String sessionId, String callId, String name, String arguments) throws IOException {
        if (sessionId == null || callId == null || name == null || arguments == null) {
            throw new IOException("Missing tool inputs");
        }
        if (!("tell_hermes".equals(name) || "get_context".equals(name) || "summarize".equals(name))) {
            throw new IOException("Unsupported broker tool");
        }
        JSONObject argumentValue = object(arguments);
        try {
            JSONObject payload = new JSONObject().put("session_id", sessionId).put("call_id", callId)
                    .put("arguments", argumentValue);
            return object(execute(backendRequest("/v1/tools/" + name)
                    .post(body(payload.toString(), JSON)).build()));
        } catch (JSONException ignored) {
            throw new IOException("Invalid tool JSON");
        }
    }

    private Request.Builder backendRequest(String route) {
        return new Request.Builder().url(backend + route).header("Authorization", "Bearer " + token);
    }

    private String execute(Request request) throws IOException {
        Response response;
        try {
            response = client.newCall(request).execute();
        } catch (IOException | RuntimeException ignored) {
            throw new IOException("z0live connection failed");
        }
        try {
            if (!response.isSuccessful()) throw new IOException("z0live HTTP " + response.code());
            ResponseBody responseBody = response.body();
            if (responseBody == null) throw new IOException("Missing z0live response");
            if (responseBody.contentLength() > MAX_BODY_BYTES) throw new IOException("z0live response too large");
            try (InputStream stream = responseBody.byteStream();
                    ByteArrayOutputStream output = new ByteArrayOutputStream()) {
                byte[] buffer = new byte[8192];
                int length;
                while ((length = stream.read(buffer)) != -1) {
                    if (output.size() + length > MAX_BODY_BYTES) throw new IOException("Response too large");
                    output.write(buffer, 0, length);
                }
                return new String(output.toByteArray(), StandardCharsets.UTF_8);
            } catch (IOException | RuntimeException ignored) {
                throw new IOException("Invalid or oversized z0live response");
            }
        } finally {
            response.close();
        }
    }

    private static RequestBody body(String value, MediaType contentType) throws IOException {
        if (value == null || value.length() > MAX_BODY_BYTES) throw new IOException("Invalid request body");
        byte[] bytes = value.getBytes(StandardCharsets.UTF_8);
        if (bytes.length > MAX_BODY_BYTES) throw new IOException("Request body too large");
        return RequestBody.create(bytes, contentType);
    }

    private static JSONObject object(String value) throws IOException {
        Object result = parse(value);
        if (!(result instanceof JSONObject)) throw new IOException("Invalid z0live JSON object");
        return (JSONObject) result;
    }

    private static Object parse(String value) throws IOException {
        try {
            JSONTokener tokener = new JSONTokener(value);
            Object result = tokener.nextValue();
            if (tokener.nextClean() != 0) throw new IOException("Invalid z0live JSON");
            return result;
        } catch (JSONException ignored) {
            throw new IOException("Invalid z0live JSON");
        }
    }

    private static String requiredText(JSONObject object, String name, int maximumLength) throws IOException {
        Object value = object.opt(name);
        if (!(value instanceof String)) throw new IOException("Missing or invalid session field");
        String text = (String) value;
        if (text.trim().isEmpty() || text.length() > maximumLength) {
            throw new IOException("Missing or invalid session field");
        }
        for (int i = 0; i < text.length(); i++) {
            if (Character.isISOControl(text.charAt(i)) || Character.getType(text.charAt(i)) == Character.FORMAT) {
                throw new IOException("Invalid session field");
            }
        }
        return text;
    }

    private static OkHttpClient boundedClient(OkHttpClient original) {
        if (original == null) throw new IllegalArgumentException("Missing HTTP client");
        return original.newBuilder().followRedirects(false).followSslRedirects(false)
                .retryOnConnectionFailure(false)
                .connectTimeout(cappedTimeout(original.connectTimeoutMillis(), 10000), TimeUnit.MILLISECONDS)
                .readTimeout(cappedTimeout(original.readTimeoutMillis(), 15000), TimeUnit.MILLISECONDS)
                .writeTimeout(cappedTimeout(original.writeTimeoutMillis(), 15000), TimeUnit.MILLISECONDS)
                .callTimeout(cappedTimeout(original.callTimeoutMillis(), 20000), TimeUnit.MILLISECONDS).build();
    }

    private static int cappedTimeout(int timeout, int maximum) {
        return timeout <= 0 ? maximum : Math.min(timeout, maximum);
    }

    private static String loopbackEndpoint(String value, boolean originOnly) {
        try {
            URI uri = new URI(value);
            if (!"http".equals(uri.getScheme()) || !"127.0.0.1".equals(uri.getHost())
                    || uri.getRawUserInfo() != null || uri.getRawQuery() != null || uri.getRawFragment() != null
                    || uri.getPort() <= 0 || uri.getPort() > 65535
                    || (originOnly && !(uri.getRawPath().isEmpty() || "/".equals(uri.getRawPath())))) {
                throw new IllegalArgumentException("Test endpoint must be an HTTP loopback URL");
            }
            return originOnly && value.endsWith("/") ? value.substring(0, value.length() - 1) : value;
        } catch (URISyntaxException | NullPointerException ignored) {
            throw new IllegalArgumentException("Test endpoint must be an HTTP loopback URL");
        }
    }
}
