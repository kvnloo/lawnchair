import app.lawnchair.zer0.voice.z0live.OuraGestureAdmission;
import app.lawnchair.zer0.voice.z0live.OuraGestureAdmission.Decision;

public final class OuraGestureAdmissionTest {
    private static void check(boolean accepted, boolean armed, boolean proof,
            boolean active, String gesture, long at, long now, long seq, long last,
            long previousAcceptedAt) {
        Decision actual = OuraGestureAdmission.evaluate(
                armed, proof, active, gesture, at, now, seq, last, previousAcceptedAt);
        if ((actual == Decision.TOGGLE_EXISTING_SESSION_MUTE) != accepted) {
            throw new AssertionError("Unexpected gesture admission decision");
        }
    }

    public static void main(String[] args) {
        check(true,  true, true, true, "DOUBLE_TAP", 1000, 1120, 8, 7, -1);
        check(false, false, true, true, "DOUBLE_TAP", 1000, 1120, 8, 7, -1);
        check(false, true, false, true, "DOUBLE_TAP", 1000, 1120, 8, 7, -1);
        check(false, true, true, false, "DOUBLE_TAP", 1000, 1120, 8, 7, -1);
        check(false, true, true, true, "TRIPLE_TAP", 1000, 1120, 8, 7, -1);
        check(false, true, true, true, "DOUBLE_TAP", 1000, 1120, 7, 7, -1);
        check(false, true, true, true, "DOUBLE_TAP", 1000, 1120, 0, -1, -1);
        check(false, true, true, true, "DOUBLE_TAP", 1000, 2501, 8, 7, -1);
        check(false, true, true, true, "DOUBLE_TAP", 1000, 999, 8, 7, -1);
        check(false, true, true, true, "DOUBLE_TAP", -1, 999, 8, 7, -1);
        check(false, true, true, true, "DOUBLE_TAP", 1800, 2000, 8, 7, 1500);
        check(false, true, true, true, "DOUBLE_TAP", 1800, 2000, 8, 7, 2100);
        check(true,  true, true, true, "DOUBLE_TAP", 1000, 2500, 8, 7, -1);
        check(true,  true, true, true, "DOUBLE_TAP", 2400, 2500, 8, 7, 1500);
        System.out.println("Oura gesture admission: 14 cases passed");
    }
}
