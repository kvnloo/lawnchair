package app.lawnchair.zer0.voice;

import java.text.Normalizer;
import java.util.Locale;

/**
 * Pure, deterministic one-shot routing. Routing to another app is a handoff, not
 * audio streaming or third-party tool invocation. No OS assistant role is changed.
 */
public final class SmartVoiceRouter {
    public enum Destination {
        LOCAL_ACTION,
        CHATGPT_APP,
        ANDROID_ASSISTANT,
        UNAVAILABLE
    }

    private SmartVoiceRouter() {}

    public static Destination select(
            String transcript, VoiceCommand.Kind command, boolean hasChatGpt,
            boolean hasAndroidAssistant) {
        if (!validTranscript(transcript)) return Destination.UNAVAILABLE;
        if (command == VoiceCommand.Kind.OPEN_APP
                || command == VoiceCommand.Kind.SWITCH_SPACE
                || command == VoiceCommand.Kind.SEARCH_APPS) {
            return Destination.LOCAL_ACTION;
        }
        // Unsupported local navigation is never executed without the required OS
        // privileges. The installed Android assistant can accept a new request.
        if (command != VoiceCommand.Kind.UNKNOWN) {
            return hasAndroidAssistant
                    ? Destination.ANDROID_ASSISTANT : Destination.UNAVAILABLE;
        }
        String text = Normalizer.normalize(transcript, Normalizer.Form.NFKC)
                .trim().toLowerCase(Locale.ROOT).replaceAll("\\s+", " ");

        // Gemini/Google is the user-selected default assistant, not ChatGPT.
        // Only a deliberate "ask ChatGPT" utterance opens the consumer app.
        if (matchesPrefix(text, "ask chatgpt", "open chatgpt")) {
            return hasChatGpt ? Destination.CHATGPT_APP : Destination.UNAVAILABLE;
        }
        return hasAndroidAssistant
                ? Destination.ANDROID_ASSISTANT : Destination.UNAVAILABLE;
    }

    static boolean validTranscript(String transcript) {
        if (transcript == null || transcript.isBlank() || transcript.length() > 240) {
            return false;
        }
        for (int i = 0; i < transcript.length();) {
            int cp = transcript.codePointAt(i);
            if (Character.isISOControl(cp)
                    || Character.getType(cp) == Character.FORMAT) return false;
            i += Character.charCount(cp);
        }
        return true;
    }

    private static boolean deviceRequest(String text) {
        return matchesPrefix(text,
                "call ", "dial ", "text ", "send a text ", "send a message ",
                "set an alarm", "set a timer", "start a timer", "create a reminder",
                "remind me ", "turn on ", "turn off ", "switch on ", "switch off ",
                "increase volume", "decrease volume", "set volume",
                "navigate to ", "directions to ", "play music",
                "pause music", "next song", "check notifications",
                "open notifications", "turn the flashlight", "enable bluetooth",
                "disable bluetooth", "enable wifi", "disable wifi");
    }

    private static boolean matchesPrefix(String text, String... prefixes) {
        for (String prefix : prefixes) {
            if (text.equals(prefix.trim()) || text.startsWith(prefix)) return true;
        }
        return false;
    }
}
