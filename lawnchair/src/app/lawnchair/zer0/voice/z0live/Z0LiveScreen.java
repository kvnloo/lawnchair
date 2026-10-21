package app.lawnchair.zer0.voice.z0live;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import org.json.JSONArray;
import org.json.JSONObject;

/**
 * Android-free compact screen snapshot built from the accessibility tree the service already
 * holds in-process. Each kept node gets a short id that is stable across re-reads of the same
 * screen (package + view id + class + label + ordinal), so the broker can act by id after the
 * app re-renders, and the phone can re-find the node when its AccessibilityNodeInfo went stale.
 */
public final class Z0LiveScreen {
    public static final int MAX_NODES = 160;

    /** One kept node. {@code parent} is the index of the nearest kept ancestor, or -1. */
    public static final class Node {
        public String id = "";
        public int parent = -1;
        public String label = "";
        public String role = "view";
        public String viewId = "";
        public String className = "";
        public String pkg = "";
        public int left, top, right, bottom;
        public boolean clickable, longClickable, editable, scrollable, checkable, checked, password, selected;

        public boolean actionable() { return clickable || longClickable || editable || scrollable; }

        public int centerX() { return (left + right) / 2; }

        public int centerY() { return (top + bottom) / 2; }
    }

    public final List<Node> nodes;
    public final String pkg;
    public final String hash;

    public Z0LiveScreen(String pkg, List<Node> nodes) {
        this.pkg = pkg == null ? "" : pkg;
        this.nodes = nodes;
        inheritLabels(nodes);
        assignIds(nodes);
        this.hash = signature(this.pkg, nodes);
    }

    public int indexOf(String id) {
        if (id == null) return -1;
        for (int i = 0; i < nodes.size(); i++) if (nodes.get(i).id.equals(id)) return i;
        return -1;
    }

    /** Text-ish role a model can read: button, edit, toggle, list, text, image, tab. */
    public static String role(String className, boolean clickable, boolean editable, boolean scrollable, boolean checkable) {
        String c = className == null ? "" : className.toLowerCase(Locale.ROOT);
        if (editable || c.endsWith("edittext")) return "edit";
        if (checkable || c.endsWith("switch") || c.endsWith("checkbox") || c.endsWith("togglebutton") || c.endsWith("radiobutton")) return "toggle";
        if (scrollable || c.contains("recyclerview") || c.contains("listview") || c.contains("scrollview") || c.contains("viewpager")) return "list";
        if (c.endsWith("tab") || c.contains("tabview")) return "tab";
        if (c.endsWith("imagebutton") || c.endsWith("button") || clickable) return "button";
        if (c.endsWith("imageview")) return "image";
        return "text";
    }

    /** Human words from a resource id: "com.x:id/open_drawer_button" -> "open drawer button". */
    public static String idWords(String viewId) {
        if (viewId == null || viewId.isEmpty()) return "";
        String tail = viewId.substring(viewId.lastIndexOf('/') + 1);
        StringBuilder out = new StringBuilder();
        for (int i = 0; i < tail.length(); i++) {
            char ch = tail.charAt(i);
            if (ch == '_' || ch == '-' || ch == '.') { out.append(' '); continue; }
            if (Character.isUpperCase(ch) && i > 0 && Character.isLowerCase(tail.charAt(i - 1))) out.append(' ');
            out.append(Character.toLowerCase(ch));
        }
        return out.toString().trim().replaceAll("\\s+", " ");
    }

    /** An unlabelled clickable row takes its first labelled descendant's text ("Connections"). */
    static void inheritLabels(List<Node> nodes) {
        for (Node node : nodes) {
            int p = node.parent;
            if (p < 0 || p >= nodes.size() || node.label.isEmpty()) continue;
            Node parent = nodes.get(p);
            if (parent.label.isEmpty() && (parent.clickable || parent.longClickable) && !parent.editable) parent.label = node.label;
        }
    }

    static void assignIds(List<Node> nodes) {
        Map<String, Integer> seen = new HashMap<>();
        for (Node node : nodes) {
            String key = node.pkg + "|" + node.viewId + "|" + node.className + "|" + node.label;
            int ordinal = seen.merge(key, 1, Integer::sum) - 1;
            node.id = shortHash(key + "#" + ordinal);
        }
    }

    static String shortHash(String value) {
        // FNV-1a 32-bit, rendered base36: 5-7 chars, cheap and stable across processes.
        int h = 0x811c9dc5;
        for (int i = 0; i < value.length(); i++) {
            h ^= value.charAt(i);
            h *= 0x01000193;
        }
        return Long.toString(h & 0xffffffffL, 36);
    }

    static String signature(String pkg, List<Node> nodes) {
        StringBuilder out = new StringBuilder(pkg);
        for (Node node : nodes) {
            out.append('|').append(node.id).append(node.checked ? '1' : '0').append(node.selected ? '1' : '0')
                    .append(node.top / 24).append(',').append(node.left / 24);
        }
        return shortHash(out.toString()) + Integer.toString(nodes.size(), 36);
    }

    /** Clickable target for node i: itself, else its nearest clickable kept ancestor, else -1. */
    public int clickTarget(int index) {
        for (int i = index, hops = 0; i >= 0 && hops < 12; i = nodes.get(i).parent, hops++) {
            if (nodes.get(i).clickable) return i;
        }
        return -1;
    }

    /** All labels on screen (for the destructive-screen check). */
    public String screenText() {
        StringBuilder out = new StringBuilder();
        for (Node node : nodes) if (!node.editable && !node.label.isEmpty()) out.append(node.label).append(' ');
        return out.toString();
    }

    /** Compact JSON for a model: [{id, l, r, ...flags}] with labelled or actionable nodes only. */
    public JSONArray toJson(int limit) {
        JSONArray out = new JSONArray();
        try {
            for (Node node : nodes) {
                if (out.length() >= limit) break;
                if (node.password) continue;
                if (node.label.isEmpty() && !node.clickable && !node.editable && !node.scrollable) continue;
                // An editable node's label is its hint or description: capture never reads its value.
                JSONObject entry = new JSONObject().put("id", node.id).put("l", node.label).put("r", node.role);
                // Resource-id words often name what an opaque label hides ("menu button").
                String words = idWords(node.viewId);
                if (!words.isEmpty() && !node.label.toLowerCase(Locale.ROOT).contains(words)) entry.put("v", words.length() > 40 ? words.substring(0, 40) : words);
                if (node.editable) entry.put("edit", true);
                if (node.scrollable) entry.put("scroll", true);
                if (node.checkable) entry.put("on", node.checked);
                if (!node.clickable && !node.editable && !node.scrollable) entry.put("static", true);
                out.put(entry);
            }
        } catch (org.json.JSONException impossible) {
            throw new IllegalStateException(impossible);
        }
        return out;
    }

    /** Labels that appeared since {@code before}: a cheap, speakable effect summary. */
    public List<String> appeared(Z0LiveScreen before, int limit) {
        List<String> out = new ArrayList<>();
        java.util.Set<String> old = new java.util.HashSet<>();
        if (before != null) for (Node node : before.nodes) old.add(node.label);
        for (Node node : nodes) {
            if (out.size() >= limit) break;
            if (!node.label.isEmpty() && !node.editable && !node.password && !old.contains(node.label) && node.label.length() <= 60) out.add(node.label);
        }
        return out;
    }
}
