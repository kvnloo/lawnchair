import app.lawnchair.zer0.voice.z0live.Z0LiveAudioState;

public final class Z0LiveAudioStateTest {
    public static void main(String[] args) {
        Z0LiveAudioState state = new Z0LiveAudioState();
        check(!state.microphoneEnabled(), "no audio before a real connection");
        state.setConnected(true);
        check(state.microphoneEnabled(), "connected owner can speak");
        state.toggleMute();
        state.setPlayingInbox(true);
        state.setPlayingInbox(false);
        check(state.isMuted() && !state.microphoneEnabled(), "TTS cannot undo owner mute");
        state.toggleMute();
        state.setPlayingInbox(true);
        check(!state.microphoneEnabled() && !state.realtimeSpeakerEnabled(), "inbox does not feed back into realtime");
        state.toggleMute();
        state.setPlayingInbox(false);
        check(state.isMuted() && !state.microphoneEnabled(), "mute changed during TTS survives completion");
        state.toggleMute();
        state.setFocus(false);
        state.setPlayingInbox(true);
        state.setPlayingInbox(false);
        check(!state.microphoneEnabled(), "TTS completion cannot bypass audio focus loss");
        state.setFocus(true);
        check(state.microphoneEnabled(), "focus gain restores only unmuted mic");
        state.setConnected(false);
        state.setFocus(true);
        state.setPlayingInbox(false);
        check(!state.microphoneEnabled(), "late callbacks cannot enable ended mic");
        System.out.println("PASS: 9 native voice audio admission checks");
    }
    private static void check(boolean condition, String message) {
        if (!condition) throw new AssertionError(message);
    }
}
