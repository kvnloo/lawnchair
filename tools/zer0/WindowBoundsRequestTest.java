import app.lawnchair.zer0.spaces.WindowBoundsRequest;
import java.util.Arrays;

/** Platform-independent geometry tests; never evidence of a real freeform window. */
public final class WindowBoundsRequestTest {
    private static int checks;

    private static void check(boolean ok, String label) {
        if (!ok) throw new AssertionError(label);
        checks++;
    }

    private static void rejects(Runnable action, String label) {
        try {
            action.run();
            throw new AssertionError("Expected bounds rejection: " + label);
        } catch (IllegalArgumentException expected) {
            checks++;
        }
    }

    public static void main(String[] args) {
        int[] phone = WindowBoundsRequest.centered(0, 0, 1080, 2400);
        check(Arrays.equals(phone, new int[] {151, 420, 928, 1980}), "phone rectangle");
        check(WindowBoundsRequest.boundsCsv(phone).equals("151,420,928,1980"), "receipt geometry");
        int[] desktop = WindowBoundsRequest.centered(0, 0, 1920, 1080);
        check(Arrays.equals(desktop, new int[] {269, 189, 1651, 891}), "landscape");
        int[] inset = WindowBoundsRequest.centered(100, 200, 1180, 2600);
        check(Arrays.equals(inset, new int[] {251, 620, 1028, 2180}), "non-zero display origin");
        check(WindowBoundsRequest.centered(0, 0, 200, 500) == null, "too narrow");
        check(WindowBoundsRequest.centered(1000, 0, 999, 2000) == null, "inverted");
        check(WindowBoundsRequest.centered(Integer.MIN_VALUE, 0, Integer.MAX_VALUE, 1000)
                == null, "overflow/bogus display size");
        check(!WindowBoundsRequest.eligible(29, true, phone), "require window metrics API");
        check(!WindowBoundsRequest.eligible(37, false, phone), "feature gate");
        check(!WindowBoundsRequest.eligible(37, true, null), "bounds gate");
        check(WindowBoundsRequest.eligible(37, true, phone), "best effort admitted");
        rejects(() -> WindowBoundsRequest.boundsCsv(null), "null");
        rejects(() -> WindowBoundsRequest.boundsCsv(new int[] {-1, 0, 500, 500}), "negative");
        rejects(() -> WindowBoundsRequest.boundsCsv(new int[] {0, 0, 0, 500}), "zero width");
        System.out.println("PASS: " + checks + " popup bounds geometry checks");
    }
}
