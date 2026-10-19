import app.lawnchair.zer0.voice.JevFastDecisionPolicy;
import app.lawnchair.zer0.voice.PhoneActionGate;
import app.lawnchair.zer0.voice.PhoneActionGate.Authority;
import app.lawnchair.zer0.voice.PhoneActionGate.Decision;
import app.lawnchair.zer0.voice.PhoneActionGate.Kind;
import app.lawnchair.zer0.voice.PhoneActionGate.Request;
import java.util.ArrayList;
import java.util.List;

/** Pure-Java caller-side admission matrix, not an Android executor integration test. */
public final class PhoneActionBoundaryMatrixTest {
    private static final long NOW = 10_000;
    private static final long REV = 37;
    private static int checks;

    private static Request request(String id, String utterance, Kind kind, Authority authority,
            String target, long revision, long expiry) {
        return new Request(id, utterance, kind, authority, target, revision, expiry);
    }

    private static Request normal(String id, String utterance) {
        return request(id, utterance, Kind.OPEN_APP, Authority.ONE_SHOT_VOICE,
                "camera", REV, NOW + 500);
    }

    private static void check(boolean value, String label) {
        if (!value) throw new AssertionError(label);
        checks++;
    }

    /** Synthetic caller: only an ADMITTED decision can reach this fake counter. */
    private static final class AdmissionProbe {
        int dispatches;
        void consider(Decision decision) {
            if (decision == Decision.ADMITTED) dispatches++;
        }
    }

    private static void rejected(PhoneActionGate gate, AdmissionProbe probe,
            Request request, long now, long revision, Decision expected, String label) {
        int before = probe.dispatches;
        Decision decision = gate.admit(request, now, revision);
        check(decision == expected, label + ": expected " + expected + ", got " + decision);
        probe.consider(decision);
        check(probe.dispatches == before, label + ": rejected request dispatched");
    }

    private static void replayExpiryRevisionAndAuthority() {
        PhoneActionGate gate = new PhoneActionGate();
        AdmissionProbe probe = new AdmissionProbe();
        Request first = normal("action-first", "utterance-first");
        check(gate.admit(first, NOW, REV) == Decision.ADMITTED, "first explicit voice admit");
        rejected(gate, probe, first, NOW + 1, REV, Decision.DUPLICATE,
                "exact action/utterance replay");
        rejected(gate, probe, normal("action-second", "utterance-first"), NOW + 1, REV,
                Decision.DUPLICATE, "different action same finalized utterance");
        rejected(gate, probe, normal("action-first", "utterance-second"), NOW + 1, REV,
                Decision.DUPLICATE, "same action different utterance");
        rejected(gate, probe, request("expired", "expired-u", Kind.OPEN_APP,
                        Authority.USER_TAP, "camera", REV, NOW), NOW, REV,
                Decision.EXPIRED, "deadline inclusive");
        rejected(gate, probe, normal("expired", "new-u"), NOW, REV,
                Decision.DUPLICATE, "expired action cannot reuse old ID");
        rejected(gate, probe, request("future", "future-u", Kind.OPEN_APP,
                        Authority.USER_TAP, "camera", REV, NOW + PhoneActionGate.MAX_HORIZON_MS + 1),
                NOW, REV, Decision.EXPIRED, "future horizon plus one");
        check(gate.admit(request("edge", "edge-u", Kind.SEARCH_APPS,
                Authority.USER_TAP, "apps", REV, NOW + PhoneActionGate.MAX_HORIZON_MS),
                NOW, REV) == Decision.ADMITTED, "max horizon inclusive");
        rejected(gate, probe, request("stale", "stale-u", Kind.OPEN_APP,
                Authority.USER_TAP, "camera", REV - 1, NOW + 500), NOW, REV,
                Decision.STALE_UI, "changed revision");
        rejected(gate, probe, request("new-id", "stale-u", Kind.OPEN_APP,
                Authority.USER_TAP, "camera", REV, NOW + 500), NOW, REV,
                Decision.DUPLICATE, "stale utterance still consumed");
        rejected(gate, probe, request("remote", "remote-u", Kind.OPEN_APP,
                Authority.REMOTE_MODEL, "camera", REV, NOW + 500), NOW, REV,
                Decision.REQUIRES_CONFIRMATION, "remote suggestion lacks authority");
        rejected(gate, probe, request("remote", "remote-u", Kind.OPEN_APP,
                Authority.USER_TAP, "camera", REV, NOW + 500), NOW, REV,
                Decision.DUPLICATE, "relabeling rejected request cannot authorize replay");
        rejected(gate, probe, request("split", "split-u", Kind.REQUEST_SPLIT,
                Authority.ONE_SHOT_VOICE, "a|b", REV, NOW + 500), NOW, REV,
                Decision.NOT_ALLOWED, "destructive split requires owner tap");
        check(probe.dispatches == 0, "none of the rejected cases reached fake caller");
    }

    private static void invalidDoesNotConsumeIdentity() {
        PhoneActionGate gate = new PhoneActionGate();
        AdmissionProbe probe = new AdmissionProbe();
        String id = "retry-after-invalid";
        String utterance = "retry-utterance";
        Request[] invalid = {
            null,
            request(id, utterance, null, Authority.USER_TAP, "camera", REV, NOW + 1),
            request(id, utterance, Kind.OPEN_APP, null, "camera", REV, NOW + 1),
            request(id, utterance, Kind.OPEN_APP, Authority.USER_TAP, "bad\nname", REV, NOW + 1),
            request(id, utterance, Kind.OPEN_APP, Authority.USER_TAP, "bad\u202e", REV, NOW + 1),
            request(id, utterance, Kind.OPEN_APP, Authority.USER_TAP, "x".repeat(161), REV, NOW + 1),
            request(id, utterance, Kind.OPEN_APP, Authority.USER_TAP, "", REV, NOW + 1),
            request("bad action", utterance, Kind.OPEN_APP, Authority.USER_TAP, "camera", REV, NOW + 1),
        };
        for (int i = 0; i < invalid.length; i++) {
            rejected(gate, probe, invalid[i], NOW, REV, Decision.INVALID,
                    "invalid candidate " + i);
        }
        check(gate.admit(normal(id, utterance), NOW, REV) == Decision.ADMITTED,
                "invalid requests never consume candidate identity");
        rejected(gate, probe, normal(id, utterance), NOW, REV,
                Decision.DUPLICATE, "valid retry is consumed");
    }

    private static void modelProposalCannotSelfAuthorize() {
        JevFastDecisionPolicy.Candidate target =
                new JevFastDecisionPolicy.Candidate(Kind.OPEN_APP, "camera");
        List<JevFastDecisionPolicy.Candidate> table = List.of(target);
        double threshold = JevFastDecisionPolicy.MODEL_AUTO_COMMIT_THRESHOLD;
        JevFastDecisionPolicy.Proposal accepted =
                new JevFastDecisionPolicy.Proposal(0, threshold, REV);
        check(JevFastDecisionPolicy.admitModelChoice(table, accepted, REV) == target,
                "exact confidence threshold yields proposal only");
        check(JevFastDecisionPolicy.admitModelChoice(table,
                new JevFastDecisionPolicy.Proposal(0, Math.nextDown(threshold), REV), REV)
                == null, "confidence just below threshold abstains");
        check(JevFastDecisionPolicy.admitModelChoice(table,
                new JevFastDecisionPolicy.Proposal(0, 1.0, REV), REV) == target,
                "probability one allowed as proposal");
        check(JevFastDecisionPolicy.admitModelChoice(table,
                new JevFastDecisionPolicy.Proposal(0, Double.NaN, REV), REV) == null,
                "NaN proposal rejected");
        check(JevFastDecisionPolicy.admitModelChoice(table,
                new JevFastDecisionPolicy.Proposal(0, 0.99, REV - 1), REV) == null,
                "stale model candidate revision");
        check(JevFastDecisionPolicy.admitModelChoice(table,
                new JevFastDecisionPolicy.Proposal(0, 0.99, 0), 0) == null,
                "placeholder revision is never observed");
        check(JevFastDecisionPolicy.admitModelChoice(table,
                new JevFastDecisionPolicy.Proposal(1, 0.99, REV), REV) == null,
                "candidate index cannot invent a target");
        check(JevFastDecisionPolicy.admitModelChoice(List.of(
                new JevFastDecisionPolicy.Candidate(Kind.REQUEST_SPLIT, "a|b")),
                accepted, REV) == null, "model cannot propose split");
        check(JevFastDecisionPolicy.admitModelChoice(List.of(
                new JevFastDecisionPolicy.Candidate(Kind.OPEN_APP, "camera\u200f")),
                accepted, REV) == null, "format characters rejected");
        check(JevFastDecisionPolicy.admitModelChoice(List.of(
                new JevFastDecisionPolicy.Candidate(Kind.OPEN_APP, "  ")),
                accepted, REV) == null, "blank target rejected");
        List<JevFastDecisionPolicy.Candidate> tooMany = new ArrayList<>();
        for (int i = 0; i <= JevFastDecisionPolicy.MAX_CANDIDATES; i++) {
            tooMany.add(target);
        }
        check(JevFastDecisionPolicy.admitModelChoice(tooMany, accepted, REV) == null,
                "overlong candidate table rejected");
        check(JevFastDecisionPolicy.compileExact(null) == null,
                "missing exact user command never compiles");

        PhoneActionGate gate = new PhoneActionGate();
        AdmissionProbe probe = new AdmissionProbe();
        rejected(gate, probe, request("jev", "model-utterance", target.kind,
                        Authority.REMOTE_MODEL, target.target, REV, NOW + 500),
                NOW, REV, Decision.REQUIRES_CONFIRMATION,
                "high-confidence proposal cannot create authority");
    }

    private static void profileAndReceiptLimits() {
        // Gate carries only an opaque target string; it cannot know profile visibility.
        PhoneActionGate gate = new PhoneActionGate();
        Decision decision = gate.admit(request("profile", "profile-u", Kind.OPEN_APP,
                Authority.USER_TAP, "work:camera", REV, NOW + 100), NOW, REV);
        check(decision == Decision.ADMITTED,
                "opaque work-profile string illustrates absent profile check, not permission");
        // No Android executor/permission/verification API exists in these classes.
        check(decision != Decision.DUPLICATE, "admission alone is not a verified receipt");
    }

    public static void main(String[] args) {
        replayExpiryRevisionAndAuthority();
        invalidDoesNotConsumeIdentity();
        modelProposalCannotSelfAuthorize();
        profileAndReceiptLimits();
        System.out.println("PASS: " + checks + " caller-side phone action boundary assertions");
    }
}
