package app.lawnchair.zer0.voice.z0live;

import java.util.Locale;
import java.util.Set;

/**
 * Decides whether detected "speech" is the owner talking. Voice activity alone fires on footsteps,
 * doors and room noise, so neither interruption nor a reply is allowed until the live transcript
 * contains real words. Pure: no Android, network or audio.
 */
public final class Z0LiveSpeechGate {
    /** Transcripts that speech models commonly produce for non-speech sounds. */
    private static final Set<String> NOISE = Set.of(
            "you", "thank you", "thanks for watching", "bye", "uh", "um", "hm", "hmm", "mm", "mhm", "ah", "oh", "huh");

    private final StringBuilder heard = new StringBuilder();
    private boolean listening;
    private boolean interrupted;

    /** Voice activity started. Nothing is interrupted yet. */
    public void speechStarted() {
        listening = true;
        interrupted = false;
        heard.setLength(0);
    }

    /** Live transcript text arrived. True exactly once per turn, when words prove the owner is talking. */
    public boolean transcriptDelta(String delta) {
        if (delta != null) heard.append(delta);
        if (!listening || interrupted || !isSpeech(heard.toString())) return false;
        interrupted = true;
        return true;
    }

    /** The turn's final transcript. True when it is speech and deserves a reply. */
    public boolean transcriptCompleted(String transcript) {
        listening = false;
        heard.setLength(0);
        return isSpeech(transcript);
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
