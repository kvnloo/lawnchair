package app.lawnchair.zer0.voice.z0live;

import android.content.ComponentName;
import android.content.Intent;
import android.content.pm.ResolveInfo;
import android.os.Bundle;
import android.os.RemoteException;
import android.speech.RecognitionListener;
import android.speech.RecognitionService;
import android.speech.SpeechRecognizer;

import java.util.List;

/**
 * Android only runs an assistant whose config names a speech recognizer, and the active assistant's recognizer
 * becomes the phone's default for every app that asks for speech input. z0live has no recognizer of its own, so
 * this one hands every request to the phone's real recognizer (Google's when present) and passes the results
 * back unchanged. Dictation in other apps keeps working while z0live is the assistant.
 */
public final class Z0LiveRecognitionService extends RecognitionService {
    private static final String[] PREFERRED = {
        "com.google.android.tts",
        "com.google.android.googlequicksearchbox",
        "com.samsung.android.bixby.agent",
    };

    private SpeechRecognizer inner;

    @Override
    protected void onStartListening(Intent recognizerIntent, Callback listener) {
        ComponentName target = delegate();
        if (target == null) {
            send(() -> listener.error(SpeechRecognizer.ERROR_CLIENT));
            return;
        }
        release();
        inner = SpeechRecognizer.createSpeechRecognizer(this, target);
        inner.setRecognitionListener(new RecognitionListener() {
            @Override public void onReadyForSpeech(Bundle params) { send(() -> listener.readyForSpeech(params)); }
            @Override public void onBeginningOfSpeech() { send(listener::beginningOfSpeech); }
            @Override public void onRmsChanged(float rmsdB) { send(() -> listener.rmsChanged(rmsdB)); }
            @Override public void onBufferReceived(byte[] buffer) { send(() -> listener.bufferReceived(buffer)); }
            @Override public void onEndOfSpeech() { send(listener::endOfSpeech); }
            @Override public void onError(int error) { send(() -> listener.error(error)); release(); }
            @Override public void onResults(Bundle results) { send(() -> listener.results(results)); release(); }
            @Override public void onPartialResults(Bundle partial) { send(() -> listener.partialResults(partial)); }
            @Override public void onEvent(int eventType, Bundle params) { }
        });
        inner.startListening(recognizerIntent);
    }

    @Override
    protected void onStopListening(Callback listener) {
        if (inner != null) inner.stopListening();
    }

    @Override
    protected void onCancel(Callback listener) {
        if (inner != null) inner.cancel();
        release();
    }

    @Override
    public void onDestroy() {
        release();
        super.onDestroy();
    }

    private void release() {
        if (inner == null) return;
        inner.destroy();
        inner = null;
    }

    /** The phone's own recognizer: a preferred vendor first, then any other that is not this one. */
    private ComponentName delegate() {
        List<ResolveInfo> all = getPackageManager().queryIntentServices(new Intent(RecognitionService.SERVICE_INTERFACE), 0);
        ComponentName fallback = null;
        for (String pkg : PREFERRED) {
            for (ResolveInfo r : all) {
                if (pkg.equals(r.serviceInfo.packageName)) return new ComponentName(pkg, r.serviceInfo.name);
            }
        }
        for (ResolveInfo r : all) {
            if (!getPackageName().equals(r.serviceInfo.packageName)) {
                fallback = new ComponentName(r.serviceInfo.packageName, r.serviceInfo.name);
                break;
            }
        }
        return fallback;
    }

    private interface Call { void run() throws RemoteException; }

    private static void send(Call c) {
        try {
            c.run();
        } catch (RemoteException ignored) {
            // The asking app went away; nothing to deliver to.
        }
    }
}
