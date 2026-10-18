import app.lawnchair.zer0.voice.OnDeviceVoiceSession;

/** Pure JVM lifecycle contract, not proof of an installed offline speech model. */
public final class OnDeviceVoiceSessionTest {
    private static int checked;
    private static void check(boolean condition, String name) {
        if (!condition) throw new AssertionError(name);
        checked++;
    }

    public static void main(String[] args) {
        OnDeviceVoiceSession voice = new OnDeviceVoiceSession();
        check(!voice.isTerminal(), "new session not finished");
        check(!voice.complete(), "no final result before start");
        check(voice.begin(), "one explicit start");
        check(!voice.begin(), "cannot start twice");
        check(voice.complete(), "first final result accepted");
        check(voice.isTerminal(), "terminal after result");
        check(!voice.complete(), "duplicate callback suppressed");
        check(!voice.begin(), "no restart after final result");
        OnDeviceVoiceSession canceled = new OnDeviceVoiceSession();
        canceled.cancel();
        check(!canceled.begin(), "cancel before permission response");
        check(!canceled.complete(), "cancel blocks late results");
        OnDeviceVoiceSession listening = new OnDeviceVoiceSession();
        check(listening.begin(), "new session can start");
        listening.cancel();
        check(!listening.complete(), "background/stop rejects later result");
        System.out.println("PASS: " + checked + " offline voice lifecycle checks");
    }
}
