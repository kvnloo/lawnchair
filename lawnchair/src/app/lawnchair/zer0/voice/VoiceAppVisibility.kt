package app.lawnchair.zer0.voice

import android.content.Context
import android.content.Intent
import android.content.pm.LauncherActivityInfo
import app.lawnchair.preferences2.PreferenceManager2
import app.lawnchair.preferences2.firstCached
import app.lawnchair.search.algorithms.filterHiddenApps
import com.android.launcher3.model.data.AppInfo
import java.util.Locale

/**
 * Apply the same app-visibility policy as launcher search before a voice command
 * opens an app. The exact-label voice path checks one target. The separate
 * spaces picker evaluates installed apps in one preference snapshot per scan.
 *
 * A preference lookup failure must never bypass a hidden-app restriction.
 */
object VoiceAppVisibility {
    @JvmStatic
    fun isAllowed(
        context: Context,
        activity: LauncherActivityInfo,
        requestedLabel: String,
    ): Boolean = runCatching {
        val prefs = PreferenceManager2.getInstance(context)
        val label = activity.label ?: return@runCatching false
        val app = AppInfo(
            activity.componentName,
            label,
            activity.user,
            Intent(),
        )
        sequenceOf(app)
            .filterHiddenApps(
                requestedLabel.lowercase(Locale.getDefault()),
                prefs.hiddenApps.firstCached(),
                prefs.hiddenAppsInSearch.firstCached(),
            ).any()
    }.getOrDefault(false)

    /**
     * Visible personal-profile launcher activities for a zero-query picker. Reuses
     * Lawnchair's shared search policy without reading preference flows once per app.
     *
     * The caller must supply ONLY the current personal profile, re-evaluate before
     * execution, and not show any app labels on preference read failure.
     */
    @JvmStatic
    fun visibleApps(
        context: Context,
        activities: List<LauncherActivityInfo>,
    ): List<LauncherActivityInfo> = runCatching {
        val prefs = PreferenceManager2.getInstance(context)
        val hidden = prefs.hiddenApps.firstCached()
        val policy = prefs.hiddenAppsInSearch.firstCached()
        activities.filter { activity ->
            // Preserve per-app failure isolation. One stale/broken package label must
            // not blank the entire picker, while missing preferences still fail closed.
            runCatching {
                val label = activity.label
                if (label == null || label.isEmpty()) {
                    false
                } else {
                    val app = AppInfo(
                        activity.componentName,
                        label,
                        activity.user,
                        Intent(),
                    )
                    sequenceOf(app).filterHiddenApps("", hidden, policy).any()
                }
            }.getOrDefault(false)
        }
    }.getOrDefault(emptyList())
}
