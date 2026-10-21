package app.lawnchair.zer0.voice.z0live;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import org.json.JSONArray;
import org.json.JSONObject;

/**
 * Runs the real tier-0 matcher, snapshot ids and consequence policy on the shared labelled set
 * (tools/zer0/fixtures/z0live-control-cases.json). Rule: a confident pick must be the right one;
 * otherwise the case escalates with the right answer among the candidates.
 */
public final class Z0LiveControlTest {
    public static void main(String[] args) throws Exception {
        JSONObject data = new JSONObject(Files.readString(Path.of(args[0]), StandardCharsets.UTF_8));
        JSONObject screens = data.getJSONObject("screens");
        JSONArray cases = data.getJSONArray("cases");
        int acted = 0, escalated = 0, wrong = 0, missed = 0, confirmGate = 0;
        long totalNanos = 0;
        for (int i = 0; i < cases.length(); i++) {
            JSONObject c = cases.getJSONObject(i);
            Z0LiveScreen screen = build(screens.getJSONObject(c.getString("screen")));
            String expect = c.isNull("expect") ? null : c.getString("expect");
            long t0 = System.nanoTime();
            Z0LiveTargetMatcher.Choice choice = Z0LiveTargetMatcher.choose(screen, c.getString("goal"));
            totalNanos += System.nanoTime() - t0;
            String best = choice.best() < 0 ? null : screen.nodes.get(choice.best()).label;
            List<String> top = new ArrayList<>();
            for (int k = 0; k < Math.min(16, choice.ranked.size()); k++) top.add(screen.nodes.get(choice.ranked.get(k).index).label);
            String verdict;
            if (choice.confident()) {
                if (expect != null && expect.equals(best)) { acted++; verdict = "act"; }
                else { wrong++; verdict = "WRONG"; }
            } else if (expect == null || top.contains(expect)) { escalated++; verdict = "escalate"; }
            else { missed++; verdict = "MISS"; }
            if (c.optBoolean("consequential") && expect != null) {
                Z0LiveScreen.Node node = find(screen, expect);
                String reason = Z0LiveAccessibilityPolicy.consequential(node.label, node.checkable, screen.pkg, screen.screenText());
                if (reason == null) throw new AssertionError("consequential target not gated: " + expect);
                confirmGate++;
            }
            System.out.printf("%-9s %-17s %-40s -> %-28s conf=%.2f margin=%.2f%n", verdict, c.getString("screen"),
                    '"' + c.getString("goal") + '"', best, choice.confidence, choice.margin);
        }
        System.out.printf("tier0 lexical: cases=%d acted_correct=%d escalated=%d wrong_confident=%d missed=%d confirm_gated=%d mean_us=%.1f%n",
                cases.length(), acted, escalated, wrong, missed, confirmGate, totalNanos / 1000.0 / cases.length());
        if (wrong > 0) throw new AssertionError("tier 0 acted on a wrong target");
        if (missed > 0) throw new AssertionError("tier 0 lost the right target from its candidates");
        if (acted < cases.length() / 2) throw new AssertionError("tier 0 should settle most cases on the phone");

        // Stable ids: re-reading the same screen yields the same ids; an extra node elsewhere keeps them.
        JSONObject chat = screens.getJSONObject("chatgpt_home");
        Z0LiveScreen a = build(chat), b = build(chat);
        if (!a.hash.equals(b.hash) || !a.nodes.get(1).id.equals(b.nodes.get(1).id)) throw new AssertionError("ids not stable");
        JSONObject grown = new JSONObject(chat.toString());
        grown.getJSONArray("nodes").put(new JSONObject().put("cls", "android.widget.TextView").put("label", "Toast").put("parent", 0));
        Z0LiveScreen c2 = build(grown);
        if (!c2.nodes.get(1).id.equals(a.nodes.get(1).id) || c2.hash.equals(a.hash)) throw new AssertionError("id must survive, hash must change");
        // Ancestor click: a static label inside a clickable row resolves to the row.
        Z0LiveScreen settings = build(screens.getJSONObject("settings_main"));
        int label = indexOf(settings, "Wi-Fi  •  Bluetooth  •  SIM manager");
        if (settings.clickTarget(label) != 1) throw new AssertionError("ancestor click target");
        if (!settings.nodes.get(1).label.equals("Connections")) throw new AssertionError("row label inherited");
        // Editable values and password fields never leave the phone.
        Z0LiveScreen.Node secret = new Z0LiveScreen.Node();
        secret.password = true; secret.editable = true; secret.label = ""; secret.className = "android.widget.EditText";
        Z0LiveScreen.Node field = new Z0LiveScreen.Node();
        field.editable = true; field.label = "Ask anything"; field.className = "android.widget.EditText";
        JSONArray json = new Z0LiveScreen("x", new ArrayList<>(Arrays.asList(secret, field))).toJson(10);
        // Capture sets an editable node's label from its hint only; password nodes never appear.
        if (json.length() != 1 || !json.getJSONObject(0).getString("l").equals("Ask anything")) throw new AssertionError("editable/password: " + json);
        if (Z0LiveAccessibilityPolicy.consequential("Open sidebar", false, "com.openai.chatgpt", "") != null) throw new AssertionError("routine tap gated");
        if (Z0LiveAccessibilityPolicy.consequential("Continue", false, "x", "Erase all data? This cannot be undone") == null) throw new AssertionError("bland proceed on destructive screen");
        if (Z0LiveAccessibilityPolicy.consequential("Don't allow", false, "com.android.permissioncontroller", "") != null) throw new AssertionError("declining is safe");
        if (!Z0LiveAccessibilityPolicy.secretField("Enter PIN", "") || Z0LiveAccessibilityPolicy.secretField("Ask anything", "")) throw new AssertionError("secret field");
        // Icon-font glyphs (React Native Ionicons) are not labels.
        if (!Z0LiveScreen.readable("\uf19a").isEmpty() || !Z0LiveScreen.readable(" Send \uf114").equals("Send"))
            throw new AssertionError("icon glyphs stripped");
        System.out.println("PASS stable ids, ancestor click target, inherited row labels, no editable/password values, icon glyphs dropped, phone_goal denylist gates");
    }

    static Z0LiveScreen.Node find(Z0LiveScreen screen, String label) {
        return screen.nodes.get(indexOf(screen, label));
    }

    static int indexOf(Z0LiveScreen screen, String label) {
        for (int i = 0; i < screen.nodes.size(); i++) if (screen.nodes.get(i).label.equals(label)) return i;
        throw new AssertionError("no node " + label);
    }

    static Z0LiveScreen build(JSONObject spec) {
        List<Z0LiveScreen.Node> nodes = new ArrayList<>();
        JSONArray raw = spec.getJSONArray("nodes");
        for (int i = 0; i < raw.length(); i++) {
            JSONObject r = raw.getJSONObject(i);
            Z0LiveScreen.Node n = new Z0LiveScreen.Node();
            n.className = r.optString("cls");
            n.label = r.optString("label");
            n.viewId = r.optString("viewId");
            n.pkg = spec.getString("package");
            n.parent = r.optInt("parent", -1);
            n.clickable = r.optBoolean("clickable");
            n.longClickable = r.optBoolean("longClickable");
            n.editable = r.optBoolean("editable");
            n.scrollable = r.optBoolean("scrollable");
            n.checkable = r.optBoolean("checkable");
            JSONArray b = r.optJSONArray("bounds");
            if (b != null) { n.left = b.getInt(0); n.top = b.getInt(1); n.right = b.getInt(2); n.bottom = b.getInt(3); }
            n.role = Z0LiveScreen.role(n.className, n.clickable, n.editable, n.scrollable, n.checkable);
            nodes.add(n);
        }
        return new Z0LiveScreen(spec.getString("package"), nodes);
    }
}
