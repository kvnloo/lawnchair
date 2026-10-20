import app.lawnchair.zer0.voice.z0live.Z0LiveAudioState;

public final class Z0LiveAudioStateTest {
    private static int checks;
    public static void main(String[] args) {
        Z0LiveAudioState state = new Z0LiveAudioState();
        check(!state.microphoneEnabled(), "no audio before a real connection");
        state.setConnected(true);
        check(state.microphoneEnabled(), "connected owner can speak and interrupt Realtime voice");
        check(state.realtimeSpeakerEnabled(), "Hermes uses the existing Realtime speaker, not Android TTS");
        state.toggleMute();
        check(state.isMuted() && !state.microphoneEnabled(), "Realtime reply cannot undo manual mute");
        check(state.realtimeSpeakerEnabled(), "manual mic mute does not silence a reply");
        state.toggleMute();
        check(state.microphoneEnabled(), "unmuted mic remains open for barge-in while GPT speaks");
        state.setFocus(false);
        check(!state.microphoneEnabled() && !state.realtimeSpeakerEnabled(), "focus loss suppresses capture and playback");
        state.setFocus(true);
        check(state.microphoneEnabled(), "focus gain restores only unmuted mic");
        state.setConnected(false);
        state.setFocus(true);
        check(!state.microphoneEnabled(), "late focus callbacks cannot enable ended mic");
        System.out.println("PASS: " + checks + " native Realtime audio admission checks");
    }
    private static void check(boolean condition, String message) {
        if (!condition) throw new AssertionError(message);
        checks++;
    }
}
