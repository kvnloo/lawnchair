import app.lawnchair.zer0.voice.z0live.Z0LiveGesturePolicy;
import static app.lawnchair.zer0.voice.z0live.Z0LiveGesturePolicy.Action.*;
public final class Z0LiveGesturePolicyTest {
    static void expect(Z0LiveGesturePolicy.Action got, Z0LiveGesturePolicy.Action want, String why) {
        if (got != want) throw new AssertionError(why + ": got " + got + ", want " + want);
    }
    public static void main(String[] args) {
        int checked = 0;
        for (boolean enabled : new boolean[]{false,true})
        for (boolean ready : new boolean[]{false,true})
        for (boolean active : new boolean[]{false,true})
        for (boolean ending : new boolean[]{false,true})
        for (String gesture : new String[]{"double_tap","triple_tap","other"}) {
            var expected = !enabled || ending ? NONE : gesture.equals("triple_tap") ? (active ? END : NONE) : !ready ? NONE : gesture.equals("double_tap") ? (active ? MUTE : START) : NONE;
            if (Z0LiveGesturePolicy.decide(enabled,ready,active,ending,gesture) != expected) throw new AssertionError(gesture);
            checked++;
        }
        // Owner mapping, as the service applies it: double with no call = start (Standby only),
        // double during a call = toggle mute, triple = end, whether or not Standby is armed.
        java.util.function.BiFunction<boolean[], String, Z0LiveGesturePolicy.Action> service = (state, gesture) ->
                Z0LiveGesturePolicy.decide(Z0LiveGesturePolicy.accepts(state[0], state[1]), true, state[1], false, gesture);
        boolean[] idleStandby = {true, false}, idleNoStandby = {false, false}, callStandby = {true, true}, callOnly = {false, true};
        expect(service.apply(idleStandby, "double_tap"), START, "double with no call starts one");
        expect(service.apply(idleStandby, "triple_tap"), NONE, "triple with no call does nothing");
        expect(service.apply(idleNoStandby, "double_tap"), NONE, "no Standby and no call: the broker's adb relaunch starts it");
        expect(service.apply(callStandby, "double_tap"), MUTE, "double during a Standby call toggles mute");
        expect(service.apply(callOnly, "double_tap"), MUTE, "double during a call started elsewhere toggles mute");
        expect(service.apply(callStandby, "triple_tap"), END, "triple ends a Standby call");
        expect(service.apply(callOnly, "triple_tap"), END, "triple ends a call started elsewhere");
        checked += 7;
        System.out.println("Gesture policy: " + checked + " cases passed (no Android/hardware claim)");
    }
}
