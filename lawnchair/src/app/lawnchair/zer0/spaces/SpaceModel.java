package app.lawnchair.zer0.spaces;

import java.text.Normalizer;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Locale;

/**
 * Small, pure-Java catalog for named launcher contexts.
 *
 * <p>Spaces are labels, not Android task/window ownership. Scratch is session-only and
 * never included in the serialized catalog or saved active space.
 */
public final class SpaceModel {
    public static final String SCRATCH = "Scratch";
    public static final int MAX_SPACES = 12;

    private final List<String> names;
    private final String savedActive;
    private final boolean scratchActive;

    private SpaceModel(List<String> names, String savedActive, boolean scratchActive) {
        this.names = Collections.unmodifiableList(new ArrayList<>(names));
        this.savedActive = savedActive;
        this.scratchActive = scratchActive;
    }

    public static SpaceModel restore(String serializedNames, String savedActive) {
        ArrayList<String> names = new ArrayList<>();
        if (serializedNames != null) {
            for (String candidate : serializedNames.split("\n")) {
                String normalized = clean(candidate);
                if (valid(normalized) && find(names, normalized) == null
                        && names.size() < MAX_SPACES) {
                    names.add(normalized);
                }
            }
        }
        if (names.isEmpty()) {
            Collections.addAll(names, "Now", "Build", "Learn", "Life");
        }
        String resolved = find(names, savedActive);
        return new SpaceModel(names, resolved == null ? names.get(0) : resolved, false);
    }

    public List<String> names() {
        return names;
    }

    public String activeName() {
        return scratchActive ? SCRATCH : savedActive;
    }

    public String savedActiveName() {
        return savedActive;
    }

    public boolean isScratchActive() {
        return scratchActive;
    }

    public String encodeNames() {
        return String.join("\n", names);
    }

    public boolean contains(String requested) {
        return SCRATCH.equalsIgnoreCase(clean(requested)) || find(names, requested) != null;
    }

    public SpaceModel activate(String requested) {
        if (SCRATCH.equalsIgnoreCase(clean(requested))) {
            return new SpaceModel(names, savedActive, true);
        }
        String resolved = find(names, requested);
        if (resolved == null) throw new IllegalArgumentException("Unknown space");
        return new SpaceModel(names, resolved, false);
    }

    public SpaceModel add(String requested) {
        String name = clean(requested);
        requireValid(name);
        if (find(names, name) != null) throw new IllegalArgumentException("Duplicate space");
        if (names.size() >= MAX_SPACES) throw new IllegalArgumentException("Space limit reached");
        ArrayList<String> next = new ArrayList<>(names);
        next.add(name);
        return new SpaceModel(next, savedActive, scratchActive);
    }

    public SpaceModel rename(String oldName, String requested) {
        String old = find(names, oldName);
        if (old == null) throw new IllegalArgumentException("Unknown space");
        String name = clean(requested);
        requireValid(name);
        String collision = find(names, name);
        if (collision != null && !collision.equals(old)) {
            throw new IllegalArgumentException("Duplicate space");
        }
        ArrayList<String> next = new ArrayList<>(names);
        next.set(next.indexOf(old), name);
        String active = savedActive.equals(old) ? name : savedActive;
        return new SpaceModel(next, active, scratchActive);
    }

    public SpaceModel remove(String requested) {
        String existing = find(names, requested);
        if (existing == null) throw new IllegalArgumentException("Unknown space");
        if (names.size() == 1) throw new IllegalArgumentException("Keep at least one space");
        ArrayList<String> next = new ArrayList<>(names);
        next.remove(existing);
        String active = savedActive.equals(existing) ? next.get(0) : savedActive;
        return new SpaceModel(next, active, scratchActive);
    }

    private static void requireValid(String value) {
        if (!valid(value)) {
            throw new IllegalArgumentException("Use 1-24 visible characters; Scratch is reserved");
        }
    }

    private static boolean valid(String value) {
        if (value.isEmpty() || value.codePointCount(0, value.length()) > 24
                || SCRATCH.equalsIgnoreCase(value)) return false;
        for (int i = 0; i < value.length();) {
            int cp = value.codePointAt(i);
            if (Character.isISOControl(cp) || Character.getType(cp) == Character.FORMAT) {
                return false;
            }
            i += Character.charCount(cp);
        }
        return true;
    }

    private static String clean(String value) {
        if (value == null) return "";
        return Normalizer.normalize(value, Normalizer.Form.NFKC)
                .trim().replaceAll("\\s+", " ");
    }

    private static String find(List<String> names, String requested) {
        String needle = clean(requested).toLowerCase(Locale.ROOT);
        for (String name : names) {
            if (name.toLowerCase(Locale.ROOT).equals(needle)) return name;
        }
        return null;
    }
}
