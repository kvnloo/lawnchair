package app.lawnchair.zer0.voice.z0live;

/** The launcher-facing START/MUTE/END contract of the z0live companion. */
public final class Z0LiveCommandsTest {
    private static int checks;
    private static void check(boolean value, String message) { if (!value) throw new AssertionError(message); checks++; }

    public static void main(String[] args) {
        check("z0live.action.START".equals(Z0LiveCommands.START), "START action name is the published contract");
        check("z0live.action.MUTE".equals(Z0LiveCommands.MUTE), "MUTE action name is the published contract");
        check("z0live.action.END".equals(Z0LiveCommands.END), "END action name is the published contract");

        check(Z0LiveCommands.decide(Z0LiveCommands.START, false) == Z0LiveCommands.Effect.OPEN_AND_START,
                "START with no call opens the visible controls, which start the call");
        check(Z0LiveCommands.decide(Z0LiveCommands.START, true) == Z0LiveCommands.Effect.NONE,
                "START during a call never starts a second call or mutes");
        check(Z0LiveCommands.decide(Z0LiveCommands.MUTE, true) == Z0LiveCommands.Effect.TOGGLE_MUTE,
                "MUTE during a call toggles mute");
        check(Z0LiveCommands.decide(Z0LiveCommands.MUTE, false) == Z0LiveCommands.Effect.NONE,
                "MUTE with no call never starts one");
        check(Z0LiveCommands.decide(Z0LiveCommands.END, true) == Z0LiveCommands.Effect.END,
                "END during a call ends it");
        check(Z0LiveCommands.decide(Z0LiveCommands.END, false) == Z0LiveCommands.Effect.NONE,
                "END with no call is a no-op");
        for (String other : new String[] {null, "", "z0live.START", "android.intent.action.MAIN", "z0live.action.start"}) {
            check(Z0LiveCommands.decide(other, false) == Z0LiveCommands.Effect.NONE
                    && Z0LiveCommands.decide(other, true) == Z0LiveCommands.Effect.NONE,
                    "unknown or internal actions do nothing: " + other);
        }
        System.out.println("PASS: " + checks + " companion command checks");
    }
}
