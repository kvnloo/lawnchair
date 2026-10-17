import app.lawnchair.zer0.spaces.SpaceModel;

/** Pure-JVM contract checks; neither a UI test nor proof of Samsung window placement. */
public final class SpaceModelTest {
    private static int checked;

    private static void check(boolean condition, String label) {
        if (!condition) throw new AssertionError(label);
        checked++;
    }

    private static void rejects(Runnable action, String label) {
        try {
            action.run();
            throw new AssertionError("Expected rejection: " + label);
        } catch (IllegalArgumentException expected) {
            checked++;
        }
    }

    public static void main(String[] args) {
        SpaceModel model = SpaceModel.restore(null, null);
        check(model.names().size() == 4 && model.activeName().equals("Now"), "defaults");
        model = model.activate("BUILD");
        check(model.activeName().equals("Build"), "case-insensitive activation");
        check(model.savedActiveName().equals("Build"), "saved selection");
        model = model.add("Research");
        check(model.names().size() == 5 && model.contains("research"), "custom space");
        rejects(() -> SpaceModel.restore(null, null).add("build"), "duplicate");
        final SpaceModel added = model;
        rejects(() -> added.activate("Unknown"), "unknown activation");
        rejects(() -> added.add("Scratch"), "reserved scratch name");
        rejects(() -> added.add("line\nbreak"), "invalid name");
        model = model.rename("research", "Science");
        check(model.contains("SCIENCE") && !model.contains("Research"), "rename");
        model = model.rename("Build", "Engineering");
        check(model.savedActiveName().equals("Engineering"), "active rename");
        model = model.remove("Engineering");
        check(model.savedActiveName().equals("Now"), "fallback on active deletion");
        String data = model.encodeNames();
        SpaceModel restored = SpaceModel.restore(data, model.savedActiveName());
        check(restored.encodeNames().equals(data), "round trip order");
        SpaceModel scratch = restored.activate("scratch");
        check(scratch.isScratchActive() && scratch.activeName().equals("Scratch"), "scratch active");
        check(scratch.encodeNames().equals(data), "scratch not serialized");
        check(scratch.savedActiveName().equals("Now"), "scratch does not overwrite saved active");
        check(!SpaceModel.restore(data, "Scratch").isScratchActive(), "scratch not restored");
        check(SpaceModel.restore("Bad\u0001\nNow\nnow\n", "Now").names().size() == 1,
                "invalid / duplicate serialized names rejected");
        check(SpaceModel.restore("   ", "Unavailable").names().size() == 4,
                "corrupt / empty prefs recover defaults");
        SpaceModel single = SpaceModel.restore("Solo", "Solo");
        rejects(() -> single.remove("Solo"), "cannot remove final space");
        SpaceModel cap = SpaceModel.restore("One\nTwo\nThree\nFour\nFive\nSix\nSeven\nEight\nNine\nTen\nEleven\nTwelve", "One");
        rejects(() -> cap.add("Thirteen"), "bounded count");
        System.out.println("PASS: " + checked + " space model checks");
    }
}
