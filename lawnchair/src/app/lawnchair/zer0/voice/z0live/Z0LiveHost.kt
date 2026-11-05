package app.lawnchair.zer0.voice.z0live

import android.content.Context
import android.content.Intent
import android.content.pm.LauncherActivityInfo
import app.lawnchair.zer0.voice.VoiceAppVisibility

/** Lawnchair's side of the seam shared z0live code calls: launcher app-hiding policy and the Ripple panel. */
object Z0LiveHost {
    @JvmStatic
    fun visibleApps(context: Context, activities: List<LauncherActivityInfo>): List<LauncherActivityInfo> = VoiceAppVisibility.visibleApps(context, activities)

    @JvmStatic
    fun ripple(context: Context): Intent? = Intent(context, RippleActivity::class.java)
}
