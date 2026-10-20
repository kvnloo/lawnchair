package app.lawnchair.zer0.voice.z0live;

/** Behavior of Frameless Bidi client barge-in: words duck playback, noise never does. */
public final class Z0LiveBidiBargeTest {
    private static int checked;

    public static void main(String[] args) {
        Z0LiveBidiBarge barge = new Z0LiveBidiBarge();
        check(!barge.userTranscript("hello there"), "no open assistant turn: nothing to duck");
        barge.userTurnDone();

        check(!barge.assistantTurnCreated("turn_a"), "assistant starts speaking");
        check(!barge.userTranscript("hmm"), "a grunt does not duck");
        check(!barge.userTranscript(" "), "silence does not duck");
        barge.userTurnDone();
        check(!barge.userTranscript("Thank you"), "known noise hallucination does not duck");
        barge.userTurnDone();
        check(barge.userTranscript("Wait,"), "real words duck playback immediately");
        check(barge.ducked(), "playback is ducked");
        check(!barge.userTranscript(" stop."), "ducks once per barge");
        barge.userTurnDone();
        check(barge.ducked(), "still ducked while the interrupted turn may keep streaming");
        check(barge.assistantTurnCreated("turn_b"), "the model's reply restores playback");
        check(!barge.ducked(), "reply is audible");

        check(barge.userTranscript("no, the other one"), "barge on the reply too");
        check(barge.assistantTurnDone("turn_b"), "interrupted turn finishing restores playback");
        check(!barge.ducked(), "audible again");
        barge.userTurnDone();
        check(!barge.userTranscript("and another thing"), "closed turn is never ducked");

        barge.assistantTurnCreated("turn_c");
        barge.userTranscript("stop please");
        barge.reset();
        check(!barge.ducked(), "a new call starts audible");
        System.out.println("PASS: " + checked + " Frameless Bidi barge-in checks");
    }

    private static void check(boolean condition, String label) {
        if (!condition) throw new AssertionError(label);
        checked++;
    }
}
