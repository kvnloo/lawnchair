import app.lawnchair.zer0.voice.AssistantLaunchPolicy;
import java.util.List;

/** Pure JVM selector tests; not Android Assistant or ChatGPT Voice integration proof. */
public final class AssistantLaunchPolicyTest {
    private static int checked;
    private static void check(boolean ok, String label) {
        if (!ok) throw new AssertionError(label);
        checked++;
    }

    public static void main(String[] args) {
        String chatgpt = AssistantLaunchPolicy.CHATGPT_PACKAGE;
        check(AssistantLaunchPolicy.uniqueChatGptIndex(null)
                == AssistantLaunchPolicy.NOT_FOUND, "null collection");
        check(AssistantLaunchPolicy.uniqueChatGptIndex(List.of())
                == AssistantLaunchPolicy.NOT_FOUND, "empty collection");
        check(AssistantLaunchPolicy.uniqueChatGptIndex(List.of("com.example.other"))
                == AssistantLaunchPolicy.NOT_FOUND, "never launch different app");
        check(AssistantLaunchPolicy.uniqueChatGptIndex(
                List.of("com.example.other", chatgpt)) == 1, "unique installed app");
        check(AssistantLaunchPolicy.uniqueChatGptIndex(
                List.of(chatgpt, chatgpt)) == AssistantLaunchPolicy.AMBIGUOUS,
                "multiple launcher activities fail closed");
        check(AssistantLaunchPolicy.uniqueChatGptIndex(
                List.of("com.openai.chatgpt.fake", chatgpt)) == 1,
                "no package-prefix match");
        check(AssistantLaunchPolicy.uniqueChatGptIndex(
                List.of("com.google.android.googlequicksearchbox", "com.google.android.apps.bard"))
                == AssistantLaunchPolicy.NOT_FOUND,
                "Google/Gemini are not impersonated");
        System.out.println("PASS: " + checked + " assistant handoff selector checks");
    }
}
