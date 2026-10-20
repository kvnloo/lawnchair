import app.lawnchair.zer0.voice.z0live.Z0LiveGesturePolicy;
import static app.lawnchair.zer0.voice.z0live.Z0LiveGesturePolicy.Action.*;
public final class Z0LiveGesturePolicyTest {
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
        System.out.println("Gesture policy: " + checked + " cases passed (no Android/hardware claim)");
    }
}
