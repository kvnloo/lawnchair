import app.lawnchair.zer0.voice.JevFastDecisionPolicy;
import app.lawnchair.zer0.voice.VoiceCommand;
import java.util.Arrays;

/** Host-JVM microbenchmark; no STT/model, device CPU, UI or network timing implied. */
public final class FastDecisionBenchmark {
    public static void main(String[] args) {
        String[] commands = {
            "open Camera", "switch to Build", "search for Maps",
            "explain fusion power", "turn on flashlight", "ask Gemini for help"
        };
        for (int i = 0; i < 30000; i++) {
            JevFastDecisionPolicy.compileExact(VoiceCommand.parse(commands[i % commands.length]));
        }
        long[] nanos = new long[30000];
        int local = 0;
        for (int i = 0; i < nanos.length; i++) {
            long started = System.nanoTime();
            if (JevFastDecisionPolicy.compileExact(
                    VoiceCommand.parse(commands[i % commands.length])) != null) {
                local++;
            }
            nanos[i] = System.nanoTime() - started;
        }
        Arrays.sort(nanos);
        System.out.println("Host JVM grammar + legal-action compile: samples=" + nanos.length
                + " local=" + local + " p50_us=" + (nanos[nanos.length / 2] / 1000.0)
                + " p95_us=" + (nanos[(nanos.length * 95) / 100] / 1000.0));
        System.out.println("NOT a Gemini or Jev inference benchmark or handset latency.");
    }
}
