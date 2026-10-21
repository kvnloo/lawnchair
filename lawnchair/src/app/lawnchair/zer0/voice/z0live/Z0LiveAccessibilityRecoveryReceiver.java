package app.lawnchair.zer0.voice.z0live;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;

/** Does not start voice, a foreground service or a microphone at boot/update. */
public final class Z0LiveAccessibilityRecoveryReceiver extends BroadcastReceiver {
    @Override public void onReceive(Context context, Intent intent) {
        if (Intent.ACTION_BOOT_COMPLETED.equals(intent.getAction()) || Intent.ACTION_MY_PACKAGE_REPLACED.equals(intent.getAction())) {
            Z0LiveAccessibilityRecovery.check(context);
        }
    }
}
