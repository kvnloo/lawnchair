import app.lawnchair.zer0.voice.PhoneActionGate;
import app.lawnchair.zer0.voice.PhoneActionGate.Authority;
import app.lawnchair.zer0.voice.PhoneActionGate.Decision;
import app.lawnchair.zer0.voice.PhoneActionGate.Kind;
import app.lawnchair.zer0.voice.PhoneActionGate.Request;

/** Device-independent safety contract; NOT Android voice/window integration evidence. */
public final class PhoneActionGateTest {
    private static int checked;

    private static Request req(String id, String utterance, Kind kind,
            Authority authority, String target, long revision, long expiry) {
        return new Request(id, utterance, kind, authority, target, revision, expiry);
    }

    private static void expect(Decision actual, Decision expected, String label) {
        if (actual != expected) {
            throw new AssertionError(label + ": expected " + expected + ", got " + actual);
        }
        checked++;
    }

    public static void main(String[] args) {
        PhoneActionGate gate = new PhoneActionGate();
        Request open = req("a1", "u1", Kind.OPEN_APP, Authority.ONE_SHOT_VOICE,
                "camera", 7, 10100);
        expect(gate.admit(open, 10000, 7), Decision.ADMITTED, "voice open");
        expect(gate.admit(open, 10001, 7), Decision.DUPLICATE, "exact replay");
        expect(gate.admit(req("a2", "u1", Kind.OPEN_APP, Authority.ONE_SHOT_VOICE,
                "camera", 7, 10100), 10001, 7), Decision.DUPLICATE, "new action id same utterance");
        expect(gate.admit(req("a1", "u2", Kind.OPEN_APP, Authority.ONE_SHOT_VOICE,
                "camera", 7, 10100), 10001, 7), Decision.DUPLICATE, "same action new utterance");
        expect(gate.admit(req("b", "v", Kind.SWITCH_SPACE, Authority.ONE_SHOT_VOICE,
                "Build", 7, 11000), 10002, 7), Decision.ADMITTED, "space switch");
        expect(gate.admit(req("qa", "qw", Kind.SEARCH_APPS, Authority.ONE_SHOT_VOICE,
                "maps", 7, 11000), 10002, 7), Decision.ADMITTED, "voice palette search");
        expect(gate.admit(req("qb", "qx", Kind.SEARCH_APPS, Authority.REMOTE_MODEL,
                "maps", 7, 11000), 10002, 7), Decision.REQUIRES_CONFIRMATION,
                "remote model cannot open palette without confirmation");
        expect(gate.admit(req("c", "w", Kind.REQUEST_SPLIT, Authority.ONE_SHOT_VOICE,
                "A|B", 7, 11000), 10002, 7), Decision.NOT_ALLOWED, "no voice split");
        expect(gate.admit(req("d", "x", Kind.REQUEST_SPLIT, Authority.USER_TAP,
                "A|B", 7, 11000), 10002, 7), Decision.ADMITTED, "explicit split");
        expect(gate.admit(req("e", "y", Kind.OPEN_APP, Authority.REMOTE_MODEL,
                "camera", 7, 11000), 10002, 7), Decision.REQUIRES_CONFIRMATION, "model cannot execute");
        expect(gate.admit(req("e", "y", Kind.OPEN_APP, Authority.USER_TAP,
                "camera", 7, 11000), 10003, 7), Decision.DUPLICATE, "denied action cannot retry");
        expect(gate.admit(req("f", "z", Kind.OPEN_APP, Authority.USER_TAP,
                "camera", 6, 11000), 10002, 7), Decision.STALE_UI, "stale UI revision");
        expect(gate.admit(req("g", "za", Kind.OPEN_APP, Authority.USER_TAP,
                "camera", 7, 10002), 10002, 7), Decision.EXPIRED, "deadline inclusive");
        expect(gate.admit(req("h", "zb", Kind.OPEN_APP, Authority.USER_TAP,
                "camera", 7, 40003), 10002, 7), Decision.EXPIRED, "unbounded future");
        expect(gate.admit(req("i", "zc", Kind.OPEN_APP, Authority.USER_TAP,
                "camera", 7, 40002), 10002, 7), Decision.ADMITTED, "max horizon boundary");
        expect(gate.admit(req("j", "zd", Kind.OPEN_APP, Authority.USER_TAP,
                "camera", -1, 11000), 10002, 7), Decision.INVALID, "bad revision");
        expect(gate.admit(req("j", "zd", Kind.OPEN_APP, Authority.USER_TAP,
                "camera", 7, 11000), 10002, 7), Decision.ADMITTED, "invalid not consumed");
        expect(gate.admit(req("k", "ze", Kind.OPEN_APP, Authority.ONE_SHOT_VOICE,
                "Camera\nDelete", 7, 11000), 10002, 7), Decision.INVALID, "multiline target");
        expect(gate.admit(req("k", "ze", Kind.OPEN_APP, Authority.ONE_SHOT_VOICE,
                "Camera\u202e", 7, 11000), 10002, 7), Decision.INVALID, "bidi override");
        expect(gate.admit(req("k", "ze", Kind.OPEN_APP, Authority.ONE_SHOT_VOICE,
                "Camera", 7, 11000), 10002, 7), Decision.ADMITTED, "invalid target did not consume");
        expect(gate.admit(req("bad action", "zf", Kind.OPEN_APP, Authority.USER_TAP,
                "camera", 7, 11000), 10002, 7), Decision.INVALID, "spaces in identity");
        expect(gate.admit(null, 10002, 7), Decision.INVALID, "null request");
        expect(gate.admit(req("m", "zg", Kind.OPEN_APP, Authority.USER_TAP,
                "", 7, 11000), 10002, 7), Decision.INVALID, "empty target");
        expect(gate.admit(req("n", "zh", null, Authority.USER_TAP,
                "camera", 7, 11000), 10002, 7), Decision.INVALID, "unknown kind");
        expect(gate.admit(req("o", "zi", Kind.OPEN_APP, Authority.USER_TAP,
                "camera", 7, 11000), -1, 7), Decision.INVALID, "negative monotonic time");
        PhoneActionGate other = new PhoneActionGate();
        expect(other.admit(open, 10000, 7), Decision.ADMITTED,
                "fresh process not a durable replay oracle");
        System.out.println("PASS: " + checked + " typed phone action gate checks");
    }
}
