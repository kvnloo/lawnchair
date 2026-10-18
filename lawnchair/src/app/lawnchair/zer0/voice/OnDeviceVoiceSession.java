package app.lawnchair.zer0.voice;

/**
 * One-listening-attempt guard for an explicit foreground on-device recognizer.
 * Only one final transcript may be forwarded; late errors and partials are ignored.
 */
public final class OnDeviceVoiceSession {
    private boolean started;
    private boolean terminal;

    public synchronized boolean begin() {
        if (started || terminal) return false;
        started = true;
        return true;
    }

    public synchronized boolean complete() {
        if (!started || terminal) return false;
        terminal = true;
        return true;
    }

    public synchronized void cancel() {
        terminal = true;
    }

    public synchronized boolean isTerminal() {
        return terminal;
    }
}
