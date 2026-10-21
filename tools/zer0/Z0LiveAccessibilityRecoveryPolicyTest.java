package app.lawnchair.zer0.voice.z0live;

public final class Z0LiveAccessibilityRecoveryPolicyTest {
    public static void main(String[] args) {
        int cases = 0;
        for (boolean opted : new boolean[]{false, true}) {
            for (boolean enabled : new boolean[]{false, true}) {
                for (boolean grant : new boolean[]{false, true}) {
                    Z0LiveAccessibilityRecoveryPolicy.Action expected = !opted || enabled
                            ? Z0LiveAccessibilityRecoveryPolicy.Action.NONE
                            : grant ? Z0LiveAccessibilityRecoveryPolicy.Action.RESTORE : Z0LiveAccessibilityRecoveryPolicy.Action.NOTIFY;
                    if (Z0LiveAccessibilityRecoveryPolicy.decide(opted, enabled, grant) != expected) throw new AssertionError();
                    cases++;
                }
            }
        }
        System.out.println("Accessibility recovery: " + cases + " admission cases passed (OFF, enabled, grant, fallback)");
    }
}
