package app.lawnchair.zer0.voice.z0live;

import java.util.Locale;

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
