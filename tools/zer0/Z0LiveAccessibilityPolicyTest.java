import java.lang.reflect.Method;

/** Exercises the public policy boundary without an Android runtime. */
public final class Z0LiveAccessibilityPolicyTest {
    public static void main(String[] args) throws Exception {
        Class<?> policy;
        try {
            policy = Class.forName("app.lawnchair.zer0.voice.z0live.Z0LiveAccessibilityPolicy");
        } catch (ClassNotFoundException missing) {
            throw new AssertionError("Disabled in-app control must return permission_required through the real accessibility policy", missing);
        }
        Method admission = policy.getMethod("admission", boolean.class, boolean.class, String.class, int.class, String.class, int.class);
        check("permission_required", admission.invoke(null, false, false, "org.example.notes", 7, "org.example.notes", 7));
        check("permission_required", admission.invoke(null, true, false, "org.example.notes", 7, "org.example.notes", 7));
        check("blocked_app", admission.invoke(null, true, true, "com.bank.mobile", 7, "com.bank.mobile", 7));
        check("blocked_app", admission.invoke(null, true, true, "com.chase.sig.android", 7, "com.chase.sig.android", 7));
        check("blocked_app", admission.invoke(null, true, true, "com.android.phone", 7, "com.android.phone", 7));
        check("stale_window", admission.invoke(null, true, true, "org.example.notes", 7, "org.example.browser", 7));
        check("stale_window", admission.invoke(null, true, true, "org.example.notes", 7, "org.example.notes", 8));
        check("allowed", admission.invoke(null, true, true, "org.example.notes", 7, "org.example.notes", 7));
        Method select = policy.getMethod("select", String[].class, String[].class, boolean[].class, String.class, String.class);
        String[] labels = {"Save", "Save draft", "Save"};
        String[] ids = {"org.example.notes:id/save", "org.example.notes:id/draft", "org.example.notes:id/other"};
        boolean[] safe = {true, true, true};
        check(-2, select.invoke(null, labels, ids, safe, "text", "Save"));
        check(1, select.invoke(null, labels, ids, safe, "text", "Save draft"));
        check(0, select.invoke(null, labels, ids, safe, "id", "org.example.notes:id/save"));
        check(-1, select.invoke(null, labels, ids, safe, "text", "Sav"));
        check(-3, select.invoke(null, labels, ids, new boolean[] {false, true, true}, "id", "org.example.notes:id/save"));
        Method sensitive = policy.getMethod("sensitiveTarget", String.class, String.class);
        check(true, sensitive.invoke(null, "Send", "org.example.browser:id/button"));
        check(true, sensitive.invoke(null, "Sign in", "org.example.browser:id/login"));
        check(true, sensitive.invoke(null, "", "org.example.browser:id/password"));
        check(false, sensitive.invoke(null, "Save draft", "org.example.notes:id/save"));
        Method password;
        try {
            password = policy.getMethod("passwordField", boolean.class, int.class);
        } catch (NoSuchMethodException missing) {
            throw new AssertionError("Password windows must be rejected even when only the input type marks the field", missing);
        }
        check(true, password.invoke(null, true, 1));
        check(true, password.invoke(null, false, 0x81));
        check(true, password.invoke(null, false, 0x91));
        check(true, password.invoke(null, false, 0xe1));
        check(true, password.invoke(null, false, 0x12));
        check(true, password.invoke(null, false, 0x80081));
        check(false, password.invoke(null, false, 1));
        System.out.println("PASS password rejection including numeric, web, visible and flagged input types");
        System.out.println("PASS exact text/id selection, ambiguity, unsafe targets and no-auto-send");
        System.out.println("PASS accessibility admission: disabled, consent, blocked apps, package and window matching");
    }

    static void check(Object expected, Object actual) {
        if (!expected.equals(actual)) throw new AssertionError("expected " + expected + ", got " + actual);
    }
}
