package app.lawnchair.zer0.voice;

import java.util.List;

/**
 * Exact personal-profile package resolution for an explicitly chosen assistant app.
 * Inputs must already be filtered by the launcher's hidden-app visibility policy.
 * A missing or ambiguous candidate is never launched arbitrarily.
 */
public final class AssistantLaunchPolicy {
    public static final String CHATGPT_PACKAGE = "com.openai.chatgpt";
    public static final int NOT_FOUND = -1;
    public static final int AMBIGUOUS = -2;

    private AssistantLaunchPolicy() {}

    public static int uniqueChatGptIndex(List<String> visiblePersonalProfilePackages) {
        if (visiblePersonalProfilePackages == null) return NOT_FOUND;
        int match = NOT_FOUND;
        for (int i = 0; i < visiblePersonalProfilePackages.size(); i++) {
            if (!CHATGPT_PACKAGE.equals(visiblePersonalProfilePackages.get(i))) continue;
            if (match != NOT_FOUND) return AMBIGUOUS;
            match = i;
        }
        return match;
    }
}
