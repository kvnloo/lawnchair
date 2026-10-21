package app.lawnchair.zer0.voice.z0live;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

/**
 * Tier 0 on-phone target choice: sub-millisecond lexical + synonym scoring of a spoken goal
 * ("click the menu button") against the compact accessibility snapshot. It acts only when the
 * best target is clearly ahead; otherwise the ranked candidates escalate to a bigger model.
 */
public final class Z0LiveTargetMatcher {
    public static final double ACT_SCORE = 0.78;
    public static final double ACT_MARGIN = 0.15;
    public static final int MAX_CANDIDATES = 16;

    private Z0LiveTargetMatcher() {}

    public static final class Candidate {
        public final int index;
        public final double score;

        Candidate(int index, double score) {
            this.index = index;
            this.score = score;
        }
    }

    public static final class Choice {
        public final List<Candidate> ranked;
        public final double confidence;
        public final double margin;
        public final String query;

        Choice(List<Candidate> ranked, String query) {
            this.ranked = ranked;
            this.query = query;
            this.confidence = ranked.isEmpty() ? 0 : ranked.get(0).score;
            this.margin = ranked.isEmpty() ? 0 : ranked.size() == 1 ? ranked.get(0).score : ranked.get(0).score - ranked.get(1).score;
        }

        public boolean confident() { return confidence >= ACT_SCORE && margin >= ACT_MARGIN; }

        public int best() { return ranked.isEmpty() ? -1 : ranked.get(0).index; }
    }

    private static final Set<String> FILLER = new HashSet<>(Arrays.asList(
            "can", "could", "would", "you", "please", "pls", "just", "tap", "click", "press", "hit", "select",
            "choose", "pick", "touch", "push", "on", "the", "a", "an", "button", "icon", "for", "me", "that", "it",
            "now", "then", "go", "to", "and", "into", "in", "of", "this", "there", "hey", "ok", "okay", "hello",
            "item", "option", "thing", "link", "top", "bottom", "left", "right", "corner", "screen", "app", "open", "show",
            "my", "launch", "start", "let's", "lets", "want", "need", "i", "would", "like"));

    /** Concept groups: a goal word in a group matches any node phrase in the same group. */
    private static final String[][] GROUPS = {
        {"menu", "more options", "options", "overflow", "navigation drawer", "open navigation", "drawer", "sidebar",
            "open sidebar", "hamburger", "navigation menu", "show menu", "main menu", "nav drawer", "open menu", "side menu"},
        {"back", "navigate up", "go back", "up"},
        {"search", "find", "magnifier", "look up"},
        {"settings", "preferences", "gear", "setting"},
        {"new chat", "new conversation", "start new chat", "compose", "new message", "create new"},
        {"close", "dismiss", "cancel", "exit"},
        {"profile", "account", "avatar", "my account"},
        {"voice", "microphone", "mic", "dictate", "speak", "voice mode", "voice input"},
        {"attach", "add photos", "upload", "add files", "attachment", "plus", "add"},
        {"play", "resume"},
        {"pause", "stop playback"},
        {"home", "home tab"},
        {"share", "send to"},
        {"refresh", "reload"},
        {"like", "thumbs up", "favorite", "heart", "good response"},
        {"read aloud", "read out loud", "read it out", "speak it", "listen"},
        {"message box", "text box", "text field", "chat box", "input box", "ask anything", "type a message",
            "prompt box", "message field", "compose box"},
        {"address bar", "url bar", "url", "web address", "location bar"},
        {"tabs", "tab switcher", "switch tabs", "close tabs", "open tabs"},
        {"new email", "compose", "write email", "new mail"},
    };

    public static String normalize(String text) {
        if (text == null) return "";
        String lower = text.toLowerCase(Locale.ROOT).replace('’', '\'').replaceAll("[^a-z0-9' ]", " ");
        return lower.trim().replaceAll("\\s+", " ");
    }

    /** Goal minus command filler: "can you click on the menu button" -> "menu". */
    public static String query(String goal) {
        StringBuilder out = new StringBuilder();
        for (String word : normalize(goal).split(" ")) {
            if (word.isEmpty() || FILLER.contains(word)) continue;
            if (out.length() > 0) out.append(' ');
            out.append(word);
        }
        String q = out.toString();
        return q.isEmpty() ? normalize(goal) : q;
    }

    private static boolean containsPhrase(String hay, String phrase) {
        if (phrase.isEmpty() || hay.isEmpty()) return false;
        int at = hay.indexOf(phrase);
        while (at >= 0) {
            boolean startOk = at == 0 || hay.charAt(at - 1) == ' ';
            int end = at + phrase.length();
            boolean endOk = end == hay.length() || hay.charAt(end) == ' ';
            if (startOk && endOk) return true;
            at = hay.indexOf(phrase, at + 1);
        }
        return false;
    }

    private static Set<Integer> groupsOf(String text) {
        Set<Integer> out = new HashSet<>();
        for (int g = 0; g < GROUPS.length; g++) {
            for (String phrase : GROUPS[g]) {
                if (containsPhrase(text, phrase)) {
                    out.add(g);
                    break;
                }
            }
        }
        return out;
    }

    static double score(String query, String label, String words) {
        if (query.isEmpty()) return 0;
        String hay = (label + " " + words).trim();
        if (hay.isEmpty()) return 0;
        if (label.equals(query)) return 1.0;
        double best = 0;
        // The goal names the control: "tap voice mode" -> "Start voice mode".
        if (containsPhrase(label, query)) best = Math.max(best, 0.88 + 0.12 * query.length() / Math.max(query.length(), label.length()));
        else if (containsPhrase(hay, query)) best = Math.max(best, 0.8);
        // The control names itself inside the goal: "copy that answer" -> "Copy".
        if (!label.isEmpty() && label.split(" ").length <= 3 && containsPhrase(query, label)) best = Math.max(best, 0.82);
        // Same concept, other words: "menu" -> "Open sidebar" / "More options".
        Set<Integer> want = groupsOf(query);
        if (!want.isEmpty()) {
            Set<Integer> have = groupsOf(hay);
            have.retainAll(want);
            if (!have.isEmpty()) best = Math.max(best, 0.8);
        }
        Set<String> q = new HashSet<>(Arrays.asList(query.split(" ")));
        Set<String> h = new HashSet<>(Arrays.asList(hay.split(" ")));
        int common = 0;
        for (String word : q) if (h.contains(word)) common++;
        if (common > 0) {
            double recall = (double) common / q.size();
            double precision = (double) common / h.size();
            best = Math.max(best, 0.55 * recall + 0.2 * precision);
        }
        return best;
    }

    public static Choice choose(Z0LiveScreen screen, String goal) {
        String query = query(goal);
        Map<Integer, Double> byTarget = new HashMap<>();
        for (int i = 0; i < screen.nodes.size(); i++) {
            Z0LiveScreen.Node node = screen.nodes.get(i);
            if (node.password) continue;
            String label = normalize(node.label);
            String words = normalize(Z0LiveScreen.idWords(node.viewId));
            double s = score(query, label, words);
            if (s <= 0) continue;
            int target = node.editable || node.scrollable ? i : screen.clickTarget(i);
            if (target < 0) {
                target = i;
                s *= 0.6; // nothing clickable owns this text: probably a static heading
            }
            if (label.length() > 40) s *= 0.85;
            byTarget.merge(target, s, Math::max);
        }
        List<Candidate> ranked = new ArrayList<>();
        for (Map.Entry<Integer, Double> entry : byTarget.entrySet()) ranked.add(new Candidate(entry.getKey(), entry.getValue()));
        // Higher score first; ties prefer the earlier (top/left-most in tree order) node.
        ranked.sort((a, b) -> a.score != b.score ? Double.compare(b.score, a.score) : Integer.compare(a.index, b.index));
        Choice lexical = new Choice(ranked, query);
        if (lexical.confident()) return lexical;
        // Escalation needs the right answer among the candidates even when no word matched:
        // append the remaining labelled controls (score 0) in screen order.
        List<Candidate> all = new ArrayList<>(ranked);
        for (int i = 0; i < screen.nodes.size() && all.size() < MAX_CANDIDATES; i++) {
            Z0LiveScreen.Node node = screen.nodes.get(i);
            if (node.password || node.label.isEmpty() || byTarget.containsKey(i)) continue;
            if (node.clickable || node.editable || node.longClickable) all.add(new Candidate(i, 0));
        }
        return new Choice(all, query);
    }
}
