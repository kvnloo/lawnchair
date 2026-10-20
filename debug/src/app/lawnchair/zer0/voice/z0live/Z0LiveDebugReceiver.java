package app.lawnchair.zer0.voice.z0live;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.util.Log;

/** Debug source set only. DUMP permission restricts exported injection to adb/system. */
public final class Z0LiveDebugReceiver extends BroadcastReceiver {
    @Override public void onReceive(Context context, Intent intent) {
        if (intent.getBooleanExtra("stats", false)) { Z0LiveService.debugAudioStats(); return; }
        if (intent.getBooleanExtra("ready", false)) { setResultCode(Z0LiveService.debugReady() ? 1 : 0); return; }
        if (intent.hasExtra("muted")) { Z0LiveService.debugMute(intent.getBooleanExtra("muted", true)); return; }
        if (intent.getBooleanExtra("end", false)) { Z0LiveService.end(context); return; }
        boolean accepted = Z0LiveService.injectDebugText(intent.getStringExtra("text"));
        Log.i("z0live", "debug_turn accepted=" + accepted);
        setResultCode(accepted ? 1 : 0);
    }
}
