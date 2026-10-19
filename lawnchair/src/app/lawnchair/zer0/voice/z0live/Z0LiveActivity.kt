package app.lawnchair.zer0.voice.z0live

import android.Manifest
import android.app.Activity
import android.content.Context
import android.content.Intent
import android.content.pm.LauncherApps
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import android.os.Process
import android.view.WindowManager
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.CallEnd
import androidx.compose.material.icons.rounded.Mic
import androidx.compose.material.icons.rounded.MicOff
import androidx.compose.material.icons.rounded.Settings
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import app.lawnchair.ui.preferences.PreferenceActivity
import app.lawnchair.ui.preferences.components.controls.ClickablePreference
import app.lawnchair.ui.preferences.components.layout.PreferenceGroup
import app.lawnchair.ui.preferences.navigation.Z0Live
import app.lawnchair.ui.theme.EdgeToEdge
import app.lawnchair.ui.theme.LawnchairTheme
import app.lawnchair.zer0.voice.VoiceAppVisibility
import com.android.launcher3.R
import com.google.mlkit.vision.barcode.common.Barcode
import com.google.mlkit.vision.codescanner.GmsBarcodeScannerOptions
import com.google.mlkit.vision.codescanner.GmsBarcodeScanning

/** Visible permission and pairing admission. Audio belongs to the foreground service, not this UI. */
class Z0LiveActivity : ComponentActivity(), Z0LiveStatus.Observer {
    private var store: Z0LiveStore? = null
    private var snapshot by mutableStateOf(Z0LiveStatus.get())
    private var localMessage by mutableStateOf<String?>(null)
    private var pairingVisible by mutableStateOf(false)
    // Deliberately not rememberSaveable, intent extras, or savedInstanceState.
    private var pairingText by mutableStateOf("")
    private var confirmation by mutableStateOf<PairingValidation.Pairing?>(null)
    private var forgetConfirmation by mutableStateOf(false)
    private var scanning by mutableStateOf(false)
    private var changingPairing by mutableStateOf(false)
    private var replacement: PairingValidation.Pairing? = null
    private var clearAfterEnd = false
    private var resumed = false
    private var autoStart = false
    private var wantsStart = false
    private var permissionInFlight = false
    private var notificationAsked = false

    /** Exported no-history trampoline; untrusted links can never authorize microphone startup. */
    class LinkActivity : Activity() {
        override fun onCreate(savedInstanceState: Bundle?) {
            super.onCreate(savedInstanceState)
            window.addFlags(WindowManager.LayoutParams.FLAG_SECURE)
            val incoming = intent
            val data = incoming.data
            val clean = intent(this, false).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            try {
                when {
                    savedInstanceState != null -> clean.putExtra(EXTRA_BAD_LINK, true)
                    incoming.action == Intent.ACTION_ASSIST && data == null -> Unit
                    incoming.action == Intent.ACTION_VIEW && data?.toString() == "z0live://voice" -> Unit
                    incoming.action == Intent.ACTION_VIEW && data != null -> pendingPairing.offer(data.toString())
                    else -> clean.putExtra(EXTRA_BAD_LINK, true)
                }
            } catch (_: IllegalArgumentException) {
                clean.putExtra(EXTRA_BAD_LINK, true)
            }
            incoming.data = null
            incoming.clipData = null
            incoming.replaceExtras(null as Bundle?)
            setIntent(Intent(this, LinkActivity::class.java))
            try {
                startActivity(clean)
            } catch (_: RuntimeException) {
                // A validated request stays process-local until the next visible opening.
            } finally {
                if (isTaskRoot) finishAndRemoveTask() else finish()
            }
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        window.addFlags(WindowManager.LayoutParams.FLAG_SECURE)
        enableEdgeToEdge()
        try {
            store = Z0LiveStore(this)
        } catch (_: RuntimeException) {
            showMessage(R.string.z0live_storage_error)
        }
        captureIntent(intent, savedInstanceState == null)
        setContent {
            LawnchairTheme {
                EdgeToEdge()
                SessionSheet()
            }
        }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        autoStart = false
        wantsStart = false
        captureIntent(intent, true)
        if (resumed) consumePairing()
        if (resumed && autoStart) {
            autoStart = false
            requestStart()
        }
    }

    private fun captureIntent(incoming: Intent, allowInternalStart: Boolean) {
        val data = incoming.data
        val internal = data == null && incoming.component?.className == javaClass.name && incoming.action == INTERNAL_ACTION
        val badLink = incoming.getBooleanExtra(EXTRA_BAD_LINK, false)
        autoStart = allowInternalStart && internal && incoming.getBooleanExtra(EXTRA_START_NOW, false)
        if (internal && incoming.getBooleanExtra(EXTRA_PAIR, false)) pairingVisible = true
        incoming.data = null
        incoming.clipData = null
        incoming.replaceExtras(null as Bundle?)
        setIntent(intent(this, false).addFlags(Intent.FLAG_ACTIVITY_EXCLUDE_FROM_RECENTS))
        if (data != null || badLink) {
            autoStart = false
            pairingVisible = true
            showMessage(R.string.z0live_pair_invalid)
        }
    }

    override fun onStart() {
        super.onStart()
        Z0LiveStatus.observe(this)
    }

    override fun onResume() {
        super.onResume()
        resumed = true
        consumePairing()
        onChanged(Z0LiveStatus.get())
        if (snapshot.pendingNetwork && snapshot.active) Z0LiveService.retry(this)
        if (autoStart) {
            autoStart = false
            requestStart()
        } else if (wantsStart && !permissionInFlight) {
            continueStart()
        }
    }

    override fun onPause() {
        resumed = false
        super.onPause()
    }

    override fun onStop() {
        wantsStart = false
        autoStart = false
        pairingText = ""
        Z0LiveStatus.remove(this)
        // Dismiss/background is not an End session command.
        super.onStop()
    }

    override fun onDestroy() {
        confirmation = null
        replacement = null
        changingPairing = false
        super.onDestroy()
    }

    private fun requestStart() {
        if (!resumed || store == null || changingPairing || snapshot.active) return
        try {
            if (store?.pairing() == null) {
                pairingVisible = true
                showMessage(R.string.z0live_pair_first)
                return
            }
        } catch (_: RuntimeException) {
            showMessage(R.string.z0live_storage_error)
            return
        }
        wantsStart = true
        notificationAsked = false
        continueStart()
    }

    private fun continueStart() {
        if (!resumed || !wantsStart || permissionInFlight) return
        if (checkSelfPermission(Manifest.permission.RECORD_AUDIO) != PackageManager.PERMISSION_GRANTED) {
            permissionInFlight = true
            requestPermissions(arrayOf(Manifest.permission.RECORD_AUDIO), MICROPHONE_REQUEST)
            return
        }
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU && !notificationAsked &&
            checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED
        ) {
            notificationAsked = true
            permissionInFlight = true
            requestPermissions(arrayOf(Manifest.permission.POST_NOTIFICATIONS), NOTIFICATION_REQUEST)
            return
        }
        wantsStart = false
        localMessage = null
        try {
            Z0LiveService.start(this)
        } catch (_: RuntimeException) {
            showMessage(R.string.z0live_start_failed)
        }
    }

    override fun onRequestPermissionsResult(requestCode: Int, permissions: Array<out String>, grantResults: IntArray) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)
        if (requestCode != MICROPHONE_REQUEST && requestCode != NOTIFICATION_REQUEST) return
        permissionInFlight = false
        if (requestCode == MICROPHONE_REQUEST && grantResults.firstOrNull() != PackageManager.PERMISSION_GRANTED) {
            wantsStart = false
            showMessage(R.string.z0live_mic_required)
            return
        }
        continueStart()
    }

    override fun onChanged(value: Z0LiveStatus.Snapshot) {
        if (changingPairing && !value.active) saveReplacement()
        snapshot = value
    }

    private fun consumePairing() {
        if (!resumed || isFinishing || isDestroyed) return
        val value = pendingPairing.take() ?: return
        autoStart = false
        wantsStart = false
        pairingVisible = true
        if (store == null) showMessage(R.string.z0live_storage_error) else confirmation = value
    }

    private fun reviewPairing(link: String) {
        pairingText = ""
        try {
            pendingPairing.offer(link)
            consumePairing()
        } catch (_: IllegalArgumentException) {
            showMessage(R.string.z0live_pair_invalid)
        }
    }

    private fun scanPairing() {
        if (scanning || store == null || changingPairing) return
        scanning = true
        val options = GmsBarcodeScannerOptions.Builder().setBarcodeFormats(Barcode.FORMAT_QR_CODE).build()
        try {
            GmsBarcodeScanning.getClient(this, options).startScan()
                .addOnSuccessListener { barcode ->
                    if (!isDestroyed) {
                        scanning = false
                        reviewPairing(barcode.rawValue.orEmpty())
                    }
                }
                .addOnCanceledListener { scanning = false }
                .addOnFailureListener {
                    if (!isDestroyed) {
                        scanning = false
                        showMessage(R.string.z0live_pair_scan_failed)
                    }
                }
        } catch (_: RuntimeException) {
            scanning = false
            showMessage(R.string.z0live_pair_scan_failed)
        }
    }

    private fun replacePairing(pairing: PairingValidation.Pairing?, clear: Boolean) {
        if (store == null || changingPairing) return
        wantsStart = false
        autoStart = false
        replacement = pairing
        clearAfterEnd = clear
        changingPairing = true
        showMessage(R.string.z0live_pair_stopping)
        Z0LiveService.end(this)
        onChanged(Z0LiveStatus.get())
    }

    private fun saveReplacement() {
        changingPairing = false
        val value = replacement
        replacement = null
        try {
            val settings = checkNotNull(store)
            if (clearAfterEnd) settings.clearPairing() else settings.pair(checkNotNull(value))
            showMessage(if (clearAfterEnd) R.string.z0live_pair_first else R.string.z0live_pair_saved)
        } catch (_: RuntimeException) {
            showMessage(R.string.z0live_save_error)
        }
    }

    private fun openTailscale() {
        try {
            val launcherApps = getSystemService(LauncherApps::class.java)
            val launch = packageManager.getLaunchIntentForPackage("com.tailscale.ipn")
            val activities = launcherApps?.getActivityList("com.tailscale.ipn", Process.myUserHandle())
            if (launch?.component != null && activities != null &&
                VoiceAppVisibility.visibleApps(this, activities).any { it.componentName == launch.component }
            ) {
                startActivity(launch)
                return
            }
        } catch (_: RuntimeException) {
            // Never bypass Lawnchair hidden-app restrictions.
        }
        showMessage(R.string.z0live_tailscale_unavailable)
    }

    private fun showMessage(resource: Int) {
        localMessage = getString(resource)
    }

    @OptIn(ExperimentalMaterial3Api::class)
    @Composable
    private fun SessionSheet() {
        ModalBottomSheet(
            onDismissRequest = { finish() },
            sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true),
        ) {
            Column(
                modifier = Modifier.fillMaxWidth().verticalScroll(rememberScrollState()).padding(bottom = 24.dp),
                verticalArrangement = Arrangement.spacedBy(16.dp),
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth().padding(horizontal = 24.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text(stringResource(R.string.z0live_title), style = MaterialTheme.typography.titleLarge, modifier = Modifier.weight(1f))
                    IconButton(onClick = { startActivity(PreferenceActivity.createIntent(this@Z0LiveActivity, Z0Live)) }) {
                        Icon(Icons.Rounded.Settings, stringResource(R.string.z0live_settings))
                    }
                }
                Column(
                    modifier = Modifier.padding(horizontal = 24.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp),
                ) {
                    Text(
                        snapshot.status.takeUnless { it.isNullOrBlank() } ?: stringResource(R.string.z0live_ready),
                        style = MaterialTheme.typography.titleMedium,
                        modifier = Modifier.semantics { liveRegion = LiveRegionMode.Polite },
                    )
                    if (!snapshot.caption.isNullOrBlank()) {
                        SelectionContainer {
                            Text(
                                snapshot.caption,
                                style = MaterialTheme.typography.bodyLarge,
                                modifier = Modifier.heightIn(max = 240.dp).verticalScroll(rememberScrollState())
                                    .semantics { liveRegion = LiveRegionMode.Polite },
                            )
                        }
                    }
                    val detail = localMessage ?: snapshot.error
                    if (!detail.isNullOrBlank()) {
                        Text(detail, color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodyMedium)
                    }
                    if (snapshot.telemetryFailed > 0) {
                        Text(stringResource(R.string.z0live_telemetry_failed, snapshot.telemetryFailed), style = MaterialTheme.typography.bodySmall)
                    }
                    Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                        if (snapshot.active) {
                            OutlinedButton(onClick = { Z0LiveService.toggleMute(this@Z0LiveActivity) }, modifier = Modifier.weight(1f)) {
                                Icon(if (snapshot.muted) Icons.Rounded.MicOff else Icons.Rounded.Mic, contentDescription = null)
                                Text(stringResource(if (snapshot.muted) R.string.z0live_unmute else R.string.z0live_mute), Modifier.padding(start = 8.dp))
                            }
                            Button(onClick = {
                                wantsStart = false
                                autoStart = false
                                Z0LiveService.end(this@Z0LiveActivity)
                            }, modifier = Modifier.weight(1f)) {
                                Icon(Icons.Rounded.CallEnd, contentDescription = null)
                                Text(stringResource(R.string.z0live_end), Modifier.padding(start = 8.dp))
                            }
                        } else {
                            Button(onClick = ::requestStart, enabled = store != null && !changingPairing, modifier = Modifier.fillMaxWidth()) {
                                Icon(Icons.Rounded.Mic, contentDescription = null)
                                Text(stringResource(R.string.z0live_start), Modifier.padding(start = 8.dp))
                            }
                        }
                    }
                }
                if (snapshot.pendingNetwork) {
                    PreferenceGroup(description = stringResource(R.string.z0live_network_help)) {
                        ClickablePreference(stringResource(R.string.z0live_tailscale), onClick = ::openTailscale)
                        ClickablePreference(stringResource(R.string.z0live_retry), onClick = { Z0LiveService.retry(this@Z0LiveActivity) })
                    }
                }
                if (pairingVisible) {
                    PreferenceGroup(heading = stringResource(R.string.z0live_pairing)) {
                        ClickablePreference(stringResource(R.string.z0live_pair_scan), onClick = ::scanPairing)
                    }
                    Column(Modifier.padding(horizontal = 24.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        OutlinedTextField(
                            value = pairingText,
                            onValueChange = { pairingText = it },
                            label = { Text(stringResource(R.string.z0live_pair_hint)) },
                            singleLine = true,
                            enabled = store != null && !changingPairing,
                            visualTransformation = PasswordVisualTransformation(),
                            keyboardOptions = KeyboardOptions(autoCorrectEnabled = false, keyboardType = KeyboardType.Password),
                            modifier = Modifier.fillMaxWidth(),
                        )
                        Button(onClick = { reviewPairing(pairingText) }, enabled = pairingText.isNotBlank() && store != null && !changingPairing) {
                            Text(stringResource(R.string.z0live_pair_review))
                        }
                    }
                    PreferenceGroup {
                        ClickablePreference(stringResource(R.string.z0live_forget), onClick = { forgetConfirmation = true })
                    }
                }
            }
        }
        confirmation?.let { pairing ->
            AlertDialog(
                onDismissRequest = { confirmation = null },
                title = { Text(stringResource(R.string.z0live_pair_confirm)) },
                text = { Text(stringResource(R.string.z0live_pair_explanation, pairing.backendUrl)) },
                confirmButton = {
                    TextButton(onClick = {
                        confirmation = null
                        replacePairing(pairing, false)
                    }) { Text(stringResource(R.string.z0live_pair_action)) }
                },
                dismissButton = {
                    TextButton(onClick = { confirmation = null }) { Text(stringResource(android.R.string.cancel)) }
                },
            )
        }
        if (forgetConfirmation) {
            AlertDialog(
                onDismissRequest = { forgetConfirmation = false },
                title = { Text(stringResource(R.string.z0live_forget_confirm)) },
                text = { Text(stringResource(R.string.z0live_forget_explanation)) },
                confirmButton = {
                    TextButton(onClick = {
                        forgetConfirmation = false
                        replacePairing(null, true)
                    }) { Text(stringResource(R.string.z0live_forget)) }
                },
                dismissButton = {
                    TextButton(onClick = { forgetConfirmation = false }) { Text(stringResource(android.R.string.cancel)) }
                },
            )
        }
    }

    companion object {
        private const val INTERNAL_ACTION = "app.lawnchair.zer0.voice.z0live.OPEN"
        private const val EXTRA_START_NOW = "app.lawnchair.zer0.voice.z0live.START_NOW"
        private const val EXTRA_BAD_LINK = "app.lawnchair.zer0.voice.z0live.BAD_LINK"
        private const val EXTRA_PAIR = "app.lawnchair.zer0.voice.z0live.PAIR"
        private const val MICROPHONE_REQUEST = 301
        private const val NOTIFICATION_REQUEST = 302
        private val pendingPairing = PendingPairing()

        @JvmStatic
        fun intent(context: Context, startNow: Boolean): Intent = Intent(context, Z0LiveActivity::class.java)
            .setAction(INTERNAL_ACTION).putExtra(EXTRA_START_NOW, startNow)

        @JvmStatic
        fun pairingIntent(context: Context): Intent = intent(context, false).putExtra(EXTRA_PAIR, true)
    }
}
