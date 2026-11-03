package app.lawnchair.zer0.voice.z0live

import android.content.Context
import android.content.Intent
import android.provider.Settings

/**
 * Other voice assistants on the phone, for when the owner wants something other than z0live for a moment.
 * Every app that takes ACTION_ASSIST is listed (ChatGPT, Claude, Gemini through the Google app, Grok, ...).
 * Opening one sends it an explicit ACTION_ASSIST, the same request the assistant gesture would send it.
 */
object Z0LiveAssistants {
    data class Assistant(val pkg: String, val label: String)

    fun others(context: Context): List<Assistant> {
        val pm = context.packageManager
        return pm.queryIntentActivities(Intent(Intent.ACTION_ASSIST), 0)
            .map { Assistant(it.activityInfo.packageName, it.loadLabel(pm).toString()) }
            .filter { it.pkg != context.packageName }
            .distinctBy { it.pkg }
            .sortedBy { it.label.lowercase() }
    }

    /** Opens [pkg]'s assistant. False when the app is gone or refuses the request. */
    fun open(context: Context, pkg: String): Boolean = try {
        context.startActivity(Intent(Intent.ACTION_ASSIST).setPackage(pkg).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
        true
    } catch (_: RuntimeException) {
        false
    }

    /** Android's own "digital assistant app" setting, to change the default for good. */
    fun openDefaultSetting(context: Context) {
        val intents = listOf(
            Intent(Settings.ACTION_VOICE_INPUT_SETTINGS),
            Intent(Settings.ACTION_MANAGE_DEFAULT_APPS_SETTINGS),
        )
        for (i in intents) {
            try {
                context.startActivity(i.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
                return
            } catch (_: RuntimeException) {
            }
        }
    }
}
