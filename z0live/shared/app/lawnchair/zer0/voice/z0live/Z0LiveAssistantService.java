package app.lawnchair.zer0.voice.z0live;

import android.Manifest;
import android.app.role.RoleManager;
import android.content.ComponentName;
import android.content.Context;
import android.content.pm.PackageManager;
import android.service.voice.VoiceInteractionService;

/** Selected system assistant exemption; ROLE_ASSISTANT alone is not microphone authority. */
public final class Z0LiveAssistantService extends VoiceInteractionService {
    private Z0LiveGestureChannel gestures;
    static boolean mayStart(Context context) {
        if (android.os.Build.VERSION.SDK_INT < 29) return false;
        RoleManager roles = context.getSystemService(RoleManager.class);
        return roles != null && roles.isRoleHeld(RoleManager.ROLE_ASSISTANT)
                && VoiceInteractionService.isActiveService(context, new ComponentName(context, Z0LiveAssistantService.class))
                && context.checkSelfPermission(Manifest.permission.RECORD_AUDIO) == PackageManager.PERMISSION_GRANTED;
    }
    @Override public void onReady() {
        super.onReady();
        gestures = new Z0LiveGestureChannel(this);
        gestures.start();
    }
    @Override public void onShutdown() {
        if (gestures != null) gestures.close();
        super.onShutdown();
    }
}
