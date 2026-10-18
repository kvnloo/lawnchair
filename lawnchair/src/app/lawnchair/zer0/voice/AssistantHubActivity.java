package app.lawnchair.zer0.voice;

import android.app.Activity;
import android.content.ActivityNotFoundException;
import android.content.Intent;
import android.content.pm.LauncherActivityInfo;
import android.content.pm.LauncherApps;
import android.os.Build;
import android.os.Bundle;
import android.os.Process;
import android.provider.Settings;
import android.speech.SpeechRecognizer;
import android.widget.Button;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;
import android.widget.Toast;
import com.android.launcher3.R;
import java.util.ArrayList;
import java.util.List;

/**
 * Explicit user-tap handoff to Android's selected assistant and installed ChatGPT.
 *
 * <p>It does not register as a voice-interaction service, sign in to ChatGPT, intercept
 * conversations, forward transcripts, grant permissions or execute third-party UI actions.
 */
public final class AssistantHubActivity extends Activity {
    private int dp(int value) {
        return (int) (value * getResources().getDisplayMetrics().density + 0.5f);
    }

    private TextView text(int resId, int sp) {
        TextView view = new TextView(this);
        view.setText(resId);
        view.setTextSize(sp);
        view.setPadding(dp(4), dp(9), dp(4), dp(9));
        return view;
    }

    private void button(LinearLayout list, int label, Runnable task) {
        Button button = new Button(this);
        button.setAllCaps(false);
        button.setText(label);
        button.setOnClickListener(view -> task.run());
        list.addView(button);
    }

    @Override
    protected void onCreate(Bundle state) {
        super.onCreate(state);
        ScrollView scroll = new ScrollView(this);
        LinearLayout content = new LinearLayout(this);
        content.setOrientation(LinearLayout.VERTICAL);
        content.setPadding(dp(18), dp(18), dp(18), dp(26));

        content.addView(text(R.string.zer0_assistant_hub_title, 25));
        content.addView(text(R.string.zer0_assistant_hub_note, 14));

        button(content, R.string.zer0_assistant_default, this::openDefaultAssistant);
        button(content, R.string.zer0_assistant_choose, this::openDefaultAssistantSettings);
        button(content, R.string.zer0_assistant_chatgpt, this::openChatGpt);

        content.addView(text(R.string.zer0_assistant_local_title, 20));
        button(content, R.string.zer0_assistant_nav, () -> openOneShot(false));
        button(content, R.string.zer0_assistant_offline_hint, () -> openOneShot(true));
        content.addView(text(R.string.zer0_assistant_offline_note, 13));

        boolean onDeviceAvailable = false;
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            try {
                onDeviceAvailable = SpeechRecognizer.isOnDeviceRecognitionAvailable(this);
            } catch (RuntimeException ignored) {
                // A capability query does not grant microphone access or start recognition.
            }
        }
        content.addView(text(
                onDeviceAvailable ? R.string.zer0_assistant_offline_service_available
                        : R.string.zer0_assistant_offline_service_unavailable,
                13));
        content.addView(text(R.string.zer0_assistant_limitations, 13));

        scroll.addView(content);
        setContentView(scroll);
    }

    private void openDefaultAssistant() {
        // Android routes ACTION_ASSIST; the user owns the configured assistant choice.
        // Some One UI implementations may not expose an activity for this intent.
        try {
            startActivity(new Intent(Intent.ACTION_ASSIST));
        } catch (ActivityNotFoundException | SecurityException error) {
            Toast.makeText(this, R.string.zer0_assistant_no_default, Toast.LENGTH_LONG).show();
        }
    }

    private void openDefaultAssistantSettings() {
        // Never change roles silently or pretend this launcher is the selected assistant.
        try {
            startActivity(new Intent(Settings.ACTION_MANAGE_DEFAULT_APPS_SETTINGS));
        } catch (ActivityNotFoundException | SecurityException error) {
            Toast.makeText(this, R.string.zer0_assistant_no_settings, Toast.LENGTH_LONG).show();
        }
    }

    private void openChatGpt() {
        LauncherApps launcherApps = getSystemService(LauncherApps.class);
        if (launcherApps == null) {
            Toast.makeText(this, R.string.zer0_assistant_chatgpt_unavailable,
                    Toast.LENGTH_LONG).show();
            return;
        }
        try {
            List<LauncherActivityInfo> installed =
                    launcherApps.getActivityList(null, Process.myUserHandle());
            if (installed == null) return;
            // Hidden-app policy and exact current personal profile must apply here too.
            List<LauncherActivityInfo> allowed = VoiceAppVisibility.visibleApps(this, installed);
            List<String> packages = new ArrayList<>(allowed.size());
            for (LauncherActivityInfo info : allowed) {
                packages.add(info.getComponentName().getPackageName());
            }
            int index = AssistantLaunchPolicy.uniqueChatGptIndex(packages);
            if (index < 0) {
                Toast.makeText(this, R.string.zer0_assistant_chatgpt_unavailable,
                        Toast.LENGTH_LONG).show();
                return;
            }
            LauncherActivityInfo chosen = allowed.get(index);
            // Exact component re-resolution before launch to avoid cached/stale authority.
            List<LauncherActivityInfo> now = VoiceAppVisibility.visibleApps(
                    this, launcherApps.getActivityList(null, Process.myUserHandle()));
            int count = 0;
            for (LauncherActivityInfo info : now) {
                if (chosen.getComponentName().equals(info.getComponentName())) count++;
            }
            if (count != 1) {
                Toast.makeText(this, R.string.zer0_assistant_chatgpt_unavailable,
                        Toast.LENGTH_LONG).show();
                return;
            }
            launcherApps.startMainActivity(
                    chosen.getComponentName(), Process.myUserHandle(), null, null);
        } catch (RuntimeException error) {
            Toast.makeText(this, R.string.zer0_assistant_chatgpt_unavailable,
                    Toast.LENGTH_LONG).show();
        }
    }

    private void openOneShot(boolean preferOffline) {
        startActivity(new Intent(this, VoiceCommandActivity.class)
                .putExtra(VoiceCommandActivity.EXTRA_PREFER_OFFLINE_HINT, preferOffline));
    }
}
