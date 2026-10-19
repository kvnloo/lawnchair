package app.lawnchair.zer0.voice.z0live;

/**
 * Pure policy for a future consented Oura/NOOP -> z0live gesture channel.
 *
 * IMPORTANT: authenticatedSource is a fact established by a transport
 * authenticator inside Lawnchair, NOT a value accepted from an Intent extra.
 * No receiver, service, URI handler or microphone start is exposed here.
 * This class is inert until a vetted transport connects it to an existing
 * manually-started z0live session.
 */
public final class OuraGestureAdmission {
    private static final long MAX_AGE_MS = 1_500;
    private static final long MIN_SPACING_MS = 900;

    public enum Decision {
        REJECT,
        TOGGLE_EXISTING_SESSION_MUTE
    }

    private OuraGestureAdmission() {}

    public static Decision evaluate(
            boolean userArmed,
            boolean authenticatedSource,
            boolean existingVoiceSession,
            String gesture,
            long gestureElapsedMs,
            long nowElapsedMs,
            long incomingSequence,
            long lastAcceptedSequence,
            long lastAcceptedAtMs) {
        if (!userArmed || !authenticatedSource || !existingVoiceSession) {
            return Decision.REJECT;
        }
        if (!"DOUBLE_TAP".equals(gesture)) {
            return Decision.REJECT;
        }
        if (gestureElapsedMs < 0 || nowElapsedMs < gestureElapsedMs) {
            return Decision.REJECT;
        }
        if (nowElapsedMs - gestureElapsedMs > MAX_AGE_MS) {
            return Decision.REJECT;
        }
        if (incomingSequence <= 0 || incomingSequence <= lastAcceptedSequence) {
            return Decision.REJECT;
        }
        if (lastAcceptedAtMs >= 0 && (nowElapsedMs < lastAcceptedAtMs
                || nowElapsedMs - lastAcceptedAtMs < MIN_SPACING_MS)) {
            return Decision.REJECT;
        }
        return Decision.TOGGLE_EXISTING_SESSION_MUTE;
    }
}
