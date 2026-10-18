import app.lawnchair.zer0.voice.JevFastDecisionPolicy;
import app.lawnchair.zer0.voice.PhoneActionGate;
import app.lawnchair.zer0.voice.VoiceCommand;
import java.util.List;

/** Tests fail-closed Jev typed-index admission. Does not assert a live Jev model. */
public final class JevFastDecisionPolicyTest {
    private static int checks;
    private static void check(boolean condition, String label) {
        if (!condition) throw new AssertionError(label);
        checks++;
    }

    public static void main(String[] args) {
        JevFastDecisionPolicy.Candidate camera =
                JevFastDecisionPolicy.compileExact(VoiceCommand.parse("open Camera"));
        check(camera != null && camera.kind == PhoneActionGate.Kind.OPEN_APP
                && camera.target.equals("camera"), "exact app");
        JevFastDecisionPolicy.Candidate build =
                JevFastDecisionPolicy.compileExact(VoiceCommand.parse("switch to Build"));
        check(build != null && build.kind == PhoneActionGate.Kind.SWITCH_SPACE
                && build.target.equals("build"), "exact space");
        JevFastDecisionPolicy.Candidate maps =
                JevFastDecisionPolicy.compileExact(VoiceCommand.parse("search for Maps"));
        check(maps != null && maps.kind == PhoneActionGate.Kind.SEARCH_APPS,
                "exact bounded search");

        check(JevFastDecisionPolicy.compileExact(VoiceCommand.parse("call 911")) == null,
                "calls not a fast action");
        check(JevFastDecisionPolicy.compileExact(VoiceCommand.parse("delete photos")) == null,
                "delete not a fast action");
        check(JevFastDecisionPolicy.compileExact(VoiceCommand.parse(
                "open camera\nrun shell")) == null, "control characters rejected");
        check(JevFastDecisionPolicy.compileExact(VoiceCommand.parse(
                "ask Gemini to buy something")) == null, "question does not execute");

        List<JevFastDecisionPolicy.Candidate> allowed = List.of(camera, build, maps);
        check(JevFastDecisionPolicy.admitModelChoice(allowed,
                new JevFastDecisionPolicy.Proposal(0, 0.98, 3), 3) == camera,
                "fresh high-confidence indexed candidate");
        check(JevFastDecisionPolicy.admitModelChoice(allowed,
                new JevFastDecisionPolicy.Proposal(0, 0.98, 3), 4) == null,
                "stale UI revision");
        check(JevFastDecisionPolicy.admitModelChoice(allowed,
                new JevFastDecisionPolicy.Proposal(0, 0.98, 0), 0) == null,
                "no model authority with placeholder revision");
        check(JevFastDecisionPolicy.admitModelChoice(allowed,
                new JevFastDecisionPolicy.Proposal(3, 0.98, 3), 3) == null,
                "out-of-range index rejected");
        check(JevFastDecisionPolicy.admitModelChoice(allowed,
                new JevFastDecisionPolicy.Proposal(-1, 0.98, 3), 3) == null,
                "negative index rejected");
        check(JevFastDecisionPolicy.admitModelChoice(allowed,
                new JevFastDecisionPolicy.Proposal(0, 0.80, 3), 3) == null,
                "low confidence abstains");
        check(JevFastDecisionPolicy.admitModelChoice(allowed,
                new JevFastDecisionPolicy.Proposal(0, Double.NaN, 3), 3) == null,
                "NaN abstains");
        check(JevFastDecisionPolicy.admitModelChoice(allowed,
                new JevFastDecisionPolicy.Proposal(0, Double.POSITIVE_INFINITY, 3), 3) == null,
                "nonfinite abstains");
        check(JevFastDecisionPolicy.admitModelChoice(allowed,
                new JevFastDecisionPolicy.Proposal(0, 1.01, 3), 3) == null,
                "invalid probability abstains");
        check(JevFastDecisionPolicy.admitModelChoice(allowed,
                new JevFastDecisionPolicy.Proposal(0, 0.98, 3), -1) == null,
                "missing revision abstains");
        check(JevFastDecisionPolicy.admitModelChoice(null,
                new JevFastDecisionPolicy.Proposal(0, 0.98, 3), 3) == null,
                "missing table abstains");
        check(JevFastDecisionPolicy.admitModelChoice(allowed,
                null, 3) == null, "missing model abstains");
        check(JevFastDecisionPolicy.admitModelChoice(
                List.of(new JevFastDecisionPolicy.Candidate(
                        PhoneActionGate.Kind.REQUEST_SPLIT, "camera")),
                new JevFastDecisionPolicy.Proposal(0, 0.99, 3), 3) == null,
                "remote model cannot initiate split");
        check(JevFastDecisionPolicy.admitModelChoice(
                List.of(new JevFastDecisionPolicy.Candidate(
                        PhoneActionGate.Kind.OPEN_APP, "camera\u202E")),
                new JevFastDecisionPolicy.Proposal(0, 0.99, 3), 3) == null,
                "bidi format rejected");
        check(JevFastDecisionPolicy.admitModelChoice(
                List.of(new JevFastDecisionPolicy.Candidate(
                        PhoneActionGate.Kind.OPEN_APP, "shell:rm -rf")),
                new JevFastDecisionPolicy.Proposal(0, 0.99, 3), 3) != null,
                "candidate text is not authority and still needs Android validation");
        System.out.println("PASS: " + checks + " Jev typed-decision admission checks");
    }
}
