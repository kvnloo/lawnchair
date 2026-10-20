package app.lawnchair.zer0.voice.z0live;

import java.util.Locale;
import java.util.Set;

/**
 * Decides whether detected "speech" is the owner talking. Voice activity alone fires on footsteps,
 * doors and room noise, so neither interruption nor a reply is allowed until the live transcript
 * contains real words. Pure: no Android, network or audio.
 *
 * <p>gpt-realtime only transcribes committed audio, so words normally arrive after the owner stops.
 * Two refinements keep that fast without trusting loudness: while the assistant is talking, the
 * first {@link #PROBE_MS} of the owner's speech is committed early (a barge probe) so its words can
 * interrupt within a second; and once the owner has stopped, a reply starts at the first transcript
 * delta holding two real words instead of waiting for the whole transcript. One utterance gets
 * exactly one reply, even when a probe split it into two conversation items.
 */
public final class Z0LiveSpeechGate {
    /** Speech heard over the assistant before its first part is committed for transcription. */
    public static final long PROBE_MS = 250;

    /** Transcripts that speech models commonly produce for non-speech sounds. */
    private static final Set<String> NOISE = Set.of(
            "you", "thank you", "thanks for watching", "bye", "uh", "um", "hm", "hmm", "mm", "mhm", "ah", "oh", "huh");

    private final StringBuilder heard = new StringBuilder();
    private boolean listening;
    private boolean interrupted;
    private boolean replied;

    /** Voice activity started. Nothing is interrupted yet. */
    public void speechStarted() { speechStarted(false); }

    /** Voice activity started. Returns true when a barge probe should be scheduled. */
    public boolean speechStarted(boolean assistantTalking) {
        listening = true;
        interrupted = false;
        replied = false;
        heard.setLength(0);
        return assistantTalking;
    }

    /** True when a scheduled barge probe should still commit (no words have interrupted yet). */
    public boolean probeDue() { return listening && !interrupted; }

    /** Live transcript text arrived. True exactly once per turn, when words prove the owner is talking. */
    public boolean transcriptDelta(String delta) {
        if (delta != null) heard.append(delta);
        if (!listening || interrupted || !isSpeech(heard.toString())) return false;
        interrupted = true;
        return true;
    }

    /**
     * After the owner stopped talking: true once per utterance when the transcript so far already
     * holds at least two real words, so the reply can start before the transcript completes.
     */
    public boolean earlyReply(boolean ownerStillTalking) {
        String text = heard.toString().trim();
        if (ownerStillTalking || replied || !isSpeech(text) || text.split("\\s+").length < 2) return false;
        replied = true;
        return true;
    }

    /** The turn's final transcript. True when it is speech and deserves a reply. */
    public boolean transcriptCompleted(String transcript) { return transcriptCompleted(transcript, false); }

    /**
     * The final transcript of one committed item. True when it is speech and this utterance has not
     * been answered yet. A barge-probe item completes while the owner keeps talking.
     */
    public boolean transcriptCompleted(String transcript, boolean ownerStillTalking) {
        boolean answered = replied;
        listening = ownerStillTalking;
        heard.setLength(0);
        boolean reply = isSpeech(transcript) && !answered;
        // The utterance ends with its last item: the next one is a new turn.
        replied = ownerStillTalking && (answered || reply);
        return reply;
    }

    /** True when this turn already interrupted the assistant. */
    public boolean interruptedThisTurn() { return interrupted; }

    public static boolean isSpeech(String text) {
        if (text == null) return false;
        String normalized = text.toLowerCase(Locale.ROOT).replaceAll("[^a-z' ]", " ").replaceAll("\\s+", " ").trim();
        int letters = 0;
        for (int i = 0; i < normalized.length(); i++) if (Character.isLetter(normalized.charAt(i))) letters++;
        // Two letters keeps short real answers ("ok", "no") while dropping scripts the owner does not
        // speak, which the transcriber emits for grunts and background noise.
        return letters >= 2 && !NOISE.contains(normalized);
    }
}
