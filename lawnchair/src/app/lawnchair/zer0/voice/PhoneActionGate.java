package app.lawnchair.zer0.voice;

import java.util.LinkedHashSet;
import java.util.Set;

/**
 * Admission boundary between a proposed phone action and any Android executor.
 *
 * <p>It never executes actions, inspects Android screens or asserts that a UI change happened.
 * Idempotency is bounded to one live process and the most recent 256 action/utterance IDs;
 * durable cross-restart receipts are a separate, required gate before remote automation.
 */
public final class PhoneActionGate {
    public enum Kind {
        OPEN_APP,
        SWITCH_SPACE,
        SEARCH_APPS,
        REQUEST_SPLIT
    }

    public enum Authority {
        USER_TAP,
        ONE_SHOT_VOICE,
        REMOTE_MODEL
    }

    public enum Decision {
        ADMITTED,
        INVALID,
        DUPLICATE,
        EXPIRED,
        STALE_UI,
        REQUIRES_CONFIRMATION,
        NOT_ALLOWED
    }

    public static final long MAX_HORIZON_MS = 30_000;
    private static final int MAX_SEEN = 256;

    public static final class Request {
        public final String actionId;
        public final String utteranceId;
        public final Kind kind;
        public final Authority authority;
        public final String target;
        public final long expectedUiRevision;
        public final long expiresAtElapsedMs;

        public Request(
                String actionId,
                String utteranceId,
                Kind kind,
                Authority authority,
                String target,
                long expectedUiRevision,
                long expiresAtElapsedMs) {
            this.actionId = actionId;
            this.utteranceId = utteranceId;
            this.kind = kind;
            this.authority = authority;
            this.target = target;
            this.expectedUiRevision = expectedUiRevision;
            this.expiresAtElapsedMs = expiresAtElapsedMs;
        }
    }

    private final Set<String> seenActions = new LinkedHashSet<>();
    private final Set<String> seenUtterances = new LinkedHashSet<>();

    /**
     * One action per finalized utterance. Request and current times must use the same
     * monotonic clock (SystemClock.elapsedRealtime on Android), not wall-clock time.
     *
     * <p>The caller must independently resolve target/profile, check visibility/permissions
     * and verify the observed UI result before calling anything "verified".
     */
    public synchronized Decision admit(Request request, long nowElapsedMs, long currentUiRevision) {
        if (request == null || !safeId(request.actionId) || !safeId(request.utteranceId)
                || request.kind == null || request.authority == null
                || !safeTarget(request.target) || request.expectedUiRevision < 0
                || nowElapsedMs < 0 || currentUiRevision < 0) {
            return Decision.INVALID;
        }

        if (seenActions.contains(request.actionId)
                || seenUtterances.contains(request.utteranceId)) {
            return Decision.DUPLICATE;
        }
        // Consume a valid action ID even if it is stale, denied or expired: a later
        // attempt must be explicit with a *new* utterance and fresh authority.
        remember(seenActions, request.actionId);
        remember(seenUtterances, request.utteranceId);

        if (request.expiresAtElapsedMs <= nowElapsedMs
                || request.expiresAtElapsedMs - nowElapsedMs > MAX_HORIZON_MS) {
            return Decision.EXPIRED;
        }
        if (request.expectedUiRevision != currentUiRevision) {
            return Decision.STALE_UI;
        }
        if (request.authority == Authority.REMOTE_MODEL) {
            return Decision.REQUIRES_CONFIRMATION;
        }
        if (request.kind == Kind.REQUEST_SPLIT
                && request.authority != Authority.USER_TAP) {
            return Decision.NOT_ALLOWED;
        }
        return Decision.ADMITTED;
    }

    private static void remember(Set<String> seen, String id) {
        if (seen.size() >= MAX_SEEN) {
            String oldest = seen.iterator().next();
            seen.remove(oldest);
        }
        seen.add(id);
    }

    private static boolean safeId(String id) {
        if (id == null || id.isEmpty() || id.length() > 80) return false;
        for (int i = 0; i < id.length(); i++) {
            char c = id.charAt(i);
            if (!((c >= 'A' && c <= 'Z') || (c >= 'a' && c <= 'z')
                    || (c >= '0' && c <= '9') || c == '-' || c == '_'
                    || c == '.' || c == ':')) return false;
        }
        return true;
    }

    private static boolean safeTarget(String target) {
        if (target == null || target.isEmpty() || target.length() > 160) return false;
        for (int i = 0; i < target.length();) {
            int cp = target.codePointAt(i);
            if (Character.isISOControl(cp)
                    || Character.getType(cp) == Character.FORMAT) return false;
            i += Character.charCount(cp);
        }
        return true;
    }
}
