package app.lawnchair.zer0.voice.z0live

import android.Manifest
import android.app.Activity
import android.app.AlertDialog
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.text.InputType
import android.view.WindowManager
import android.widget.Button
import android.widget.EditText
import android.widget.LinearLayout
import android.widget.TextView

/**
 * Companion controls: pairing, Start/Mute/End and Standby. Plain views on purpose; the orb (S2) replaces this
 * as the everyday surface. Same security rules as Lawnchair's sheet: FLAG_SECURE, validated single-use pairing,
 * and only a resumed, permission-granted Activity may start the microphone.
 */
class Z0LiveActivity : Activity(), Z0LiveStatus.Observer {
    private var store: Z0LiveStore? = null
    private var resumed = false
    private var autoStart = false
    private var wantsStart = false
    private var wantsStandby = false
    private var permissionInFlight = false
    private lateinit var status: TextView
    private lateinit var start: Button
    private lateinit var mute: Button
    private lateinit var end: Button

    /** Exported no-history trampoline; untrusted links can never authorize microphone startup. */
    class LinkActivity : Activity() {
        override fun onCreate(savedInstanceState: Bundle?) {
            super.onCreate(savedInstanceState)
            window.addFlags(WindowManager.LayoutParams.FLAG_SECURE)
            val data = intent.data
            val assist = intent.action == Intent.ACTION_ASSIST && data == null && savedInstanceState == null
            val clean = intent(this, assist).addFlags(Z0LiveLaunch.RELAUNCH_FLAGS)
            try {
                when {
                    savedInstanceState != null -> clean.putExtra(EXTRA_BAD_LINK, true)
                    assist -> Unit
                    intent.action == Intent.ACTION_VIEW && data?.toString() == "z0live://voice" -> Unit
                    intent.action == Intent.ACTION_VIEW && data != null -> pendingPairing.offer(data.toString())
                    else -> clean.putExtra(EXTRA_BAD_LINK, true)
                }
            } catch (_: IllegalArgumentException) {
                clean.putExtra(EXTRA_BAD_LINK, true)
            }
            intent.data = null
            intent.replaceExtras(null as Bundle?)
            try {
                startActivity(clean)
            } catch (_: RuntimeException) {
                // A validated request stays process-local until the next visible opening.
            } finally {
                finish()
            }
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        window.addFlags(WindowManager.LayoutParams.FLAG_SECURE)
        store = runCatching { Z0LiveStore(this) }.getOrNull()
        val pad = (16 * resources.displayMetrics.density).toInt()
        val link = EditText(this).apply {
            hint = getString(R.string.z0live_pair_hint)
            inputType = InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_VARIATION_PASSWORD
        }
        status = TextView(this)
        start = button(R.string.z0live_start) { requestStart(false) }
        mute = button(R.string.z0live_mute) { Z0LiveService.toggleMute(this) }
        end = button(R.string.z0live_end) { Z0LiveService.end(this) }
        setContentView(
            LinearLayout(this).apply {
                orientation = LinearLayout.VERTICAL
                setPadding(pad, pad, pad, pad)
                addView(status)
                addView(start)
                addView(mute)
                addView(end)
                addView(button(R.string.z0live_standby_on) { requestStart(true) })
                addView(button(R.string.z0live_standby_off) { Z0LiveService.off(this@Z0LiveActivity) })
                addView(link)
                addView(
                    button(R.string.z0live_pair_review) {
                        val text = link.text.toString()
                        link.setText("")
                        try {
                            pendingPairing.offer(text)
                            consumePairing()
                        } catch (_: IllegalArgumentException) {
                            showMessage(R.string.z0live_pair_invalid)
                        }
                    },
                )
            },
        )
        captureIntent(intent, savedInstanceState == null)
    }

    private fun button(label: Int, onClick: () -> Unit) = Button(this).apply {
        text = getString(label)
        setOnClickListener { onClick() }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        captureIntent(intent, true)
        if (resumed) consumePairing()
        if (resumed && autoStart) {
            autoStart = false
            requestStart(false)
        }
    }

    private fun captureIntent(incoming: Intent, allowStart: Boolean) {
        val internal = incoming.data == null && incoming.action == INTERNAL_ACTION
        // adb/broker fallback: `am start -n app.zer0.z0live/<this> --ez autostart true [--es gesture triple_tap]`.
        val shellStart = BuildConfig.DEBUG && incoming.data == null && incoming.getBooleanExtra("autostart", false)
        val gesture = incoming.getStringExtra("gesture")
        if (shellStart && gesture == "triple_tap") Z0LiveService.end(this)
        if (shellStart && gesture != "triple_tap" && Z0LiveStatus.get().active) Z0LiveService.toggleMute(this)
        autoStart = !Z0LiveStatus.get().active && allowStart &&
            ((internal && incoming.getBooleanExtra(EXTRA_START_NOW, false)) || (shellStart && gesture != "triple_tap"))
        if (incoming.getBooleanExtra(EXTRA_BAD_LINK, false) || incoming.data != null) {
            autoStart = false
            showMessage(R.string.z0live_pair_invalid)
        }
        incoming.data = null
        incoming.replaceExtras(null as Bundle?)
        setIntent(intent(this, false))
    }

    override fun onStart() {
        super.onStart()
        Z0LiveStatus.observe(this)
    }

    override fun onResume() {
        super.onResume()
        resumed = true
        Z0LiveService.noteActivity(this)
        consumePairing()
        if (autoStart) {
            autoStart = false
            requestStart(false)
        } else if (wantsStart && !permissionInFlight) {
            continueStart()
        }
    }

    override fun onPause() {
        Z0LiveService.noteInactive(this)
        resumed = false
        super.onPause()
    }

    override fun onStop() {
        wantsStart = false
        autoStart = false
        Z0LiveStatus.remove(this)
        super.onStop() // Dismiss/background is not an End command.
    }

    override fun onChanged(value: Z0LiveStatus.Snapshot) {
        status.text = listOf(value.status, value.caption, value.error).filter { it.isNotBlank() }.joinToString("\n")
        start.isEnabled = !value.active
        mute.isEnabled = value.active
        end.isEnabled = value.active
        mute.text = getString(if (value.muted) R.string.z0live_unmute else R.string.z0live_mute)
    }

    private fun requestStart(standby: Boolean) {
        val saved = store
        if (!resumed || saved == null) {
            showMessage(R.string.z0live_storage_error)
            return
        }
        if (!standby && Z0LiveStatus.get().active) return
        if (runCatching { saved.pairing() }.getOrNull() == null) {
            showMessage(R.string.z0live_pair_first)
            return
        }
        wantsStart = true
        wantsStandby = standby
        continueStart()
    }

    private fun continueStart() {
        if (!resumed || !wantsStart || permissionInFlight) return
        val missing = buildList {
            if (checkSelfPermission(Manifest.permission.RECORD_AUDIO) != PackageManager.PERMISSION_GRANTED) add(Manifest.permission.RECORD_AUDIO)
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU &&
                checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED
            ) {
                add(Manifest.permission.POST_NOTIFICATIONS)
            }
        }
        if (missing.isNotEmpty()) {
            permissionInFlight = true
            requestPermissions(missing.toTypedArray(), PERMISSION_REQUEST)
            return
        }
        wantsStart = false
        try {
            if (wantsStandby) Z0LiveService.standby(this) else Z0LiveService.start(this)
        } catch (_: RuntimeException) {
            showMessage(R.string.z0live_start_failed)
        }
        wantsStandby = false
    }

    override fun onRequestPermissionsResult(requestCode: Int, permissions: Array<String>, grantResults: IntArray) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)
        if (requestCode != PERMISSION_REQUEST) return
        permissionInFlight = false
        val mic = checkSelfPermission(Manifest.permission.RECORD_AUDIO) == PackageManager.PERMISSION_GRANTED
        if (!mic) {
            wantsStart = false
            showMessage(R.string.z0live_mic_required)
            return
        }
        continueStart() // Notifications are optional; the call still starts without them.
    }

    private fun consumePairing() {
        if (!resumed || isFinishing || isDestroyed) return
        val value = pendingPairing.take() ?: return
        val saved = store ?: return showMessage(R.string.z0live_storage_error)
        autoStart = false
        wantsStart = false
        AlertDialog.Builder(this)
            .setTitle(R.string.z0live_pair_confirm)
            .setMessage(Uri.parse(value.backendUrl).host.orEmpty())
            .setNegativeButton(android.R.string.cancel, null)
            .setPositiveButton(R.string.z0live_pair_action) { _, _ ->
                if (Z0LiveStatus.get().active) Z0LiveService.end(this)
                try {
                    saved.pair(value)
                    showMessage(R.string.z0live_pair_saved)
                } catch (_: RuntimeException) {
                    showMessage(R.string.z0live_save_error)
                }
            }
            .show()
    }

    private fun showMessage(message: Int) {
        if (::status.isInitialized) status.text = getString(message)
    }

    companion object {
        private const val INTERNAL_ACTION = "app.lawnchair.zer0.voice.z0live.OPEN"
        private const val EXTRA_START_NOW = "app.lawnchair.zer0.voice.z0live.START_NOW"
        private const val EXTRA_BAD_LINK = "app.lawnchair.zer0.voice.z0live.BAD_LINK"
        private const val PERMISSION_REQUEST = 301
        private val pendingPairing = PendingPairing()

        @JvmStatic
        fun intent(context: Context, startNow: Boolean): Intent = Intent(context, Z0LiveActivity::class.java)
            .setAction(INTERNAL_ACTION).putExtra(EXTRA_START_NOW, startNow).addFlags(Z0LiveLaunch.CALL_FLAGS)
    }
}
