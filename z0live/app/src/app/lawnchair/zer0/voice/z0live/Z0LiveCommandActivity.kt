package app.lawnchair.zer0.voice.z0live

import android.app.Activity
import android.os.Bundle

/** Exported, invisible trampoline for z0live.action.START / MUTE / END from Lawnchair, Detent or adb. */
class Z0LiveCommandActivity : Activity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        if (savedInstanceState == null) {
            when (Z0LiveCommands.decide(intent?.action, Z0LiveStatus.get().active)) {
                Z0LiveCommands.Effect.OPEN_AND_START -> runCatching { startActivity(Z0LiveActivity.intent(this, true)) }
                Z0LiveCommands.Effect.TOGGLE_MUTE -> Z0LiveService.toggleMute(this)
                Z0LiveCommands.Effect.END -> Z0LiveService.end(this)
                Z0LiveCommands.Effect.NONE -> Unit
            }
        }
        finish()
    }
}
