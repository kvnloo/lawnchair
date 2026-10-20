package app.lawnchair.zer0.voice.z0live;

import java.io.UnsupportedEncodingException;
import java.net.URI;
import java.net.URISyntaxException;
import java.net.URLDecoder;
import java.util.HashMap;
import java.util.Map;
import okhttp3.HttpUrl;

/** Validates owner-supplied endpoints and pairing credentials before they are stored or used. */
public final class PairingValidation {
    private static final int MAX_URL_LENGTH = 8192;
    private static final int MAX_LINK_LENGTH = 32768;
    private static final int MAX_TOKEN_LENGTH = 4096;

    private PairingValidation() {}

    public static final class Pairing {
        public final String backendUrl;
        public final String token;

        public Pairing(String backendUrl, String token) {
            this.backendUrl = validateBackend(backendUrl);
            this.token = validateToken(token);
        }
    }

    public static Pairing parse(String value) {
        URI uri = parseUri(value, MAX_LINK_LENGTH);
        if (!"z0live".equals(uri.getScheme()) || !"pair".equals(uri.getRawAuthority())
                || !uri.getRawPath().isEmpty() || uri.getRawFragment() != null
                || uri.getRawQuery() == null) {
            throw new IllegalArgumentException("Invalid z0live pairing link");
        }
        Map<String, String> parameters = new HashMap<>();
        for (String entry : uri.getRawQuery().split("&", -1)) {
            int separator = entry.indexOf('=');
            if (separator <= 0) throw new IllegalArgumentException("Invalid pairing parameters");
            String name = entry.substring(0, separator);
            if (!("url".equals(name) || "token".equals(name)) || parameters.containsKey(name)) {
                throw new IllegalArgumentException("Invalid pairing parameters");
            }
            try {
                parameters.put(name, URLDecoder.decode(entry.substring(separator + 1), "UTF-8"));
            } catch (IllegalArgumentException | UnsupportedEncodingException ignored) {
                throw new IllegalArgumentException("Invalid pairing encoding");
            }
        }
        if (parameters.size() != 2) throw new IllegalArgumentException("Missing pairing parameters");
        return new Pairing(parameters.get("url"), parameters.get("token"));
    }

    public static String validateBackend(String value) {
        URI uri = validateHttps(value);
        String path = uri.getRawPath();
        if (!(path.isEmpty() || "/".equals(path))
                || uri.getRawQuery() != null || uri.getRawFragment() != null) {
            throw new IllegalArgumentException("Backend must be an HTTPS origin");
        }
        String normalized = HttpUrl.get(value).toString();
        return normalized.substring(0, normalized.length() - 1);
    }

    public static String validatePanel(String value) {
        validateHttps(value);
        return HttpUrl.get(value).toString();
    }

    static String validateToken(String token) {
        if (token == null || token.isEmpty() || token.length() > MAX_TOKEN_LENGTH) {
            throw new IllegalArgumentException("Invalid pairing token");
        }
        // HTTP bearer credentials must fit an unambiguous printable ASCII header value.
        for (int i = 0; i < token.length(); i++) {
            char character = token.charAt(i);
            if (character <= 0x20 || character >= 0x7f) {
                throw new IllegalArgumentException("Invalid pairing token");
            }
        }
        return token;
    }

    private static URI validateHttps(String value) {
        URI uri = parseUri(value, MAX_URL_LENGTH);
        if (!"https".equalsIgnoreCase(uri.getScheme()) || uri.getHost() == null
                || uri.getRawUserInfo() != null || uri.getPort() == 0 || uri.getPort() > 65535) {
            throw new IllegalArgumentException("Endpoint must use HTTPS without credentials");
        }
        try {
            HttpUrl.get(value);
        } catch (IllegalArgumentException ignored) {
            throw new IllegalArgumentException("Invalid HTTPS endpoint");
        }
        return uri;
    }

    private static URI parseUri(String value, int maximumLength) {
        if (value == null || value.isEmpty() || value.length() > maximumLength) {
            throw new IllegalArgumentException("Invalid endpoint or pairing link");
        }
        for (int i = 0; i < value.length(); i++) {
            char character = value.charAt(i);
            if (Character.isWhitespace(character) || Character.isISOControl(character)
                    || Character.getType(character) == Character.FORMAT) {
                throw new IllegalArgumentException("Invalid endpoint or pairing link");
            }
        }
        try {
            return new URI(value);
        } catch (URISyntaxException ignored) {
            throw new IllegalArgumentException("Invalid endpoint or pairing link");
        }
    }
}
