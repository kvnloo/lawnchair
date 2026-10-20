package app.lawnchair.zer0.voice.z0live;

/**
 * Client barge-in for Frameless Bidi calls. The model does its own full-duplex turn-taking and
 * yields about a second after the owner starts talking, at the same moment its transcript of the
 * owner's words arrives. By then the phone may still hold buffered assistant audio, so when the
 * owner's live transcript shows real words while the assistant is talking (or finished a turn
 * within {@link #TAIL_MS}), playback is silenced at once. It comes back as soon as the model says
 * something new: its next turn, or new output after the interrupted turn has ended (full duplex
 * often answers a quick follow-up before its turn is formally created). Noise never ducks: only
 * {@link Z0LiveSpeechGate#isSpeech} text does. Pure: no Android, network or audio.
 */
public final class Z0LiveBidiBarge {
    /** Buffered assistant audio can keep playing this long after its turn ends. */
    public static final long TAIL_MS = 1500;

    private final StringBuilder heard = new StringBuilder();
    private boolean open;
    private boolean ducked;
    private boolean duckedTurnEnded;
    /** One duck per owner turn: the rest of a late-arriving transcript must not mute the answer. */
    private boolean duckedThisTurn;
    private long endedAt = Long.MIN_VALUE / 2;

    /** An assistant turn started. Returns true when this un-ducks playback for the model's reply. */
    public boolean assistantTurnCreated(String id) {
        open = true;
        return unduck();
    }

    /** An assistant turn finished at {@code nowMs}. The interrupted turn's end does not un-duck. */
    public boolean assistantTurnDone(String id, long nowMs) {
        open = false;
        endedAt = nowMs;
        if (ducked) duckedTurnEnded = true;
        return false;
    }

    /** Assistant output text arrived. Returns true when it is new speech after the interrupted turn. */
    public boolean assistantOutput() {
        return duckedTurnEnded && unduck();
    }

    /** Live owner transcript text at {@code nowMs}. Returns true exactly when playback must be ducked now. */
    public boolean userTranscript(String text, long nowMs) {
        if (text != null) heard.append(text);
        boolean talking = open || nowMs - endedAt < TAIL_MS;
        if (!talking || ducked || duckedThisTurn || !Z0LiveSpeechGate.isSpeech(heard.toString())) return false;
        ducked = true;
        duckedThisTurn = true;
        duckedTurnEnded = !open;
        return true;
    }

    /** The owner's turn finished: the next utterance starts a fresh word check. */
    public void userTurnDone() { heard.setLength(0); duckedThisTurn = false; }

    public boolean ducked() { return ducked; }

    /** Reset for a new or ended call. */
    public void reset() {
        heard.setLength(0);
        open = false;
        ducked = false;
        duckedTurnEnded = false;
        duckedThisTurn = false;
        endedAt = Long.MIN_VALUE / 2;
    }

    private boolean unduck() {
        if (!ducked) return false;
        ducked = false;
        duckedTurnEnded = false;
        return true;
    }
}
