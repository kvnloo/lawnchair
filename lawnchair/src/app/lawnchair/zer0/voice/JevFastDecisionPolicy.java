package app.lawnchair.zer0.voice;

import java.util.List;

/**
 * A typed decision boundary suitable for Jev / z0intelligence selection.
 *
 * <p>Current on-phone behavior compiles exact, explicit grammar without an LLM.
 * Future model outputs may select ONLY indices in a fresh, caller-approved
 * candidate table. Model output can never supply package names, shell commands,
 * coordinates, permissions or unseen Android UI targets.
 */
public final class JevFastDecisionPolicy {
    public static final double MODEL_AUTO_COMMIT_THRESHOLD = 0.90;
    public static final int MAX_CANDIDATES = 32;

    public static final class Candidate {
        public final PhoneActionGate.Kind kind;
        public final String target;

        public Candidate(PhoneActionGate.Kind kind, String target) {
            this.kind = kind;
            this.target = target;
        }
    }

    /** Typed Jev head output: indexed local action plus probability and UI revision. */
    public static final class Proposal {
        public final int candidateIndex;
        public final double probability;
        public final long snapshotRevision;

        public Proposal(int candidateIndex, double probability, long snapshotRevision) {
            this.candidateIndex = candidateIndex;
            this.probability = probability;
            this.snapshotRevision = snapshotRevision;
        }
    }

    private JevFastDecisionPolicy() {}

    /**
     * Bypass inference for trusted exact user commands. Authorization and
     * target visibility still belong to PhoneActionGate and the Android executor.
     */
    public static Candidate compileExact(VoiceCommand command) {
        if (command == null) return null;
        PhoneActionGate.Kind kind;
        switch (command.kind) {
            case OPEN_APP:
                kind = PhoneActionGate.Kind.OPEN_APP;
                break;
            case SWITCH_SPACE:
                kind = PhoneActionGate.Kind.SWITCH_SPACE;
                break;
            case SEARCH_APPS:
                kind = PhoneActionGate.Kind.SEARCH_APPS;
                break;
            default:
                return null;
        }
        if (!safeTarget(command.target)) return null;
        return new Candidate(kind, command.target);
    }

    /**
     * Accept a future model proposal only after a fresh, observed candidate
     * table was built by the trusted host. An unavailable model always abstains.
     *
     * <p>The current UI revision MUST be positive and must match the proposal:
     * never use a placeholder revision such as 0 for model-initiated actions.
     * A successfully chosen index is still a proposal, not execution authority.
     */
    public static Candidate admitModelChoice(
            List<Candidate> eligible, Proposal proposal, long actualUiRevision) {
        if (proposal == null || eligible == null || eligible.isEmpty()
                || eligible.size() > MAX_CANDIDATES || actualUiRevision <= 0
                || proposal.snapshotRevision != actualUiRevision
                || proposal.candidateIndex < 0
                || proposal.candidateIndex >= eligible.size()
                || !Double.isFinite(proposal.probability)
                || proposal.probability < MODEL_AUTO_COMMIT_THRESHOLD
                || proposal.probability > 1.0) {
            return null;
        }
        Candidate chosen = eligible.get(proposal.candidateIndex);
        return allowed(chosen) ? chosen : null;
    }

    private static boolean allowed(Candidate candidate) {
        if (candidate == null || candidate.kind == null || !safeTarget(candidate.target)) {
            return false;
        }
        return candidate.kind == PhoneActionGate.Kind.OPEN_APP
                || candidate.kind == PhoneActionGate.Kind.SWITCH_SPACE
                || candidate.kind == PhoneActionGate.Kind.SEARCH_APPS;
    }

    private static boolean safeTarget(String value) {
        if (value == null || value.isBlank() || value.length() > 160) return false;
        for (int i = 0; i < value.length();) {
            int codePoint = value.codePointAt(i);
            if (Character.isISOControl(codePoint)
                    || Character.getType(codePoint) == Character.FORMAT) return false;
            i += Character.charCount(codePoint);
        }
        return true;
    }
}
