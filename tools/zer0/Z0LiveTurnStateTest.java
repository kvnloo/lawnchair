import app.lawnchair.zer0.voice.z0live.Z0LiveTurnState;

public final class Z0LiveTurnStateTest {
    private static int checks;
    private static void check(boolean ok) { if (!ok) throw new AssertionError("turn check " + checks); checks++; }
    public static void main(String[] args) {
        Z0LiveTurnState turn = new Z0LiveTurnState();
        check(turn.canSpeakInbox());
        turn.responseStarted();
        turn.toolCompleted();
        check(!turn.takeContinuation(false));
        check(!turn.canSpeakInbox());
        turn.responseDone();
        check(!turn.takeContinuation(true));
        turn.userSpeaking(true);
        check(!turn.takeContinuation(false));
        turn.userSpeaking(false);
        turn.playback(true);
        check(!turn.takeContinuation(false));
        turn.playback(false);
        check(turn.takeContinuation(false));
        check(!turn.takeContinuation(false));
        check(!turn.canSpeakInbox());
        turn.responseDone();
        check(turn.canSpeakInbox());
        turn.userSpeaking(true);
        check(!turn.canSpeakInbox());
        turn.userSpeaking(false);
        turn.playback(true);
        check(!turn.canSpeakInbox());
        turn.playback(false);
        check(turn.canSpeakInbox());
        turn.userSpeaking(true);
        check(!turn.canSpeakInbox());
        turn.inputMuted(true);
        check(turn.canSpeakInbox());
        turn.userSpeaking(true); // delayed VAD event after the mic track was disabled
        check(turn.canSpeakInbox());
        turn.toolCompleted();
        check(turn.takeContinuation(false));
        turn.responseDone();
        turn.inputMuted(false);
        turn.userSpeaking(true);
        check(!turn.canSpeakInbox());
        turn.userSpeaking(false);
        check(turn.canSpeakInbox());
        System.out.println("PASS: " + checks + " safe-turn continuation and inbox checks");
    }
}
