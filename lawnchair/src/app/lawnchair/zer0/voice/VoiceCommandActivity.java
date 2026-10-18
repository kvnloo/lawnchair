package app.lawnchair.zer0.voice;

import android.app.Activity;
import android.content.ActivityNotFoundException;
import android.content.Intent;
import android.content.SharedPreferences;
import android.content.pm.LauncherActivityInfo;
import android.content.pm.LauncherApps;
import android.os.Bundle;
import android.os.Process;
import android.os.SystemClock;
import android.speech.RecognizerIntent;
import android.widget.Toast;
import app.lawnchair.zer0.spaces.HyprSpacesActivity;
import app.lawnchair.zer0.spaces.SpaceModel;
import com.android.launcher3.R;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

/**
 * One-shot, user-invoked voice command entry point for the downstream zer0 fork.
 *
 * <p>The recognizer app owns the microphone. It may perform network transcription.
 * The only executable intent in this initial slice is opening one unambiguous,
 * launchable activity in the current personal profile. No accessibility actions.
 */
public final class VoiceCommandActivity extends Activity {
    public static final String EXTRA_PREFER_OFFLINE_HINT =
            "app.lawnchair.zer0.voice.PREFER_OFFLINE_HINT";
    public static final String EXTRA_FINAL_TRANSCRIPT =
            "app.lawnchair.zer0.voice.FINAL_ON_DEVICE_TRANSCRIPT";
    private static final int SPEECH_REQUEST = 41;
    private static final String STATE_UTTERANCE = "zer0_utterance_id";
    private static final String STATE_HANDLED = "zer0_handled";
    private static final PhoneActionGate ACTION_GATE = new PhoneActionGate();
    private String utteranceId;
    private boolean handledFinal;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        if (savedInstanceState != null) {
            utteranceId = savedInstanceState.getString(STATE_UTTERANCE);
            handledFinal = savedInstanceState.getBoolean(STATE_HANDLED, false);
            if (utteranceId == null) finish(); // Never mint a fresh ID for an old result.
            return;
        }
        utteranceId = UUID.randomUUID().toString();
        // A finalized transcript can only be sent by an in-package recognizer.
        // Both receiver and sender are non-exported, no external intent-filter.
        String finalized = getIntent().getStringExtra(EXTRA_FINAL_TRANSCRIPT);
        if (finalized != null) {
            handledFinal = true;
            try {
                handleFinalTranscript(finalized);
            } finally {
                finish();
            }
            return;
        }

        Intent speech = new Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH)
                .putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL,
                        RecognizerIntent.LANGUAGE_MODEL_FREE_FORM)
                .putExtra(RecognizerIntent.EXTRA_PROMPT, getString(R.string.zer0_voice_prompt))
                .putExtra(RecognizerIntent.EXTRA_MAX_RESULTS, 1);
        // Only a preference hint; recognizer apps are allowed to ignore this request.
        if (getIntent().getBooleanExtra(EXTRA_PREFER_OFFLINE_HINT, false)) {
            speech.putExtra(RecognizerIntent.EXTRA_PREFER_OFFLINE, true);
        }
        try {
            startActivityForResult(speech, SPEECH_REQUEST);
        } catch (ActivityNotFoundException | SecurityException error) {
            notifyUser(R.string.zer0_voice_unavailable);
            finish();
        }
    }

    @Override
    protected void onSaveInstanceState(Bundle outState) {
        outState.putString(STATE_UTTERANCE, utteranceId);
        outState.putBoolean(STATE_HANDLED, handledFinal);
        super.onSaveInstanceState(outState);
    }

    @Override
    @SuppressWarnings("deprecation")
    protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        super.onActivityResult(requestCode, resultCode, data);
        if (requestCode != SPEECH_REQUEST || handledFinal || utteranceId == null) return;
        // Consume only the first finalized speech result (including cancellation).
        handledFinal = true;
        try {
            if (resultCode == RESULT_OK && data != null) {
                ArrayList<String> transcripts =
                        data.getStringArrayListExtra(RecognizerIntent.EXTRA_RESULTS);
                if (transcripts != null && !transcripts.isEmpty()) {
                    handleFinalTranscript(transcripts.get(0));
                }
            }
        } finally {
            finish();
        }
    }

    private void handleFinalTranscript(String spoken) {
        VoiceCommand command = VoiceCommand.parse(spoken);
        if (command.kind != VoiceCommand.Kind.OPEN_APP
                && command.kind != VoiceCommand.Kind.SWITCH_SPACE) {
            notifyUser(R.string.zer0_voice_unsupported);
            return;
        }
        // This guards session replay and typed action authority. The 0 revision is
        // only this one-shot Activity's local context, NOT a measured Android
        // foreground/window revision. A future remote executor needs real UI state.
        PhoneActionGate.Kind kind = command.kind == VoiceCommand.Kind.OPEN_APP
                ? PhoneActionGate.Kind.OPEN_APP : PhoneActionGate.Kind.SWITCH_SPACE;
        long now = SystemClock.elapsedRealtime();
        PhoneActionGate.Request action = new PhoneActionGate.Request(
                utteranceId + ":" + kind.name(), utteranceId, kind,
                PhoneActionGate.Authority.ONE_SHOT_VOICE, command.target,
                0, now + 15_000);
        if (ACTION_GATE.admit(action, now, 0) != PhoneActionGate.Decision.ADMITTED) {
            notifyUser(R.string.zer0_voice_unsupported);
            return;
        }

        if (command.kind == VoiceCommand.Kind.SWITCH_SPACE) {
            // Final transcript supplies a *candidate*. Only locally configured exact
            // names can be activated; no model-chosen arbitrary Android actions.
            SharedPreferences prefs =
                    getSharedPreferences(HyprSpacesActivity.PREFS_NAME, MODE_PRIVATE);
            SpaceModel catalog = SpaceModel.restore(
                    prefs.getString(HyprSpacesActivity.KEY_NAMES, null),
                    prefs.getString(HyprSpacesActivity.KEY_ACTIVE, null));
            if (!catalog.contains(command.target)) {
                notifyUser(R.string.zer0_space_unknown);
                return;
            }
            startActivity(new Intent(this, HyprSpacesActivity.class)
                    .putExtra(HyprSpacesActivity.EXTRA_ACTIVATE, command.target));
            return;
        }
        if (command.kind != VoiceCommand.Kind.OPEN_APP) {
            notifyUser(R.string.zer0_voice_unsupported);
            return;
        }

        LauncherApps launcherApps = getSystemService(LauncherApps.class);
        if (launcherApps == null) {
            notifyUser(R.string.zer0_voice_unavailable);
            return;
        }

        // Current personal profile only. Work/Private Space require explicit profile selection.
        final List<LauncherActivityInfo> activities;
        try {
            activities = launcherApps.getActivityList(null, Process.myUserHandle());
        } catch (RuntimeException error) {
            notifyUser(R.string.zer0_voice_unavailable);
            return;
        }

        List<String> labels = new ArrayList<>(activities.size());
        for (LauncherActivityInfo activity : activities) {
            CharSequence label = activity.getLabel();
            labels.add(label == null ? "" : label.toString());
        }

        int index = AppNameMatcher.uniqueIndex(command.target, labels);
        if (index == AppNameMatcher.AMBIGUOUS) {
            notifyUser(R.string.zer0_voice_ambiguous);
        } else if (index == AppNameMatcher.NOT_FOUND) {
            notifyUser(R.string.zer0_voice_no_match);
        } else {
            LauncherActivityInfo activity = activities.get(index);
            // Voice input must not bypass Lawnchair's hidden-app visibility setting.
            if (!VoiceAppVisibility.isAllowed(this, activity, command.target)) {
                notifyUser(R.string.zer0_voice_no_match);
                return;
            }
            try {
                launcherApps.startMainActivity(
                        activity.getComponentName(), Process.myUserHandle(), null, null);
            } catch (RuntimeException error) {
                notifyUser(R.string.zer0_voice_launch_failed);
            }
        }
    }

    private void notifyUser(int message) {
        Toast.makeText(this, message, Toast.LENGTH_SHORT).show();
    }
}
