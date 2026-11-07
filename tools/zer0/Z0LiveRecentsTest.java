package app.lawnchair.zer0.voice.z0live;

import java.io.File;
import java.util.HashMap;
import java.util.Map;
import javax.xml.parsers.DocumentBuilderFactory;
import org.w3c.dom.Element;
import org.w3c.dom.NodeList;

/**
 * No z0live call screen may ever be a Recents entry: an owner quick-switch must never land in a call task
 * (it did twice, via LinkActivity's NEW_TASK relaunch). Args: companion manifest, Lawnchair manifest.
 */
public final class Z0LiveRecentsTest {
    private static final String ANDROID = "http://schemas.android.com/apk/res/android";
    private static final String CALL = "app.lawnchair.zer0.voice.z0live.Z0LiveActivity";
    private static final String LINK = CALL + "$LinkActivity";
    private static final String COMMAND = "app.lawnchair.zer0.voice.z0live.Z0LiveCommandActivity";
    private static final int NEW_TASK = 0x10000000;
    private static final int EXCLUDE_FROM_RECENTS = 0x00800000;
    private static int checks;
    private static void check(boolean value, String message) { if (!value) throw new AssertionError(message); checks++; }

    public static void main(String[] args) throws Exception {
        // Every intent that opens the call screen carries the hide-from-Recents flag, whatever the caller sent.
        check((Z0LiveLaunch.CALL_FLAGS & EXCLUDE_FROM_RECENTS) != 0, "internal call intents exclude the task from Recents");
        check(Z0LiveLaunch.RELAUNCH_FLAGS == (NEW_TASK | EXCLUDE_FROM_RECENTS),
                "LinkActivity relaunch is NEW_TASK | EXCLUDE_FROM_RECENTS (0x10800000), not 0x10000000");

        Map<String, Element> app = activities(args[0]);
        for (String name : new String[] {CALL, LINK, COMMAND}) {
            check(app.containsKey(name), "companion declares " + name);
        }
        for (Map.Entry<String, Element> e : app.entrySet()) {
            check("true".equals(e.getValue().getAttributeNS(ANDROID, "excludeFromRecents")),
                    "companion activity is excluded from Recents: " + e.getKey());
        }
        String affinity = app.get(CALL).getAttributeNS(ANDROID, "taskAffinity");
        check(!affinity.isEmpty() && !affinity.equals("app.zer0.z0live"),
                "the call screen has its own task affinity, apart from the trampolines: '" + affinity + "'");
        for (String trampoline : new String[] {LINK, COMMAND}) {
            check("true".equals(app.get(trampoline).getAttributeNS(ANDROID, "noHistory")),
                    "trampoline keeps no history: " + trampoline);
        }

        Map<String, Element> launcher = activities(args[1]);
        for (String name : new String[] {CALL, LINK}) {
            check(launcher.containsKey(name) && "true".equals(launcher.get(name).getAttributeNS(ANDROID, "excludeFromRecents")),
                    "Lawnchair's in-launcher call screen is excluded from Recents: " + name);
        }
        System.out.println("PASS: " + checks + " z0live Recents checks");
    }

    private static Map<String, Element> activities(String path) throws Exception {
        DocumentBuilderFactory factory = DocumentBuilderFactory.newInstance();
        factory.setNamespaceAware(true);
        NodeList nodes = factory.newDocumentBuilder().parse(new File(path)).getElementsByTagName("activity");
        Map<String, Element> out = new HashMap<>();
        for (int i = 0; i < nodes.getLength(); i++) {
            Element e = (Element) nodes.item(i);
            out.put(e.getAttributeNS(ANDROID, "name"), e);
        }
        return out;
    }
}
