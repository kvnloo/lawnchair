package app.lawnchair.zer0.voice;

import android.app.Activity;
import android.content.Context;
import android.content.Intent;
import android.content.pm.LauncherActivityInfo;
import android.content.pm.LauncherApps;
import android.os.Process;
import java.util.ArrayList;
import java.util.List;

/**
 * Launch only Android's configured assistant or an unambiguous, visible ChatGPT
 * app on the current personal profile. Never touches tokens or changes OS roles.
 */
public final class AssistantHandoff {
    private AssistantHandoff() {}

    public static boolean isAndroidAssistantAvailable(Context context) {
        try {
            return context.getPackageManager().resolveActivity(
                    new Intent(Intent.ACTION_ASSIST), 0) != null;
        } catch (RuntimeException ignored) {
            return false;
        }
    }

    public static boolean openAndroidAssistant(Activity activity) {
        try {
            activity.startActivity(new Intent(Intent.ACTION_ASSIST));
            return true;
        } catch (RuntimeException ignored) {
            return false;
        }
    }

    private static final String GEMINI_PACKAGE = "com.google.android.apps.bard";

    /**
     * Android's public ACTION_ASSIST respects the selected digital assistant.
     * Google hosts Gemini as that assistant. Some One UI builds do not export an
     * ACTION_ASSIST activity; a verified Gemini launch component is a fallback,
     * NOT an undocumented shortcut into Gemini Live or an OS assistant overlay.
     */
    public static boolean openPreferredGemini(Activity activity) {
        if (openAndroidAssistant(activity)) return true;
        return openVisibleGeminiApp(activity);
    }

    public static boolean isPreferredGeminiAvailable(Context context) {
        return isAndroidAssistantAvailable(context) || visibleGeminiApp(context) != null;
    }

    private static LauncherActivityInfo visibleGeminiApp(Context context) {
        LauncherApps launcherApps = context.getSystemService(LauncherApps.class);
        if (launcherApps == null) return null;
        try {
            List<LauncherActivityInfo> installed =
                    launcherApps.getActivityList(null, Process.myUserHandle());
            if (installed == null) return null;
            List<LauncherActivityInfo> allowed =
                    VoiceAppVisibility.visibleApps(context, installed);
            LauncherActivityInfo result = null;
            for (LauncherActivityInfo item : allowed) {
                if (!GEMINI_PACKAGE.equals(item.getComponentName().getPackageName())) continue;
                // A second matching activity is ambiguous; never choose arbitrarily.
                if (result != null) return null;
                result = item;
            }
            return result;
        } catch (RuntimeException ignored) {
            return null;
        }
    }

    private static boolean openVisibleGeminiApp(Activity activity) {
        LauncherActivityInfo chosen = visibleGeminiApp(activity);
        if (chosen == null) return false;
        LauncherActivityInfo current = visibleGeminiApp(activity);
        if (current == null || !chosen.getComponentName().equals(current.getComponentName())) {
            return false;
        }
        LauncherApps launcherApps = activity.getSystemService(LauncherApps.class);
        if (launcherApps == null) return false;
        try {
            launcherApps.startMainActivity(
                    current.getComponentName(), Process.myUserHandle(), null, null);
            return true;
        } catch (RuntimeException ignored) {
            return false;
        }
    }

    private static LauncherActivityInfo visibleChatGpt(Context context) {
        LauncherApps launcherApps = context.getSystemService(LauncherApps.class);
        if (launcherApps == null) return null;
        try {
            List<LauncherActivityInfo> installed = launcherApps.getActivityList(
                    null, Process.myUserHandle());
            if (installed == null) return null;
            List<LauncherActivityInfo> allowed =
                    VoiceAppVisibility.visibleApps(context, installed);
            ArrayList<String> packages = new ArrayList<>(allowed.size());
            for (LauncherActivityInfo item : allowed) {
                packages.add(item.getComponentName().getPackageName());
            }
            int index = AssistantLaunchPolicy.uniqueChatGptIndex(packages);
            return index < 0 ? null : allowed.get(index);
        } catch (RuntimeException ignored) {
            return null;
        }
    }

    public static boolean isChatGptAvailable(Context context) {
        return visibleChatGpt(context) != null;
    }

    public static boolean openChatGpt(Activity activity) {
        LauncherActivityInfo chosen = visibleChatGpt(activity);
        if (chosen == null) return false;
        LauncherActivityInfo now = visibleChatGpt(activity);
        if (now == null || !chosen.getComponentName().equals(now.getComponentName())) {
            return false;
        }
        LauncherApps launcherApps = activity.getSystemService(LauncherApps.class);
        if (launcherApps == null) return false;
        try {
            launcherApps.startMainActivity(
                    chosen.getComponentName(), Process.myUserHandle(), null, null);
            return true;
        } catch (RuntimeException ignored) {
            return false;
        }
    }
}
