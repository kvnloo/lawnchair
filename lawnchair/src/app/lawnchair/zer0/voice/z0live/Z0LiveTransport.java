package app.lawnchair.zer0.voice.z0live;

import java.util.Locale;

/**
 * Owner-chosen voice transport. Pure: no Android.
 *
 * <ul>
 *   <li>{@link #AUTO} (default): Live (Frameless Bidi) when the broker offers it, otherwise
 *       Realtime; a failed Live call setup falls back to Realtime with the same offer.</li>
 *   <li>{@link #REALTIME}: always gpt-realtime.</li>
 *   <li>{@link #BIDI}: Live when the broker offers it; a failed Live call setup is reported, not
 *       silently replaced, so the owner knows which voice he is testing.</li>
 * </ul>
 */
public enum Z0LiveTransport {
    AUTO("auto"), REALTIME("realtime"), BIDI("bidi");

    public final String key;

    Z0LiveTransport(String key) { this.key = key; }

    /** Unknown or missing values mean the default, {@link #AUTO}. */
    public static Z0LiveTransport parse(String value) {
        if (value != null) {
            String normalized = value.trim().toLowerCase(Locale.ROOT);
            for (Z0LiveTransport transport : values()) if (transport.key.equals(normalized)) return transport;
        }
        return AUTO;
    }

    /** Whether the session mint advertises Frameless Bidi to the broker. */
    public boolean offerBidi() { return this != REALTIME; }

    /** Whether a failed Live call setup may silently continue on gpt-realtime. */
    public boolean fallbackToRealtime() { return this == AUTO; }
}
