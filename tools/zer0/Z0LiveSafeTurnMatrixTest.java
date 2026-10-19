import app.lawnchair.zer0.voice.z0live.Z0LiveAudioState;
import app.lawnchair.zer0.voice.z0live.Z0LiveTurnState;

/**
 * Pure-Java boundary matrix. This is not an audio, Android, or service lifecycle test.
 * All state changes use public APIs of the canonical classes.
 */
public final class Z0LiveSafeTurnMatrixTest {
    private static int checks;

    private static void expect(boolean actual, boolean expected, String label) {
        if (actual != expected) {
            throw new AssertionError(label + ": expected " + expected + ", got " + actual);
        }
        checks++;
    }

    private static void turnGateMatrix() {
        // Exhaust response, user speech, playback, queued continuation and pending tools.
        for (int mask = 0; mask < 32; mask++) {
            boolean response = (mask & 1) != 0;
            boolean user = (mask & 2) != 0;
            boolean playback = (mask & 4) != 0;
            boolean continuation = (mask & 8) != 0;
            boolean toolsPending = (mask & 16) != 0;
            String label = "turn mask=" + mask + " [response=" + response
                    + ",user=" + user + ",playback=" + playback
                    + ",continuation=" + continuation + ",toolsPending=" + toolsPending + "]";
            Z0LiveTurnState turn = new Z0LiveTurnState();
            if (response) turn.responseStarted();
            turn.userSpeaking(user);
            turn.playback(playback);
            if (continuation) turn.toolCompleted();
            // Pending tools block continuation, not already-safe inbox speech.
            expect(turn.canSpeakInbox(), !response && !user && !playback && !continuation,
                    label + " inbox admission");
            boolean mayContinue = continuation && !response && !user
                    && !playback && !toolsPending;
            expect(turn.takeContinuation(toolsPending), mayContinue,
                    label + " continuation admission");
            expect(turn.takeContinuation(toolsPending), false,
                    label + " duplicate take is inert while gated");
            if (mayContinue) {
                expect(turn.canSpeakInbox(), false, label + " response newly active");
                turn.responseDone();
                expect(turn.canSpeakInbox(), true, label + " response finished");
            }
        }
    }

    private static void rapidEventsAndDuplicateCompletion() {
        Z0LiveTurnState turn = new Z0LiveTurnState();
        expect(turn.canSpeakInbox(), true, "idle allows inbox");
        expect(turn.takeContinuation(false), false, "no completion is not permission");
        turn.responseStarted();
        turn.toolCompleted();
        turn.toolCompleted(); // Multiple callbacks before admission coalesce into one flag.
        expect(turn.canSpeakInbox(), false, "active response blocks inbox");
        expect(turn.takeContinuation(false), false, "active response blocks continuation");
        turn.responseDone();
        turn.userSpeaking(true);
        expect(turn.canSpeakInbox(), false, "user speech blocks inbox");
        expect(turn.takeContinuation(false), false, "user speech blocks continuation");
        turn.userSpeaking(false);
        turn.playback(true);
        expect(turn.canSpeakInbox(), false, "playback blocks inbox");
        expect(turn.takeContinuation(false), false, "playback blocks continuation");
        turn.playback(false);
        expect(turn.takeContinuation(true), false, "unresolved tools block continuation");
        expect(turn.canSpeakInbox(), false, "queued continuation blocks inbox");
        expect(turn.takeContinuation(false), true, "first safe boundary admits continuation");
        expect(turn.takeContinuation(false), false, "duplicate completion cannot double-take");
        expect(turn.canSpeakInbox(), false, "continuation response blocks inbox");
        turn.responseDone();
        expect(turn.canSpeakInbox(), true, "inbox recovers after response");
        turn.userSpeaking(true);
        expect(turn.canSpeakInbox(), false, "new user speech immediately gates inbox");
        turn.userSpeaking(false);
        turn.playback(true);
        expect(turn.canSpeakInbox(), false, "new playback immediately gates inbox");
        turn.playback(false);
        expect(turn.canSpeakInbox(), true, "inbox releases after playback");
    }

    private static void audioAdmissionMatrix() {
        // Exhaust connected, focus, owner mute, and inbox playback (16 combinations).
        for (int mask = 0; mask < 16; mask++) {
            boolean connected = (mask & 1) != 0;
            boolean focus = (mask & 2) != 0;
            boolean muted = (mask & 4) != 0;
            boolean inbox = (mask & 8) != 0;
            String label = "audio mask=" + mask;
            Z0LiveAudioState audio = new Z0LiveAudioState();
            audio.setConnected(connected);
            audio.setFocus(focus);
            if (muted) audio.toggleMute();
            audio.setPlayingInbox(inbox);
            expect(audio.isMuted(), muted, label + " owner mute preserved");
            expect(audio.microphoneEnabled(), connected && focus && !muted && !inbox,
                    label + " microphone admission");
            // The speaker getter does not itself assert transport connection.
            expect(audio.realtimeSpeakerEnabled(), focus && !inbox,
                    label + " realtime speaker suppression flag");
        }
    }

    private static void audioLateCallbacksAndHangup() {
        Z0LiveAudioState audio = new Z0LiveAudioState();
        expect(audio.microphoneEnabled(), false, "no mic before connection");
        audio.setConnected(true);
        expect(audio.microphoneEnabled(), true, "mic enabled only with live connection");
        audio.toggleMute();
        audio.setPlayingInbox(true);
        audio.setPlayingInbox(false);
        expect(audio.isMuted(), true, "TTS finish cannot unmute owner");
        expect(audio.microphoneEnabled(), false, "TTS finish cannot bypass owner mute");
        audio.toggleMute();
        audio.setPlayingInbox(true);
        expect(audio.microphoneEnabled(), false, "inbox audio suppresses mic");
        expect(audio.realtimeSpeakerEnabled(), false, "inbox audio suppresses speaker");
        audio.setFocus(false);
        audio.setPlayingInbox(false);
        expect(audio.microphoneEnabled(), false, "focus loss survives playback completion");
        audio.setFocus(true);
        expect(audio.microphoneEnabled(), true, "unmuted mic resumes on focus restoration");
        audio.setConnected(false); // The represented disconnect boundary; no teardown API.
        audio.setPlayingInbox(true);
        audio.setPlayingInbox(false);
        audio.setFocus(false);
        audio.setFocus(true); // Simulate callbacks that arrive after disconnect.
        expect(audio.microphoneEnabled(), false, "late callbacks never reconnect mic");
        audio.toggleMute();
        audio.toggleMute();
        expect(audio.microphoneEnabled(), false, "mute changes cannot reconnect mic");
    }

    public static void main(String[] args) {
        turnGateMatrix();
        rapidEventsAndDuplicateCompletion();
        audioAdmissionMatrix();
        audioLateCallbacksAndHangup();
        System.out.println("PASS: " + checks + " safe-turn/audio state assertions (host JVM only)");
    }
}
