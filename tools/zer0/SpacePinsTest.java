import app.lawnchair.zer0.spaces.SpacePins;
import java.util.List;

public final class SpacePinsTest {
    private static int checked;
    private static void check(boolean good, String label) {
        if (!good) throw new AssertionError(label);
        checked++;
    }
    private static void rejected(Runnable action, String label) {
        try {
            action.run();
            throw new AssertionError("Expected rejection: " + label);
        } catch (IllegalArgumentException expected) {
            checked++;
        }
    }
    public static void main(String[] args) {
        String camera = "com.android.camera/com.android.camera.Camera";
        String chrome = "com.android.chrome/com.google.android.apps.chrome.Main";
        List<String> empty = SpacePins.decode(null);
        check(empty.isEmpty(), "no default pins");
        List<String> one = SpacePins.add(empty, camera);
        check(one.size() == 1 && empty.isEmpty(), "immutable copy");
        List<String> two = SpacePins.add(one, chrome);
        check(two.size() == 2 && two.get(1).equals(chrome), "stable order");
        check(SpacePins.add(two, camera).size() == 2, "idempotent pin");
        check(SpacePins.decode(SpacePins.encode(two)).equals(two), "round-trip");
        check(SpacePins.decode(camera + "\n" + camera + "\n" + chrome).equals(two),
                "old preference duplicates removed");
        check(SpacePins.decode("notacomponent\n" + camera + "\n").equals(one),
                "damaged preference filtered");
        check(SpacePins.remove(two, camera).equals(List.of(chrome)), "remove only target");
        check(two.size() == 2, "old model unchanged after remove");
        rejected(() -> SpacePins.add(empty, "missing/slash/extra"), "multiple delimiters");
        rejected(() -> SpacePins.add(empty, "bad\napp/id"), "newline injection");
        rejected(() -> SpacePins.add(empty, "notacomponent"), "missing slash");
        rejected(() -> SpacePins.add(empty, "pkg/"), "empty activity");
        rejected(() -> SpacePins.encode(List.of(camera, camera)), "no duplicate persistence");
        List<String> full = SpacePins.decode(
                "a/1\nb/2\nc/3\nd/4\ne/5\nf/6\ng/7\nh/8");
        check(full.size() == 8, "eight pins");
        rejected(() -> SpacePins.add(full, "i/9"), "bounded");
        check(SpacePins.decode("a/1\nb/2\nc/3\nd/4\ne/5\nf/6\ng/7\nh/8\ni/9")
                .size() == 8, "bounded corrupt restoration");
        check(SpacePins.encode(SpacePins.remove(one, camera)).isEmpty(), "clear last pin");
        System.out.println("PASS: " + checked + " space pin checks");
    }
}
