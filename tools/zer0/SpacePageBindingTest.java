import app.lawnchair.zer0.spaces.SpacePageBinding;

/** Pure JVM preflight; actual Launcher3 page behavior still requires device proof. */
public final class SpacePageBindingTest {
    private static int checked;
    private static void check(boolean result, String name) {
        if (!result) throw new AssertionError(name);
        checked++;
    }
    public static void main(String[] args) {
        long now = 250_000;
        check(SpacePageBinding.canNavigate(9, now - 100, now), "valid stable screen ID");
        check(SpacePageBinding.canNavigate(0, now - 100, now), "screen ID zero valid");
        check(!SpacePageBinding.canNavigate(-1, now - 100, now), "negative screen ID");
        check(!SpacePageBinding.canNavigate(9, 0, now), "missing timestamp");
        check(!SpacePageBinding.canNavigate(9, now + 100, now), "future time / reboot");
        check(SpacePageBinding.canNavigate(9, now - 10_000, now), "boundary allowed");
        check(!SpacePageBinding.canNavigate(9, now - 10_001, now), "stale intent rejected");
        check(!SpacePageBinding.canNavigate(9, 100_000, 100), "cross-reboot no replay");
        System.out.println("PASS: " + checked + " home screen binding checks");
    }
}
