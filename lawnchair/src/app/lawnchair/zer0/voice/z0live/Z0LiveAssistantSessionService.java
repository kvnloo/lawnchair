package app.lawnchair.zer0.voice.z0live;

import android.os.Bundle;
import android.service.voice.VoiceInteractionSession;
import android.service.voice.VoiceInteractionSessionService;

public final class Z0LiveAssistantSessionService extends VoiceInteractionSessionService {
    @Override
    public VoiceInteractionSession onNewSession(Bundle args) {
        return new VoiceInteractionSession(this) {
            @Override
            public void onCreate() {
                super.onCreate();
                setUiEnabled(false);
            }

            @Override
            public void onShow(Bundle args, int flags) {
                super.onShow(args, flags);
                try {
                    // The assistant gesture means "talk now": start a call unless one is already live.
                    startAssistantActivity(Z0LiveActivity.intent(getContext(), true));
                } finally {
                    hide();
                }
            }
        };
    }
}
