package app.lawnchair.zer0.voice.z0live;

import org.json.JSONArray;
import org.json.JSONObject;

/** Queue receipts must remain correct while callbacks and newly offered events overlap. */
public final class Z0LiveTelemetryTest {
    private static int checked;

    public static void main(String[] args) throws Exception {
        retriesAndDelivery();
        overflowDuringDelivery();
        equalValuesKeepDistinctIdentities();
        parallelOffers();
        System.out.println("PASS: " + checked + " z0live telemetry queue checks");
    }

    private static void retriesAndDelivery() throws Exception {
        Z0LiveTelemetry queue = new Z0LiveTelemetry();
        JSONObject one = event(1);
        JSONObject two = event(2);
        queue.offer(one);
        queue.offer(two);
        JSONArray first = queue.batch(1);
        check(first.length() == 1 && first.opt(0) == one, "batch retains FIFO event identity");
        queue.failed(first);
        check(queue.size() == 2 && queue.failedCount() == 0, "first failed attempt retains event");
        queue.failed(first);
        check(queue.size() == 2 && queue.failedCount() == 0, "second failed attempt retains event");
        queue.failed(first);
        check(queue.size() == 1 && queue.failedCount() == 1, "third failed attempt drops and counts event");
        check(queue.batch(5).opt(0) == two, "remaining event stays queued");
        queue.failed(first);
        check(queue.size() == 1 && queue.failedCount() == 1, "late failed receipt counted only once");
        queue.delivered(first);
        check(queue.size() == 1, "late delivery leaves other events alone");
        queue.delivered(queue.batch(5));
        check(queue.size() == 0, "successful receipt removes event");
        check(queue.batch(0).length() == 0 && queue.batch(-1).length() == 0, "nonpositive batch is empty");
    }

    private static void overflowDuringDelivery() throws Exception {
        Z0LiveTelemetry queue = new Z0LiveTelemetry();
        for (int i = 0; i < 128; i++) queue.offer(event(i));
        JSONArray inFlight = queue.batch(2);
        JSONObject newest = event(128);
        queue.offer(newest);
        check(queue.size() == 128 && queue.failedCount() == 1, "overflow bounded and counted");
        queue.delivered(inFlight);
        check(queue.size() == 127, "ack removes surviving original event only");
        check(queue.batch(1).getJSONObject(0).getInt("id") == 2, "overflow ack preserves next head");
        check(queue.batch(200).opt(126) == newest, "overflow ack preserves newly offered event");
        queue.failed(inFlight);
        check(queue.failedCount() == 1, "late failure does not count evicted event twice");

        Z0LiveTelemetry entirelyEvicted = new Z0LiveTelemetry();
        for (int i = 0; i < 128; i++) entirelyEvicted.offer(event(i));
        JSONArray old = entirelyEvicted.batch(2);
        entirelyEvicted.offer(event(128));
        entirelyEvicted.offer(event(129));
        entirelyEvicted.delivered(old);
        check(entirelyEvicted.size() == 128, "receipt never blindly removes queue head");
    }

    private static void equalValuesKeepDistinctIdentities() throws Exception {
        Z0LiveTelemetry queue = new Z0LiveTelemetry();
        JSONObject first = event(1);
        JSONObject second = event(1);
        queue.offer(first);
        JSONArray receipt = queue.batch(1);
        queue.offer(second);
        queue.delivered(receipt);
        check(queue.size() == 1 && queue.batch(1).opt(0) == second,
                "delivery matches object identity rather than JSON values");
        queue.failed(new JSONArray().put(first));
        queue.failed(new JSONArray().put(first));
        queue.failed(new JSONArray().put(first));
        check(queue.size() == 1 && queue.failedCount() == 0, "late attempts never age an equal-valued event");
    }

    private static void parallelOffers() throws Exception {
        Z0LiveTelemetry queue = new Z0LiveTelemetry();
        Thread[] producers = new Thread[4];
        Throwable[] errors = new Throwable[producers.length];
        for (int i = 0; i < producers.length; i++) {
            final int producer = i;
            producers[i] = new Thread(() -> {
                try {
                    for (int item = 0; item < 100; item++) queue.offer(event(producer * 100 + item));
                } catch (Throwable error) {
                    errors[producer] = error;
                }
            });
            producers[i].start();
        }
        for (Thread producer : producers) producer.join();
        for (Throwable error : errors) if (error != null) throw new AssertionError("producer failed", error);
        check(queue.size() == 128 && queue.failedCount() == 272, "parallel offers retain bound and exact drops");
        check(queue.batch(Integer.MAX_VALUE).length() == 128, "batch bounded by queue");
    }

    private static JSONObject event(int id) throws Exception {
        return new JSONObject().put("id", id).put("type", "test");
    }

    private static void check(boolean condition, String label) {
        if (!condition) throw new AssertionError(label);
        checked++;
    }
}
