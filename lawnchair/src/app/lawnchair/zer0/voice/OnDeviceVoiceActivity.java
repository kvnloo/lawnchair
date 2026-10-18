package app.lawnchair.zer0.voice;

import android.Manifest;
import android.app.Activity;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.os.Build;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.speech.RecognitionListener;
import android.speech.RecognizerIntent;
import android.speech.SpeechRecognizer;
import android.widget.Button;
import android.widget.LinearLayout;
import android.widget.TextView;
import com.android.launcher3.R;
import java.util.ArrayList;

/**
 * Foreground-only Android on-device recognition of one navigation command.
 *
 * <p>No always-on microphone, audio persistence, cloud STT fallback, model session
 * or accessibility control. The user presses Start and Android grants microphone access.
 */
public final class OnDeviceVoiceActivity extends Activity implements RecognitionListener {
    private static final int MIC_PERMISSION_REQUEST = 52;
    private static final long MAX_LISTEN_MS = 30_000;
    private final Handler handler = new Handler(Looper.getMainLooper());
    private final OnDeviceVoiceSession session = new OnDeviceVoiceSession();
    private SpeechRecognizer recognizer;
    private Runnable timeout;
    private TextView status;
    private Button startButton;

    @Override
    protected void onCreate(Bundle savedState) {
        super.onCreate(savedState);
        // A configuration change never resumes or replays a microphone command.
        if (savedState != null) {
            finish();
            return;
        }
        LinearLayout layout = new LinearLayout(this);
        layout.setOrientation(LinearLayout.VERTICAL);
        int dp = (int) (20 * getResources().getDisplayMetrics().density);
        layout.setPadding(dp, dp, dp, dp);

        TextView title = new TextView(this);
        title.setText(R.string.zer0_assistant_on_device_title);
        title.setTextSize(24);
        layout.addView(title);

        status = new TextView(this);
        status.setText(R.string.zer0_assistant_on_device_info);
        status.setTextSize(15);
        layout.addView(status);

        startButton = new Button(this);
        startButton.setText(R.string.zer0_assistant_on_device_start);
        startButton.setOnClickListener(view -> requestMicrophoneAndListen());
        layout.addView(startButton);

        Button cancel = new Button(this);
        cancel.setText(R.string.zer0_assistant_on_device_cancel);
        cancel.setOnClickListener(view -> {
            session.cancel();
            releaseRecognizer();
            finish();
        });
        layout.addView(cancel);
        setContentView(layout);

        if (!isAvailable()) {
            startButton.setEnabled(false);
            status.setText(R.string.zer0_assistant_on_device_unavailable);
        }
    }

    private boolean isAvailable() {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.S) return false;
        try {
            return SpeechRecognizer.isOnDeviceRecognitionAvailable(this);
        } catch (RuntimeException error) {
            return false;
        }
    }

    private void requestMicrophoneAndListen() {
        if (session.isTerminal() || !isAvailable()) {
            status.setText(R.string.zer0_assistant_on_device_unavailable);
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
        if (grants.length == 1 && grants[0] == PackageManager.PERMISSION_GRANTED) {
            listenOnce();
        } else {
            status.setText(R.string.zer0_assistant_on_device_permission_denied);
        }
    }

    private void listenOnce() {
        if (!isAvailable() || !session.begin()) return;
        startButton.setEnabled(false);
        status.setText(R.string.zer0_assistant_on_device_listening);
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
                status.setText(R.string.zer0_assistant_on_device_timeout);
            };
            handler.postDelayed(timeout, MAX_LISTEN_MS);
        } catch (RuntimeException error) {
            if (session.complete()) status.setText(R.string.zer0_assistant_on_device_failed);
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
            status.setText(R.string.zer0_assistant_on_device_listening);
        }
    }

    @Override public void onBeginningOfSpeech() {}
    @Override public void onRmsChanged(float rms) {}
    @Override public void onBufferReceived(byte[] buffer) {}

    @Override
    public void onEndOfSpeech() {
        if (!session.isTerminal()) {
            status.setText(R.string.zer0_assistant_on_device_processing);
        }
    }

    @Override
    public void onError(int error) {
        if (!session.complete()) return;
        releaseRecognizer();
        status.setText(R.string.zer0_assistant_on_device_failed);
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
            status.setText(R.string.zer0_assistant_on_device_no_match);
            return;
        }
        // Both activities are non-exported; no transcript is persisted or uploaded.
        startActivity(new Intent(this, VoiceCommandActivity.class)
                .putExtra(VoiceCommandActivity.EXTRA_FINAL_TRANSCRIPT, transcript));
        finish();
    }

    @Override public void onPartialResults(Bundle partialResults) {}
    @Override public void onEvent(int eventType, Bundle params) {}

    @Override
    protected void onStop() {
        // Do not cancel an unstarted permission request, but never record in background.
        if (recognizer != null) {
            session.cancel();
            releaseRecognizer();
        }
        super.onStop();
    }

    @Override
    protected void onDestroy() {
        session.cancel();
        releaseRecognizer();
        super.onDestroy();
    }
}
