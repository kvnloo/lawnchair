import app.lawnchair.zer0.spaces.SpaceAppSearch;
import app.lawnchair.zer0.spaces.SpaceAppSearch.Entry;
import java.util.ArrayList;
import java.util.List;

public final class SpaceAppSearchTest {
    private static int checked;
    private static void check(boolean pass, String label) {
        if (!pass) throw new AssertionError(label);
        checked++;
    }

    private static Entry app(String name, String id) { return new Entry(id, name); }
    private static List<Entry> candidates() {
        return List.of(
                app("Maps", "google/maps"),
                app("Camera", "samsung/camera"),
                app("Camera Tools", "thirdparty/camera"),
                app("Screen Camera", "other/screencamera"),
                app("Tennis Scores", "tennis/scores"),
                app("CaméRa", "intl/camera"),
                app("My Files", "samsung/files"),
                app("Maps", "work/otherMaps"));
    }
    public static void main(String[] args) {
        List<Entry> apps = candidates();
        List<Entry> result = SpaceAppSearch.filter(apps, "camera");
        check(result.size() == 4, "normal result count");
        check(result.get(0).component.equals("samsung/camera"), "exact first");
        check(result.get(1).component.equals("thirdparty/camera"), "prefix second");
        check(result.get(2).component.equals("other/screencamera"), "word start next");
        check(SpaceAppSearch.filter(apps, "TENNIS").get(0).label.equals("Tennis Scores"), "casefold");
        check(SpaceAppSearch.filter(apps, "thirdparty").size() == 1, "component fallback");
        check(SpaceAppSearch.filter(apps, "").isEmpty(), "no zero-query app reveal");
        check(SpaceAppSearch.filter(apps, null).isEmpty(), "null query");
        check(SpaceAppSearch.filter(apps, "camera\nopen").isEmpty(), "multiline invalid");
        check(SpaceAppSearch.filter(apps, "Ca\u202emera").isEmpty(), "bidi override invalid");
        check(SpaceAppSearch.filter(null, "camera").isEmpty(), "no visible list");
        check(SpaceAppSearch.exactUnique(apps, "camera").component.equals("samsung/camera"),
                "enter unique exact");
        check(SpaceAppSearch.exactUnique(apps, "maps") == null, "duplicate label fails closed");
        check(SpaceAppSearch.exactUnique(apps, "cam") == null, "partial no enter launch");
        check(SpaceAppSearch.exactUnique(apps, "") == null, "empty cannot launch");
        check(SpaceAppSearch.filter(apps, "caméra").get(0).component.equals("intl/camera"),
                "unicode NFC label");
        ArrayList<Entry> many = new ArrayList<>();
        for (int i = 0; i < 20; i++) many.add(app("Test " + i, "p/" + i));
        check(SpaceAppSearch.filter(many, "test").size() == 8, "bounded display");
        check(SpaceAppSearch.filter(List.of(app("Camera", "a/1"), app("Camera", "a/1")),
                "camera").size() == 1, "same component dedup");
        check(SpaceAppSearch.filter(List.of(app("Camera", "first/a"), app("Camera", "second/b")),
                "camera").get(0).component.equals("first/a"), "stable tie break");
        System.out.println("PASS: " + checked + " app palette matcher checks");
    }
}
