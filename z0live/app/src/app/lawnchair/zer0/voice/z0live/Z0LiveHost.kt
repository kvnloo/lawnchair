package app.lawnchair.zer0.voice.z0live

import android.content.Context
import android.content.Intent
import android.content.pm.LauncherActivityInfo

/** Companion side of the shared-code seam: no launcher hide list, no Ripple panel. */
object Z0LiveHost {
    @JvmStatic
    fun visibleApps(context: Context, activities: List<LauncherActivityInfo>): List<LauncherActivityInfo> = activities

    @JvmStatic
    fun ripple(context: Context): Intent? = null
}
