package app.lawnchair.zer0.voice.z0live;

/** Main-thread turn gate: tool continuations and inbox speech wait for safe boundaries. */
public final class Z0LiveTurnState {
    private boolean response;
    private boolean user;
    private boolean inputMuted;
    private boolean playback;
    private boolean continuation;

    public void responseStarted() { response = true; }
    public void responseDone() { response = false; }
    public void userSpeaking(boolean value) { user = value && !inputMuted; }
    public void inputMuted(boolean value) {
        inputMuted = value;
        if (value) user = false;
    }
    public void playback(boolean value) { playback = value; }
    public void toolCompleted() { toolCompleted(true); }
    public void toolCompleted(boolean needsResponse) { continuation |= needsResponse; }
    public boolean canSpeakInbox() { return !response && !user && !playback && !continuation; }
    public boolean takeContinuation(boolean toolsPending) {
        if (!continuation || response || user || playback || toolsPending) return false;
        continuation = false;
        response = true;
        return true;
    }
}
