package app.lawnchair.zer0.spaces;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * A small, deterministic set of explicit personal-profile launcher components per named space.
 *
 * <p>This stores identifiers, not Android permissions or task state. Callers MUST recheck the
 * installed personal-profile component and hidden-app policy before display or execution.
 */
public final class SpacePins {
    public static final int MAX_PINS = 8;
    private static final int MAX_COMPONENT_CHARS = 300;

    private SpacePins() {}

    public static List<String> decode(String encoded) {
        ArrayList<String> pins = new ArrayList<>();
        if (encoded != null) {
            for (String id : encoded.split("\n")) {
                if (isComponent(id) && !pins.contains(id) && pins.size() < MAX_PINS) {
                    pins.add(id);
                }
            }
        }
        return Collections.unmodifiableList(pins);
    }

    public static String encode(List<String> pins) {
        if (pins == null || pins.size() > MAX_PINS) {
            throw new IllegalArgumentException("Too many pinned apps");
        }
        ArrayList<String> valid = new ArrayList<>();
        for (String id : pins) {
            requireComponent(id);
            if (valid.contains(id)) throw new IllegalArgumentException("Duplicate pin");
            valid.add(id);
        }
        return String.join("\n", valid);
    }

    public static List<String> add(List<String> pins, String component) {
        requireComponent(component);
        if (pins == null) throw new IllegalArgumentException("Invalid list");
        ArrayList<String> next = new ArrayList<>(pins);
        if (next.contains(component)) return Collections.unmodifiableList(next);
        if (next.size() >= MAX_PINS) throw new IllegalArgumentException("Pin limit reached");
        next.add(component);
        encode(next); // Enforce validity even when a caller passes a synthetic list.
        return Collections.unmodifiableList(next);
    }

    public static List<String> remove(List<String> pins, String component) {
        if (pins == null) throw new IllegalArgumentException("Invalid list");
        ArrayList<String> next = new ArrayList<>(pins);
        next.remove(component);
        encode(next);
        return Collections.unmodifiableList(next);
    }

    private static void requireComponent(String id) {
        if (!isComponent(id)) throw new IllegalArgumentException("Invalid component");
    }

    private static boolean isComponent(String id) {
        if (id == null || id.isEmpty() || id.length() > MAX_COMPONENT_CHARS) return false;
        int slash = id.indexOf('/');
        if (slash < 1 || slash == id.length() - 1 || id.lastIndexOf('/') != slash) return false;
        for (int i = 0; i < id.length(); i++) {
            char c = id.charAt(i);
            if (Character.isWhitespace(c) || Character.isISOControl(c)) return false;
        }
        return true;
    }
}
