package app.lawnchair.zer0.voice;

import android.app.Activity;
import android.content.ActivityNotFoundException;
import android.content.Intent;
import android.net.Uri;
import android.widget.Toast;
import com.android.launcher3.R;

/**
 * Explicit handoff to z0live's localhost browser UI running inside Tern on this device.
 *
 * <p>Never grants network or microphone permission to Lawnchair and never sends an
 * Android intent to a remote machine. Tern owns z0live and OMP/Hermes; Wi-Fi ADB
 * pairing and phone-harness approvals remain completely independent.
 */
public final class LocalVoiceHandoff {
    private static final Uri SETUP =
            Uri.parse("http://127.0.0.1:8780/voice-setup.html");
    private static final Uri SESSION =
            Uri.parse("http://127.0.0.1:8780/");

    private LocalVoiceHandoff() {}

    public static void openSetup(Activity activity) {
        open(activity, SETUP);
    }

    public static void openSession(Activity activity) {
        open(activity, SESSION);
    }

    private static void open(Activity activity, Uri target) {
        // ACTION_VIEW is a *user tap*, not a remote-control action or voice API.
        Intent intent = new Intent(Intent.ACTION_VIEW, target);
        intent.addCategory(Intent.CATEGORY_BROWSABLE);
        try {
            activity.startActivity(intent);
        } catch (ActivityNotFoundException | SecurityException error) {
            Toast.makeText(activity, R.string.zer0_assistant_tern_unavailable,
                    Toast.LENGTH_LONG).show();
        }
    }
}
