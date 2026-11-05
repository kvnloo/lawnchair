package app.lawnchair.zer0.voice.z0live;

import android.os.Handler;
import android.os.Looper;
import java.util.concurrent.CopyOnWriteArraySet;

/** Process-local UI state. No transcript or credential is written to disk. */
public final class Z0LiveStatus {
    public interface Observer { void onChanged(Snapshot value); }

    public static final class Snapshot {
        public final String status, caption, error;
        public final boolean muted, active, pendingNetwork;
        public final int telemetryFailed;

        Snapshot(String status, String caption, String error, boolean muted,
                boolean active, boolean pendingNetwork, int telemetryFailed) {
            this.status = status;
            this.caption = caption;
            this.error = error;
            this.muted = muted;
            this.active = active;
            this.pendingNetwork = pendingNetwork;
            this.telemetryFailed = telemetryFailed;
        }
    }

    private static final Handler MAIN = new Handler(Looper.getMainLooper());
    private static final CopyOnWriteArraySet<Observer> OBSERVERS = new CopyOnWriteArraySet<>();
    private static volatile Snapshot current = new Snapshot("Ended", "", "", false, false, false, 0);

    private Z0LiveStatus() {}
    public static Snapshot get() { return current; }
    public static void observe(Observer observer) {
        OBSERVERS.add(observer);
        MAIN.post(() -> { if (OBSERVERS.contains(observer)) observer.onChanged(current); });
    }
    public static void remove(Observer observer) { OBSERVERS.remove(observer); }
    static void publish(Snapshot value) {
        current = value;
        for (Observer observer : OBSERVERS) observer.onChanged(value);
    }
}
