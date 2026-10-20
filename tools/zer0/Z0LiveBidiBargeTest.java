package app.lawnchair.zer0.voice.z0live;

/** Behavior of Frameless Bidi client barge-in: words duck playback, noise never does. */
public final class Z0LiveBidiBargeTest {
    private static int checked;

    public static void main(String[] args) {
        Z0LiveBidiBarge barge = new Z0LiveBidiBarge();
        check(!barge.userTranscript("hello there", 0), "no assistant audio: nothing to duck");
        barge.userTurnDone();

        check(!barge.assistantTurnCreated("turn_a"), "assistant starts speaking");
        check(!barge.userTranscript("hmm", 100), "a grunt does not duck");
        check(!barge.userTranscript(" ", 150), "silence does not duck");
        barge.userTurnDone();
        check(!barge.userTranscript("Thank you", 200), "known noise hallucination does not duck");
        barge.userTurnDone();
        check(barge.userTranscript("Wait,", 1000), "real words duck playback immediately");
        check(barge.ducked(), "playback is ducked");
        check(!barge.userTranscript(" stop.", 1100), "ducks once per barge");
        check(!barge.assistantTurnDone("turn_a", 1040), "the interrupted turn ending does not restore playback");
        check(barge.ducked(), "buffered audio of the interrupted turn stays silent");
        barge.userTurnDone();
        check(barge.assistantTurnCreated("turn_b"), "the model's reply restores playback");
        check(!barge.ducked(), "reply is audible");

        // The model often yields at the same moment its transcript of the barge arrives.
        check(!barge.assistantTurnDone("turn_b", 5000), "reply finishes");
        check(barge.userTranscript("no, the other one", 5200), "words right after a turn still silence its buffered tail");
        barge.userTurnDone();
        barge.assistantTurnCreated("turn_c");
        barge.assistantTurnDone("turn_c", 9000);
        check(!barge.userTranscript("and another thing", 9000 + Z0LiveBidiBarge.TAIL_MS + 1), "long after a turn, nothing is ducked");

        // Rapid follow-up: words right after a reply duck its tail; the full-duplex answer to the
        // follow-up streams before its turn is created and must be heard (seen in the parity eval).
        barge.assistantTurnCreated("turn_q1");
        check(!barge.assistantOutput(), "ordinary output is not an un-duck when nothing is ducked");
        barge.userTurnDone();
        check(barge.userTranscript(" And times", 20010), "follow-up words over the reply duck it");
        check(!barge.assistantOutput(), "the interrupted reply's own trailing output stays silent");
        barge.assistantTurnDone("turn_q1", 20050);
        check(barge.assistantOutput(), "new output after the interrupted turn ended restores playback");
        check(!barge.ducked(), "the follow-up answer is audible");
        check(!barge.userTranscript(" three?", 20400), "the rest of the follow-up's late transcript never re-mutes its answer");
        check(!barge.ducked(), "the answer stays audible");
        barge.userTurnDone();
        barge.assistantTurnDone("turn_q2", 21000);
        check(barge.userTranscript("and divided by two", 21500), "tail duck right after a turn");
        check(barge.assistantOutput(), "the turn had already ended: the next output is the answer");

        barge.assistantTurnCreated("turn_d");
        barge.userTranscript("stop please", 12000);
        barge.reset();
        check(!barge.ducked(), "a new call starts audible");
        System.out.println("PASS: " + checked + " Frameless Bidi barge-in checks");
    }

    private static void check(boolean condition, String label) {
        if (!condition) throw new AssertionError(label);
        checked++;
    }
}
