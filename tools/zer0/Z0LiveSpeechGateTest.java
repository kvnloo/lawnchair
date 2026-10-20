package app.lawnchair.zer0.voice.z0live;

import org.json.JSONObject;

/** Barge-in and replies require real words: room noise must neither interrupt nor get answered. */
public final class Z0LiveSpeechGateTest {
    private static int checks;
    private static void check(boolean value, String message) { if (!value) throw new AssertionError(message); checks++; }

    public static void main(String[] args) throws Exception {
        Z0LiveSpeechGate gate = new Z0LiveSpeechGate();

        gate.speechStarted();
        check(!gate.transcriptDelta(""), "voice activity alone (footsteps) never interrupts");
        check(!gate.transcriptDelta("."), "punctuation-only transcript is not speech");
        check(!gate.transcriptCompleted(""), "empty turn gets no reply");

        gate.speechStarted();
        check(!gate.transcriptDelta("うん。"), "non-English grunt transcription does not interrupt");
        check(!gate.transcriptCompleted("うん。"), "non-English grunt transcription is not answered");

        gate.speechStarted();
        check(!gate.transcriptCompleted("Thank you."), "common noise hallucination is not answered");

        gate.speechStarted();
        check(!gate.transcriptDelta("W"), "one letter is not yet proof of speech");
        check(gate.transcriptDelta("ait"), "real words interrupt");
        check(!gate.transcriptDelta(" stop"), "a turn interrupts at most once");
        check(gate.interruptedThisTurn(), "turn remembers it interrupted");
        check(gate.transcriptCompleted("Wait stop"), "real turn is answered");

        gate.speechStarted();
        check(!gate.interruptedThisTurn(), "new turn starts uninterrupted");
        check(gate.transcriptCompleted("OK."), "short real answers count");
        check(gate.transcriptCompleted("no"), "short real answers count");

        check(!gate.transcriptDelta("hello"), "words outside a detected turn do not interrupt");

        JSONObject input = new JSONObject(Z0LiveBargeIn.vadConfiguration()).getJSONObject("session").getJSONObject("audio").getJSONObject("input");
        JSONObject vad = input.getJSONObject("turn_detection");
        check(vad.getString("type").equals("semantic_vad"), "turn detection judges speech, not loudness");
        check(!vad.getBoolean("interrupt_response"), "server never interrupts on voice activity alone");
        check(!vad.getBoolean("create_response"), "server never answers a turn the client has not confirmed as speech");
        check(input.getJSONObject("noise_reduction").getString("type").equals("far_field"), "room noise is reduced for a phone away from the mouth");

        Z0LiveTurnState turn = new Z0LiveTurnState();
        turn.userTurnNeedsReply();
        turn.userSpeaking(true);
        check(!turn.takeUserReply(), "no reply while the owner is still talking");
        turn.userSpeaking(false);
        turn.responseStarted();
        check(!turn.takeUserReply(), "reply waits for the cancelled response to finish");
        turn.responseDone();
        check(!turn.canSpeakInbox(), "a pending user reply goes before inbox speech");
        check(turn.takeUserReply(), "reply is sent at the next safe boundary");
        check(!turn.takeUserReply(), "exactly one reply per turn");
        turn.responseDone();
        turn.toolCompleted();
        turn.userTurnNeedsReply();
        check(turn.takeUserReply() && !turn.takeContinuation(false), "the user's reply absorbs a pending tool continuation (one response, not two)");

        System.out.println("PASS: " + checks + " speech-gated barge-in and reply checks");
    }
}
