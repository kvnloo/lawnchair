package app.lawnchair.zer0.voice;

import java.text.Normalizer;
import java.util.Locale;

/**
 * Parses a deliberately small set of explicit phone-navigation commands.
 *
 * <p>Parsing is not authorization or execution: callers must only provide finalized transcripts,
 * resolve app targets against installed apps, check relevant permissions and confirm outcomes.
 * No model, accessibility service, microphone, or network client is owned by this class.
 */
public final class VoiceCommand {
    public enum Kind {
        SWITCH_SPACE,
        OPEN_APP,
        SEARCH_APPS,
        START_FOCUS,
        STOP_FOCUS,
        SHOW_HOME,
        GO_BACK,
        SHOW_RECENTS,
        UNKNOWN
    }

    public final Kind kind;
    public final String target;

    private VoiceCommand(Kind kind, String target) {
        this.kind = kind;
        this.target = target;
    }

    private static VoiceCommand of(Kind kind) {
        return new VoiceCommand(kind, "");
    }

    /** Returns UNKNOWN for unsupported requests; never guesses at a destructive action. */
    public static VoiceCommand parse(String finalizedTranscript) {
        if (finalizedTranscript == null) return of(Kind.UNKNOWN);
        String text = Normalizer.normalize(finalizedTranscript, Normalizer.Form.NFKC)
                .toLowerCase(Locale.ROOT)
                .trim()
                .replaceAll("[.,!?]+$", "")
                .replaceAll("\s+", " ")
                .trim();
        if (text.isEmpty() || text.length() > 160) return of(Kind.UNKNOWN);

        switch (text) {
            case "home":
            case "go home":
            case "show home":
                return of(Kind.SHOW_HOME);
            case "back":
            case "go back":
                return of(Kind.GO_BACK);
            case "show recents":
            case "recent apps":
            case "show recent apps":
                return of(Kind.SHOW_RECENTS);
            case "stop focus":
            case "end focus":
            case "cancel focus":
                return of(Kind.STOP_FOCUS);
            case "start focus":
            case "start focus timer":
            case "focus for 25 minutes":
            case "start a 25 minute focus timer":
                return of(Kind.START_FOCUS);
            default:
                break;
        }

        for (String space : new String[] {"now", "build", "learn", "life"}) {
            if (text.equals("switch to " + space) || text.equals("go to " + space)
                    || text.equals("open " + space + " space") || text.equals(space + " space")) {
                return new VoiceCommand(Kind.SWITCH_SPACE, space);
            }
        }
        if (text.equals("open app") || text.equals("find app")) return of(Kind.UNKNOWN);
        for (String prefix : new String[] {"open app ", "open ", "launch "}) {
            if (text.startsWith(prefix)) {
                return new VoiceCommand(Kind.OPEN_APP, text.substring(prefix.length()));
            }
        }
        for (String prefix : new String[] {"find app ", "search for "}) {
            if (text.startsWith(prefix)) {
                return new VoiceCommand(Kind.SEARCH_APPS, text.substring(prefix.length()));
            }
        }
        return of(Kind.UNKNOWN);
    }
}
