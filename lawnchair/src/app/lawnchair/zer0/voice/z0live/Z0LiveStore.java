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

    public Z0LiveStore(Context context) {
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
    }

    public long inboxCursor() { return Math.max(0, preferences.getLong("inbox_cursor", 0)); }

    public void setInboxCursor(long cursor) {
        commit(preferences.edit().putLong("inbox_cursor", cursor));
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

    public void clearPairing() {
        commit(preferences.edit().remove("backend").remove("token").remove("inbox_cursor"));
    }

    private void commit(SharedPreferences.Editor editor) {
        if (!editor.commit()) throw new IllegalStateException("Encrypted settings could not be saved.");
    }
}
