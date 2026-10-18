import app.lawnchair.zer0.voice.SmartVoiceRouter;
import app.lawnchair.zer0.voice.VoiceCommand;

/** One-shot dispatch tests: not proof of cross-app control or a realtime voice API. */
public final class SmartVoiceRouterTest {
    private static int checked;
    private static void expect(
            String input, boolean chatGpt, boolean android, SmartVoiceRouter.Destination expected) {
        SmartVoiceRouter.Destination actual = SmartVoiceRouter.select(
                input, VoiceCommand.parse(input).kind, chatGpt, android);
        if (actual != expected) throw new AssertionError(
                input + ": expected " + expected + ", got " + actual);
        checked++;
    }

    public static void main(String[] args) {
        SmartVoiceRouter.Destination local = SmartVoiceRouter.Destination.LOCAL_ACTION;
        SmartVoiceRouter.Destination chat = SmartVoiceRouter.Destination.CHATGPT_APP;
        SmartVoiceRouter.Destination system = SmartVoiceRouter.Destination.ANDROID_ASSISTANT;
        SmartVoiceRouter.Destination none = SmartVoiceRouter.Destination.UNAVAILABLE;
        expect("open camera", true, true, local);
        expect("search for Maps", true, true, local);
        expect("find app Camera", true, true, local);
        expect("open app Maps", true, true, local);
        expect("switch to Build", true, true, local);
        expect("open life space", true, true, local);
        expect("What is an LLM?", true, true, system);
        expect("Explain attention", true, true, system);
        expect("write a poem", true, true, system);
        expect("ask ChatGPT about batteries", true, true, chat);
        expect("Explain attention", false, true, system);
        expect("Explain attention", false, false, none);
        expect("What is an LLM?", true, false, none);
        expect("ask ChatGPT about batteries", false, true, none);
        expect("turn on flashlight", true, true, system);
        expect("set a timer for 5 minutes", true, true, system);
        expect("navigate to work", true, true, system);
        expect("call a contact", true, true, system);
        expect("hey google weather", true, true, system);
        expect("turn off wifi", true, false, none);
        expect("go back", true, true, system);
        expect("show recents", true, false, none);
        expect("start focus", true, true, system);
        expect("go home", true, true, system);
        expect("", true, true, none);
        expect("   ", true, true, none);
        expect("hello\nopen camera", true, true, none);
        expect("hello\u202egtp tahc", true, true, none);
        expect("x".repeat(241), true, true, none);
        System.out.println("PASS: " + checked + " smart assistant routing checks");
    }
}
