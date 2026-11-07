package app.lawnchair.zer0.voice.z0live

import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager

/** Lawnchair's sender for the companion app's launcher contract (z0live/README.md). */
object Z0LiveCompanion {
    @JvmStatic
    fun intent(action: String): Intent = Intent(action)
        .setPackage(Z0LiveCommands.PACKAGE)
        .addFlags(Z0LiveCommands.SEND_FLAGS)

    @JvmStatic
    fun installed(context: Context): Boolean = try {
        context.packageManager.getPackageInfo(Z0LiveCommands.PACKAGE, 0)
        true
    } catch (_: PackageManager.NameNotFoundException) {
        false
    }

    /** False when the companion is missing or refused the intent; the caller shows its own error. */
    @JvmStatic
    fun send(context: Context, action: String): Boolean = runCatching { context.startActivity(intent(action)) }.isSuccess

    @JvmStatic
    fun start(context: Context) = send(context, Z0LiveCommands.START)

    @JvmStatic
    fun mute(context: Context) = send(context, Z0LiveCommands.MUTE)

    @JvmStatic
    fun end(context: Context) = send(context, Z0LiveCommands.END)
}
