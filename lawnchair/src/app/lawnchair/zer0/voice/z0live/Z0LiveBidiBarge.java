package app.lawnchair.zer0.voice.z0live;

/**
 * Client barge-in for Frameless Bidi calls. The model does its own full-duplex turn-taking and
 * yields about a second after the owner starts talking, at the same moment its transcript of the
 * owner's words arrives. By then the phone may still hold buffered assistant audio, so when the
 * owner's live transcript shows real words while the assistant is talking (or finished a turn
 * within {@link #TAIL_MS}), playback is silenced at once and stays silent until the model's next
 * turn (its reply). Noise never ducks: only {@link Z0LiveSpeechGate#isSpeech} text does.
 * Pure: no Android, network or audio.
 */
public final class Z0LiveBidiBarge {
    /** Buffered assistant audio can keep playing this long after its turn ends. */
    public static final long TAIL_MS = 1500;

    private final StringBuilder heard = new StringBuilder();
    private boolean open;
    private boolean ducked;
    private long endedAt = Long.MIN_VALUE / 2;

    /** An assistant turn started. Returns true when this un-ducks playback for the model's reply. */
    public boolean assistantTurnCreated(String id) {
        open = true;
        if (!ducked) return false;
        ducked = false;
        return true;
    }

    /** An assistant turn finished at {@code nowMs}. Playback stays ducked until the next turn. */
    public boolean assistantTurnDone(String id, long nowMs) {
        open = false;
        endedAt = nowMs;
        return false;
    }

    /** Live owner transcript text at {@code nowMs}. Returns true exactly when playback must be ducked now. */
    public boolean userTranscript(String text, long nowMs) {
        if (text != null) heard.append(text);
        boolean talking = open || nowMs - endedAt < TAIL_MS;
        if (!talking || ducked || !Z0LiveSpeechGate.isSpeech(heard.toString())) return false;
        ducked = true;
        return true;
    }

    /** The owner's turn finished: the next utterance starts a fresh word check. */
    public void userTurnDone() { heard.setLength(0); }

    public boolean ducked() { return ducked; }

    /** Reset for a new or ended call. */
    public void reset() { heard.setLength(0); open = false; ducked = false; endedAt = Long.MIN_VALUE / 2; }
}
