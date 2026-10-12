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
 * opens an app. Only called after exact unique-label matching, so we don't need
 * to create AppInfo records for the entire installed-app list.
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
}
