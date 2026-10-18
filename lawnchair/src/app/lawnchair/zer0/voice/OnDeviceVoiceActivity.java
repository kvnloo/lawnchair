package app.lawnchair.zer0.voice;

import android.Manifest;
import android.content.Context;
import androidx.activity.ComponentActivity;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.os.Build;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.speech.RecognitionListener;
import android.speech.RecognizerIntent;
import android.speech.SpeechRecognizer;
import com.android.launcher3.R;
import java.util.ArrayList;

/**
 * Foreground-only Android on-device recognition of one navigation command.
 *
 * <p>No always-on microphone, audio persistence, cloud STT fallback, model session
 * or accessibility control. The user presses Start and Android grants microphone access.
 */
public final class OnDeviceVoiceActivity extends ComponentActivity implements RecognitionListener {
    public static final String EXTRA_START_NOW = "app.lawnchair.zer0.voice.START_ON_DEVICE";

    private static final int MIC_PERMISSION_REQUEST = 52;
    private static final long MAX_LISTEN_MS = 30_000;
    private final Handler handler = new Handler(Looper.getMainLooper());
    private final OnDeviceVoiceSession session = new OnDeviceVoiceSession();
    private SpeechRecognizer recognizer;
    private Runnable timeout;
    private OnDeviceVoiceUi.UiState ui;
    private boolean foregroundStarted;
    private boolean autoStart;

    @Override
    protected void onCreate(Bundle savedState) {
        super.onCreate(savedState);
        // A configuration change never resumes or replays a microphone command.
        if (savedState != null) {
            finish();
            return;
        }
        ui = OnDeviceVoiceUi.show(this);
        autoStart = getIntent().getBooleanExtra(EXTRA_START_NOW, false);
        if (!isAvailable()) {
            ui.setStartEnabled(false);
            ui.setStatusResource(R.string.zer0_assistant_on_device_unavailable);
            autoStart = false;
        }
    }

    @Override
    protected void onStart() {
        super.onStart();
        foregroundStarted = true;
        // The launcher mic click is already a user action. Do not ask for a second
        // Start click, and never auto-resume after leaving the foreground.
        if (autoStart) {
            autoStart = false;
            requestMicrophoneAndListen();
        }
    }

    public static boolean isAvailable(Context context) {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.S) return false;
        try {
            return SpeechRecognizer.isOnDeviceRecognitionAvailable(context);
        } catch (RuntimeException error) {
            return false;
        }
    }

    public void startFromUserTap() {
        requestMicrophoneAndListen();
    }

    public void cancelAndClose() {
        session.cancel();
        releaseRecognizer();
        finish();
    }

    private void requestMicrophoneAndListen() {
        if (!foregroundStarted || session.isTerminal() || !isAvailable()) {
            ui.setStatusResource(R.string.zer0_assistant_on_device_unavailable);
            return;
        }
        if (checkSelfPermission(Manifest.permission.RECORD_AUDIO)
                != PackageManager.PERMISSION_GRANTED) {
            requestPermissions(new String[] {Manifest.permission.RECORD_AUDIO},
                    MIC_PERMISSION_REQUEST);
            return;
        }
        listenOnce();
    }

    @Override
    public void onRequestPermissionsResult(int code, String[] permissions, int[] grants) {
        super.onRequestPermissionsResult(code, permissions, grants);
        if (code != MIC_PERMISSION_REQUEST || session.isTerminal()) return;
        if (!foregroundStarted) {
            session.cancel(); // A late permission grant cannot activate a background mic.
            return;
        }
        if (grants.length == 1 && grants[0] == PackageManager.PERMISSION_GRANTED) {
            listenOnce();
        } else {
            ui.setStatusResource(R.string.zer0_assistant_on_device_permission_denied);
        }
    }

    private void listenOnce() {
        if (!foregroundStarted || !isAvailable() || !session.begin()) return;
        ui.setStartEnabled(false);
        ui.setStatusResource(R.string.zer0_assistant_on_device_listening);
        try {
            recognizer = SpeechRecognizer.createOnDeviceSpeechRecognizer(this);
            recognizer.setRecognitionListener(this);
            Intent intent = new Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH)
                    .putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL,
                            RecognizerIntent.LANGUAGE_MODEL_FREE_FORM)
                    .putExtra(RecognizerIntent.EXTRA_MAX_RESULTS, 1)
                    .putExtra(RecognizerIntent.EXTRA_PREFER_OFFLINE, true);
            recognizer.startListening(intent);
            timeout = () -> {
                if (!session.complete()) return;
                releaseRecognizer();
                ui.setStatusResource(R.string.zer0_assistant_on_device_timeout);
            };
            handler.postDelayed(timeout, MAX_LISTEN_MS);
        } catch (RuntimeException error) {
            if (session.complete()) ui.setStatusResource(R.string.zer0_assistant_on_device_failed);
            releaseRecognizer();
        }
    }

    private void releaseRecognizer() {
        if (timeout != null) handler.removeCallbacks(timeout);
        timeout = null;
        if (recognizer != null) {
            SpeechRecognizer old = recognizer;
            recognizer = null;
            old.cancel();
            old.destroy();
        }
    }

    @Override
    public void onReadyForSpeech(Bundle params) {
        if (!session.isTerminal()) {
            ui.setStatusResource(R.string.zer0_assistant_on_device_listening);
        }
    }

    @Override public void onBeginningOfSpeech() {}
    @Override public void onRmsChanged(float rms) {}
    @Override public void onBufferReceived(byte[] buffer) {}

    @Override
    public void onEndOfSpeech() {
        if (!session.isTerminal()) {
            ui.setStatusResource(R.string.zer0_assistant_on_device_processing);
        }
    }

    @Override
    public void onError(int error) {
        if (!session.complete()) return;
        releaseRecognizer();
        ui.setStatusResource(R.string.zer0_assistant_on_device_failed);
    }

    @Override
    public void onResults(Bundle bundle) {
        if (!session.complete()) return;
        ArrayList<String> transcripts = bundle == null ? null
                : bundle.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION);
        String transcript = transcripts == null || transcripts.isEmpty()
                ? null : transcripts.get(0);
        releaseRecognizer();
        if (transcript == null || transcript.isEmpty()) {
            ui.setStatusResource(R.string.zer0_assistant_on_device_no_match);
            return;
        }
        // Both activities are non-exported; no transcript is persisted or uploaded.
        startActivity(new Intent(this, VoiceCommandActivity.class)
                .putExtra(VoiceCommandActivity.EXTRA_FINAL_TRANSCRIPT, transcript)
                .putExtra(VoiceCommandActivity.EXTRA_AUTO_ROUTE,
                        getIntent().getBooleanExtra(VoiceCommandActivity.EXTRA_AUTO_ROUTE, false)));
        finish();
    }

    @Override public void onPartialResults(Bundle partialResults) {}
    @Override public void onEvent(int eventType, Bundle params) {}

    @Override
    protected void onStop() {
        foregroundStarted = false;
        // Cancel pending permission results too; returning to this Activity does not
        // restart an interrupted microphone session or consume a stale transcript.
        session.cancel();
        releaseRecognizer();
        super.onStop();
    }

    @Override
    protected void onDestroy() {
        session.cancel();
        releaseRecognizer();
        super.onDestroy();
    }
}
