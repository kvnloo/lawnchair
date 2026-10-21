package app.lawnchair.zer0.voice.z0live;

import java.util.Locale;
import java.util.regex.Pattern;

/** Android-free admission policy shared by UI inspection and actions. */
public final class Z0LiveAccessibilityPolicy {
    private Z0LiveAccessibilityPolicy() {}

    public static String admission(boolean enabled, boolean consent, String expectedPackage,
            int expectedWindow, String actualPackage, int actualWindow) {
        if (!enabled || !consent) return "permission_required";
        if (blockedPackage(actualPackage)) return "blocked_app";
        if (expectedPackage == null || !expectedPackage.equals(actualPackage)
                || expectedWindow < 0 || expectedWindow != actualWindow) return "stale_window";
        return "allowed";
    }

    /** -1 absent, -2 ambiguous, -3 unsafe. Never chooses an ancestor or substring. */
    public static int select(String[] text, String[] ids, boolean[] safe, String by, String target) {
        if (target == null || target.isBlank() || !("text".equals(by) || "id".equals(by))) return -1;
        int match = -1;
        for (int i = 0; i < text.length; i++) {
            if (!target.equals("id".equals(by) ? ids[i] : text[i])) continue;
            if (match >= 0) return -2;
            match = i;
        }
        return match >= 0 && !safe[match] ? -3 : match;
    }

    public static boolean sensitiveTarget(String text, String id) {
        String value = ((text == null ? "" : text) + " " + (id == null ? "" : id)).toLowerCase(Locale.ROOT);
        return value.matches("(?s).*(password|passwd|credential|username|user_name|login|log.in|sign.in|sign.up|one.time|otp|pin|verification|security.code|send|submit|call|dial|pay|purchase|buy|checkout|transfer|confirm|delete|remove|erase|uninstall|allow|enable|grant).*");
    }

    // Same denylist as the phone_goal plugin (goalkit/safety.py): these taps need a spoken yes.
    private static final String[] DENY_WORDS = {
        "send", "pay", "pay now", "buy", "buy now", "purchase", "place order", "order now", "checkout", "check out",
        "subscribe", "unsubscribe", "donate", "transfer", "submit", "post", "publish", "share", "tweet", "reply all",
        "delete", "delete all", "remove", "uninstall", "reset", "factory", "factory data reset", "erase", "wipe",
        "format", "clear data", "clear storage", "clear cache", "force stop", "disable", "turn off", "power off",
        "restart", "reboot", "sign out", "log out", "logout", "remove account",
        "confirm", "install", "update", "download and install", "install now", "update now",
        "allow", "always allow", "allow all", "while using the app", "only this time", "grant", "accept", "agree",
        "i agree", "block", "report", "call", "dial", "video call", "end call", "archive", "move to trash", "trash",
        "discard", "overwrite", "replace", "save password", "enable",
    };
    private static final Pattern[] DENY;
    static {
        String[] sorted = DENY_WORDS.clone();
        java.util.Arrays.sort(sorted, (a, b) -> b.length() - a.length());
        DENY = new Pattern[sorted.length];
        for (int i = 0; i < sorted.length; i++) {
            DENY[i] = Pattern.compile("(?i)(?<![A-Za-z])" + Pattern.quote(sorted[i]).replace(" ", "\\E\\s+\\Q") + "(?![A-Za-z])");
        }
    }
    private static final Pattern DESTRUCTIVE_SCREEN = Pattern.compile(
            "(?i)\\b(delete|erase|reset|uninstall|remove|factory|wipe|format|clear (?:data|storage)|sign out|log out|"
            + "permanently|cannot be undone|can't be undone|payment|purchase|charge|subscription|transfer|send)\\b");
    private static final Pattern PERMISSION_TEXT = Pattern.compile("(?i)\\ballow\\b.{1,60}\\bto\\b.{1,100}\\?");
    private static final java.util.List<String> BLAND_PROCEED = java.util.Arrays.asList(
            "continue", "ok", "okay", "yes", "next", "proceed", "done", "got it", "apply", "save", "agree");
    private static final java.util.List<String> SAFE_ON_PERMISSION = java.util.Arrays.asList(
            "don't allow", "deny", "cancel", "not now", "no thanks");

    /** Why tapping this target needs the owner's spoken yes, or null for routine navigation. */
    public static String consequential(String label, boolean checkable, String pkg, String screenText) {
        String head = label == null ? "" : label.trim();
        String norm = head.toLowerCase(Locale.ROOT).replaceAll("\\s+", " ");
        if (SAFE_ON_PERMISSION.contains(norm)) return null;
        String screen = screenText == null ? "" : screenText;
        if (pkg != null && (pkg.contains("permissioncontroller") || pkg.contains("packageinstaller") || pkg.equals("com.android.vending"))) {
            return "permission or install dialog button '" + head + "'";
        }
        if (PERMISSION_TEXT.matcher(screen).find() && head.length() <= 40 && BLAND_PROCEED.contains(norm)) {
            return "permission dialog button '" + head + "'";
        }
        for (Pattern word : DENY) if (word.matcher(head).find()) return "'" + head + "' can send, pay, delete, install or grant";
        if (checkable) return "'" + head + "' toggles a setting";
        java.util.regex.Matcher destructive = DESTRUCTIVE_SCREEN.matcher(screen);
        if (BLAND_PROCEED.contains(norm) && destructive.find()) return "'" + head + "' on a screen that mentions " + destructive.group(1);
        return null;
    }

    private static final Pattern SECRET_FIELD = Pattern.compile("(?i)(pass(word|code|phrase)?|(?<![a-z])pin(?![a-z])|(?<![a-z])otp(?![a-z])|one[- _]?time|"
            + "2fa|two[- _]factor|verification|auth[_ ]?code|security[_ ]?code|(?<![a-z])cv[vc](?![a-z])|card[_ ]?number|(?<![a-z])ssn(?![a-z])|secret|token)");

    /** A field z0live must never type into (same shape as phone_goal's secret_field). */
    public static boolean secretField(String label, String viewId) {
        return SECRET_FIELD.matcher((label == null ? "" : label) + " " + (viewId == null ? "" : viewId)).find();
    }

    public static boolean passwordField(boolean password, int inputType) {
        int variation = inputType & 0xfff;
        return password || variation == 0x81 || variation == 0x91 || variation == 0xe1 || variation == 0x12;
    }

    public static boolean blockedPackage(String name) {
        if (name == null || name.isBlank()) return true;
        String value = name.toLowerCase(Locale.ROOT);
        for (String part : new String[] {"bank", "wallet", "password", "authenticator", "1password",
                "lastpass", "bitwarden", "keepass", "keeper", "dashlane", "credit", "payment",
                "paypal", "venmo", "dialer", "messaging", "mms", "contacts", "whatsapp",
                "telegram", "signal", "permissioncontroller", "packageinstaller", "chase", "wellsfargo",
                "capitalone", "citi", "usaa", "fidelity", "americanexpress", "schwab", "cashapp",
                "robinhood", "incallui", "telecom"}) {
            if (value.contains(part)) return true;
        }
        return value.equals("com.android.settings") || value.equals("com.android.systemui")
                || value.equals("com.android.phone") || value.equals("com.google.android.gms")
                || value.startsWith("app.lawnchair") || value.equals("com.samsung.android.app.telephonyui");
    }
}
