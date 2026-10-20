package app.lawnchair.zer0.voice.z0live;

import android.content.Context;
import android.content.SharedPreferences;
import androidx.security.crypto.EncryptedSharedPreferences;
import androidx.security.crypto.MasterKey;
import java.io.IOException;
import java.security.GeneralSecurityException;

/** Encrypted, application-private pairing credentials and owner panel configuration. */
public final class Z0LiveStore {
    public static final String PREFERENCES_NAME = "z0live_secrets";
    private final SharedPreferences preferences;
    private final Context appContext;
    private static final Object INBOX_LOCK = new Object();

    public Z0LiveStore(Context context) {
        appContext = context.getApplicationContext();
        try {
            Context app = context.getApplicationContext();
            MasterKey key = new MasterKey.Builder(app)
                    .setKeyScheme(MasterKey.KeyScheme.AES256_GCM).build();
            preferences = EncryptedSharedPreferences.create(app, PREFERENCES_NAME, key,
                    EncryptedSharedPreferences.PrefKeyEncryptionScheme.AES256_SIV,
                    EncryptedSharedPreferences.PrefValueEncryptionScheme.AES256_GCM);
        } catch (GeneralSecurityException | IOException | RuntimeException error) {
            throw new IllegalStateException("Encrypted pairing storage is unavailable.", error);
        }
    }

    public PairingValidation.Pairing pairing() {
        try {
            String backend = preferences.getString("backend", null);
            String token = preferences.getString("token", null);
            if (backend == null && token == null) return null;
            if (backend == null || token == null || token.isEmpty()) {
                throw new IllegalStateException("Saved pairing is incomplete. Pair again.");
            }
            return new PairingValidation.Pairing(PairingValidation.validateBackend(backend), token);
        } catch (RuntimeException error) {
            throw new IllegalStateException("Saved pairing could not be read. Pair again.", error);
        }
    }

    public void pair(PairingValidation.Pairing pairing) {
        String backend = PairingValidation.validateBackend(pairing.backendUrl);
        if (pairing.token == null || pairing.token.isEmpty()) {
            throw new IllegalArgumentException("A pairing token is required.");
        }
        commit(preferences.edit().putString("backend", backend).putString("token", pairing.token).remove("inbox_cursor"));
        Z0LiveInboxNotifications.schedule(appContext);
    }

    private String inboxCursorKey(PairingValidation.Pairing pairing) {
        if (pairing == null) throw new IllegalStateException("Pairing required for inbox cursor");
        return Z0LiveInboxDelivery.cursorKey(pairing.backendUrl, pairing.token);
    }
    public long inboxCursor() { return inboxCursor(pairing()); }
    public long inboxCursor(PairingValidation.Pairing pairing) {
        synchronized (INBOX_LOCK) { return Math.max(-1, preferences.getLong(inboxCursorKey(pairing), -1)); }
    }
    public void setInboxCursor(long cursor) { setInboxCursor(pairing(), cursor); }
    public void setInboxCursor(PairingValidation.Pairing pairing, long cursor) {
        if (cursor < 0) throw new IllegalArgumentException("Invalid inbox cursor");
        synchronized (INBOX_LOCK) {
            String key = inboxCursorKey(pairing);
            commit(preferences.edit().putLong(key, Math.max(cursor, preferences.getLong(key, -1))));
        }
    }

    public String rippleUrl() {
        String value = preferences.getString("ripple", null);
        return value == null ? null : PairingValidation.validatePanel(value);
    }

    public void setRippleUrl(String value) {
        if (value == null || value.trim().isEmpty()) {
            commit(preferences.edit().remove("ripple"));
        } else {
            commit(preferences.edit().putString("ripple", PairingValidation.validatePanel(value.trim())));
        }
    }

    /** Voice transport chosen in z0live settings (default Auto). */
    public Z0LiveTransport transport() { return Z0LiveTransport.parse(preferences.getString("transport", null)); }

    public void setTransport(Z0LiveTransport transport) {
        commit(preferences.edit().putString("transport", transport.key));
    }

    public void clearPairing() {
        commit(preferences.edit().remove("backend").remove("token").remove("inbox_cursor"));
        Z0LiveInboxNotifications.cancel(appContext);
    }

    private void commit(SharedPreferences.Editor editor) {
        if (!editor.commit()) throw new IllegalStateException("Encrypted settings could not be saved.");
    }
}
