import app.lawnchair.zer0.spaces.SpaceModel;
import app.lawnchair.zer0.spaces.SpacePageBinding;
import app.lawnchair.zer0.spaces.SpacePins;
import java.util.List;

/** Pure Java catalog/preflight matrix; not Android backup, page or profile verification. */
public final class SpacesRestoreMatrixTest {
    private static int checks;

    private static void check(boolean value, String label) {
        if (!value) throw new AssertionError(label);
        checks++;
    }

    private static void rejects(Runnable action, String label) {
        try {
            action.run();
        } catch (IllegalArgumentException expected) {
            checks++;
            return;
        }
        throw new AssertionError("expected rejection: " + label);
    }

    private static void corruptPreferencesAndStableRoundtrip() {
        String raw = "Ｂｕｉｌｄ\nbuild\nLearn\nScratch\nBad\u0001\nNow\nnow";
        SpaceModel model = SpaceModel.restore(raw, "BUILD");
        check(model.names().equals(List.of("Build", "Learn", "Now")),
                "NFKC normalization, duplicate folding and reserved/corrupt filters");
        check(model.savedActiveName().equals("Build"), "saved name normalized");
        String encoded = model.encodeNames();
        SpaceModel roundtrip = SpaceModel.restore(encoded, model.savedActiveName());
        check(roundtrip.names().equals(model.names()), "ordered names round trip");
        check(roundtrip.savedActiveName().equals("Build"), "saved active round trip");
        check(!roundtrip.isScratchActive(), "restore never enters Scratch");
        check(SpaceModel.restore(encoded, "unknown").activeName().equals("Build"),
                "missing saved selection falls back to first");
        check(SpaceModel.restore(null, "Scratch").names().equals(
                List.of("Now", "Build", "Learn", "Life")), "null catalog gets defaults");
        String all = "a\nb\nc\nd\ne\nf\ng\nh\ni\nj\nk\nl\nm";
        SpaceModel capped = SpaceModel.restore(all, "m");
        check(capped.names().size() == SpaceModel.MAX_SPACES,
                "too many restored names truncated deterministically");
        check(!capped.contains("m"), "truncated name cannot become active");
        rejects(() -> capped.add("n"), "max catalog size");
        rejects(() -> model.add("build"), "case-insensitive restored duplicate");
        rejects(() -> model.add("Scratch"), "Scratch is reserved");
    }

    private static void scratchAndRenameDeleteBoundaries() {
        SpaceModel base = SpaceModel.restore("Now\nBuild\nLearn", "Build");
        SpaceModel scratch = base.activate("scratch");
        check(scratch.isScratchActive() && scratch.activeName().equals("Scratch"),
                "Scratch active in memory");
        check(scratch.savedActiveName().equals("Build"), "Scratch keeps previous selection");
        check(scratch.encodeNames().equals(base.encodeNames()),
                "Scratch does not persist in catalog");
        check(!SpaceModel.restore(scratch.encodeNames(), scratch.savedActiveName())
                .isScratchActive(), "Scratch never restored from names/active");
        SpaceModel renamed = scratch.rename("Build", "Engineering");
        check(renamed.isScratchActive() && renamed.savedActiveName().equals("Engineering"),
                "rename migrates saved selection without leaving transient Scratch");
        check(!renamed.contains("Build") && renamed.contains("Engineering"),
                "old catalog name removed on rename");
        SpaceModel afterDelete = renamed.remove("Engineering");
        check(afterDelete.isScratchActive() && afterDelete.savedActiveName().equals("Now"),
                "deleting saved selection moves persistent fallback, not Scratch");
        check(!afterDelete.contains("Engineering"),
                "deleted name is absent from serialization");
        SpaceModel activated = afterDelete.activate("Learn");
        check(!activated.isScratchActive() && activated.savedActiveName().equals("Learn"),
                "named switch leaves Scratch and persists selection");
        check(base.savedActiveName().equals("Build"), "pure model leaves original unchanged");
        rejects(() -> SpaceModel.restore("Solo", "Solo").remove("Solo"),
                "cannot remove final named space");
        rejects(() -> SpaceModel.restore("Now\nBuild", "Now")
                .rename("Now", "build"), "rename collision");
    }

    private static void pinSerializationAndPrivacyLimit() {
        String a = "example.one/.Home";
        String b = "example.two/example.two.Main";
        List<String> pins = SpacePins.decode(a + "\n" + a + "\nbroken\n" + b + "\n");
        check(pins.equals(List.of(a, b)), "corrupt serialized pins deduplicated and filtered");
        check(SpacePins.decode(SpacePins.encode(pins)).equals(pins),
                "valid pins ordered round trip");
        check(SpacePins.add(pins, a).equals(pins), "idempotent pin addition");
        check(SpacePins.remove(pins, a).equals(List.of(b)), "pin removal preserves other pin");
        check(pins.equals(List.of(a, b)), "pin operations return immutable copies");
        String longPins = "a/0\nb/1\nc/2\nd/3\ne/4\nf/5\ng/6\nh/7\ni/8";
        List<String> capped = SpacePins.decode(longPins);
        check(capped.size() == SpacePins.MAX_PINS,
                "restore discards overflow beyond eight");
        rejects(() -> SpacePins.add(capped, "j/9"), "ninth pin rejected");
        rejects(() -> SpacePins.encode(List.of(a, a)), "duplicate persistence rejected");
        rejects(() -> SpacePins.encode(List.of("not-a-component")), "malformed component");
        rejects(() -> SpacePins.add(pins, "invalid\nline/.Main"),
                "control/newline pin injection rejected");
        check(SpacePins.decode(null).isEmpty(), "missing private pin list restores empty");
        // Pure data model cannot consult PackageManager, work profiles or hidden-app policy.
        check(SpacePins.decode("work.package/.Main").size() == 1,
                "syntactically valid unavailable/profile-looking pin remains unverified");
    }

    private static void pageBindingIntentNotPageExistence() {
        long now = 200_000;
        check(SpacePageBinding.canNavigate(0, now, now), "zero screen id accepted");
        check(SpacePageBinding.canNavigate(6,
                now - SpacePageBinding.MAX_REQUEST_AGE_MS, now),
                "last permitted millisecond");
        check(!SpacePageBinding.canNavigate(6,
                now - SpacePageBinding.MAX_REQUEST_AGE_MS - 1, now),
                "expired binding must not navigate");
        check(!SpacePageBinding.canNavigate(SpacePageBinding.NO_SCREEN, now, now),
                "unavailable sentinel rejected");
        check(!SpacePageBinding.canNavigate(1, now + 1, now),
                "future timestamp fails closed");
        check(!SpacePageBinding.canNavigate(1, 0, now),
                "zero timestamp fails closed");
        check(SpacePageBinding.canNavigate(987654, now, now),
                "positive but deleted page ID passes pure preflight: host must verify existence");
    }

    public static void main(String[] args) {
        corruptPreferencesAndStableRoundtrip();
        scratchAndRenameDeleteBoundaries();
        pinSerializationAndPrivacyLimit();
        pageBindingIntentNotPageExistence();
        System.out.println("PASS: " + checks + " pure-Java Spaces restore assertions");
    }
}
