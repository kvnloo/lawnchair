package app.lawnchair.zer0.voice.z0live;

import java.util.ArrayDeque;
import java.util.Iterator;
import org.json.JSONArray;
import org.json.JSONObject;

/** A bounded FIFO. The service owns scheduling and acknowledges the original batch objects. */
public final class Z0LiveTelemetry {
    private static final int MAX_EVENTS = 128;
    private static final int MAX_ATTEMPTS = 3;

    private static final class Entry {
        final JSONObject event;
        int attempts;

        Entry(JSONObject event) {
            this.event = event;
        }
    }

    private final ArrayDeque<Entry> entries = new ArrayDeque<>();
    private int dropped;

    public synchronized void offer(JSONObject event) {
        if (event == null) return;
        if (entries.size() == MAX_EVENTS) {
            entries.removeFirst();
            dropped++;
        }
        entries.addLast(new Entry(event));
    }

    public synchronized JSONArray batch(int maximum) {
        JSONArray batch = new JSONArray();
        for (Entry entry : entries) {
            if (batch.length() >= maximum) break;
            batch.put(entry.event);
        }
        return batch;
    }

    public synchronized void delivered(JSONArray batch) {
        if (batch == null) return;
        Iterator<Entry> iterator = entries.iterator();
        while (iterator.hasNext()) {
            if (containsIdentity(batch, iterator.next().event)) iterator.remove();
        }
    }

    public synchronized void failed(JSONArray batch) {
        if (batch == null) return;
        Iterator<Entry> iterator = entries.iterator();
        while (iterator.hasNext()) {
            Entry entry = iterator.next();
            if (containsIdentity(batch, entry.event) && ++entry.attempts >= MAX_ATTEMPTS) {
                iterator.remove();
                dropped++;
            }
        }
    }

    /** Cumulative events dropped after overflow or three failed sends. */
    public synchronized int failedCount() {
        return dropped;
    }

    public synchronized int size() {
        return entries.size();
    }

    private static boolean containsIdentity(JSONArray batch, JSONObject event) {
        for (int index = 0; index < batch.length(); index++) {
            if (batch.opt(index) == event) return true;
        }
        return false;
    }
}
