package app.lawnchair.zer0.voice.z0live

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.os.SystemClock
import android.util.Log
import java.util.concurrent.Executors
import org.json.JSONObject

/**
 * adb-only latency probe for in-app control. Guarded by android.permission.DUMP in the manifest,
 * which only the shell (adb) and the system hold, so no app can drive it. It skips the voice
 * session gate but still needs the owner to have enabled the accessibility service.
 *
 * adb shell am broadcast -n app.lawnchair.play.debug/app.lawnchair.zer0.voice.z0live.Z0LiveControlProbe \
 *   --es cmd do --es goal 'tap the menu button'      (cmd: tree | do | act [--es id ID] | back | home)
 * Result JSON is the broadcast's result data and logcat tag z0live-probe.
 */
class Z0LiveControlProbe : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        val pending = goAsync()
        pool.execute {
            val started = SystemClock.elapsedRealtimeNanos()
            val out = try {
                val cmd = intent.getStringExtra("cmd").orEmpty()
                val args = JSONObject()
                val name = when (cmd) {
                    "tree" -> "phone_ui_tree"

                    "do" -> {
                        args.put("goal", intent.getStringExtra("goal").orEmpty()).put("confirm", false)
                        "phone_do"
                    }

                    "act" -> {
                        args.put("id", intent.getStringExtra("id").orEmpty()).put("action", intent.getStringExtra("action") ?: "click")
                        "phone_act"
                    }

                    "back", "home", "recents" -> {
                        args.put("action", cmd)
                        "phone_navigation"
                    }

                    else -> ""
                }
                if (name.isEmpty()) {
                    JSONObject().put("ok", false).put("status", "invalid_argument")
                } else {
                    Z0LiveAccessibilityService.probe(name, args)
                }
            } catch (exception: RuntimeException) {
                JSONObject().put("ok", false).put("status", "failed").put("error", exception.javaClass.simpleName)
            }
            out.put("probe_ms", Math.round((SystemClock.elapsedRealtimeNanos() - started) / 1e5) / 10.0)
            val text = out.toString()
            Log.i("z0live-probe", text)
            pending.resultData = text
            pending.resultCode = 1
            pending.finish()
        }
    }

    companion object {
        private val pool = Executors.newSingleThreadExecutor()
    }
}
