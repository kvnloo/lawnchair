package app.lawnchair.zer0.voice.z0live;

/**
 * Client barge-in for Frameless Bidi calls. The model does its own full-duplex turn-taking, which
 * can take more than a second to yield. When the owner's live transcript shows real words while an
 * assistant turn is still open, playback is silenced on the phone at once; it comes back for the
 * model's next turn (its reply). Noise never ducks: only {@link Z0LiveSpeechGate#isSpeech} text does.
 * Pure: no Android, network or audio.
 */
public final class Z0LiveBidiBarge {
    private final StringBuilder heard = new StringBuilder();
    private String assistant = "";
    private String ducked = "";
    private boolean open;

    /** An assistant turn started. Returns true when this un-ducks playback for the model's reply. */
    public boolean assistantTurnCreated(String id) {
        assistant = id == null ? "" : id;
        open = true;
        if (!ducked.isEmpty() && !ducked.equals(assistant)) { ducked = ""; return true; }
        return false;
    }

    /** An assistant turn finished. Returns true when this un-ducks playback. */
    public boolean assistantTurnDone(String id) {
        if (id != null && id.equals(assistant)) open = false;
        if (!ducked.isEmpty() && ducked.equals(id)) { ducked = ""; return true; }
        return false;
    }

    /** Live owner transcript text. Returns true exactly when playback must be ducked now. */
    public boolean userTranscript(String text) {
        if (text != null) heard.append(text);
        if (!open || !ducked.isEmpty() || !Z0LiveSpeechGate.isSpeech(heard.toString())) return false;
        ducked = assistant.isEmpty() ? "?" : assistant;
        return true;
    }

    /** The owner's turn finished: the next utterance starts a fresh word check. */
    public void userTurnDone() { heard.setLength(0); }

    public boolean ducked() { return !ducked.isEmpty(); }

    /** Reset for a new or ended call. */
    public void reset() { heard.setLength(0); assistant = ""; ducked = ""; open = false; }
}
