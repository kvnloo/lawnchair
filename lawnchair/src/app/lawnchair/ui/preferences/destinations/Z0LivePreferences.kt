package app.lawnchair.ui.preferences.destinations

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.provider.Settings
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.window.DialogProperties
import androidx.compose.ui.window.SecureFlagPolicy
import androidx.lifecycle.compose.LifecycleResumeEffect
import app.lawnchair.ui.preferences.components.controls.ClickablePreference
import app.lawnchair.ui.preferences.components.controls.ListPreference
import app.lawnchair.ui.preferences.components.controls.ListPreferenceEntry
import app.lawnchair.ui.preferences.components.controls.SwitchPreference
import app.lawnchair.ui.preferences.components.controls.WarningPreference
import app.lawnchair.ui.preferences.components.layout.PreferenceGroup
import app.lawnchair.ui.preferences.components.layout.PreferenceLayout
import app.lawnchair.zer0.voice.z0live.RippleActivity
import app.lawnchair.zer0.voice.z0live.Z0LiveAccessibilityService
import app.lawnchair.zer0.voice.z0live.Z0LiveActivity
import app.lawnchair.zer0.voice.z0live.Z0LiveStatus
import app.lawnchair.zer0.voice.z0live.Z0LiveStore
import app.lawnchair.zer0.voice.z0live.Z0LiveTransport
import com.android.launcher3.R

/** Settings contain no pairing token. Pairing admission always opens the secure, internal activity. */
@Composable
fun Z0LivePreferences() {
    val context = LocalContext.current
    val store = remember { runCatching { Z0LiveStore(context) }.getOrNull() }
    var backend by remember { mutableStateOf<String?>(null) }
    var ripple by remember { mutableStateOf("") }
    var transport by remember { mutableStateOf(Z0LiveTransport.AUTO) }
    var message by remember { mutableStateOf<Int?>(null) }
    var editRipple by remember { mutableStateOf(false) }
    var rippleDraft by remember { mutableStateOf("") }
    var rippleError by remember { mutableStateOf<Int?>(null) }
    var navigationAllowed by remember { mutableStateOf(false) }
    var navigationConsent by remember { mutableStateOf(false) }
    var cameraGranted by remember { mutableStateOf(false) }
    val cameraPermission = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) {
        cameraGranted = it
    }

    DisposableEffect(Unit) {
        val observer = Z0LiveStatus.Observer {
            navigationAllowed = Z0LiveAccessibilityService.sessionAllowed()
            if (!it.active) navigationConsent = false
        }
        Z0LiveStatus.observe(observer)
        onDispose { Z0LiveStatus.remove(observer) }
    }

    fun launch(intent: Intent) {
        try {
            context.startActivity(intent)
        } catch (_: RuntimeException) {
            message = R.string.z0live_settings_unavailable
        }
    }

    LifecycleResumeEffect(store) {
        try {
            checkNotNull(store)
            backend = store.pairing()?.backendUrl
            ripple = store.rippleUrl().orEmpty()
            transport = store.transport()
            navigationAllowed = Z0LiveAccessibilityService.sessionAllowed()
        } catch (_: RuntimeException) {
            message = R.string.z0live_storage_error
        }
        cameraGranted = context.checkSelfPermission(Manifest.permission.CAMERA) == PackageManager.PERMISSION_GRANTED
        onPauseOrDispose {
            // Do not retain unsaved URL input across backgrounding.
            rippleDraft = ""
            editRipple = false
        }
    }

    PreferenceLayout(label = stringResource(R.string.z0live_title)) {
        message?.let {
            PreferenceGroup { WarningPreference(stringResource(it)) }
        }
        PreferenceGroup(heading = stringResource(R.string.z0live_voice)) {
            ClickablePreference(
                label = stringResource(R.string.z0live_session_controls),
                subtitle = stringResource(R.string.z0live_session_description),
                onClick = { launch(Z0LiveActivity.intent(context, false)) },
            )
            ListPreference(
                entries = listOf(
                    ListPreferenceEntry(Z0LiveTransport.AUTO) { stringResource(R.string.z0live_transport_auto) },
                    ListPreferenceEntry(Z0LiveTransport.REALTIME) { stringResource(R.string.z0live_transport_realtime) },
                    ListPreferenceEntry(Z0LiveTransport.BIDI) { stringResource(R.string.z0live_transport_bidi) },
                ),
                value = transport,
                onValueChange = { chosen ->
                    try {
                        checkNotNull(store).setTransport(chosen)
                        transport = chosen
                    } catch (_: RuntimeException) {
                        message = R.string.z0live_storage_error
                    }
                },
                label = stringResource(R.string.z0live_transport),
            )
        }
        PreferenceGroup(
            heading = stringResource(R.string.z0live_pairing),
            description = stringResource(R.string.z0live_pairing_description),
        ) {
            ClickablePreference(
                label = stringResource(R.string.z0live_manage_pairing),
                subtitle = backend?.let { stringResource(R.string.z0live_paired_backend, it) }
                    ?: stringResource(R.string.z0live_pair_first),
                onClick = { launch(Z0LiveActivity.pairingIntent(context)) },
            )
        }
        PreferenceGroup(heading = stringResource(R.string.z0live_ripple)) {
            ClickablePreference(
                label = stringResource(R.string.z0live_ripple_hint),
                subtitle = ripple.ifBlank { stringResource(R.string.z0live_ripple_missing) },
                onClick = {
                    rippleDraft = ripple
                    rippleError = null
                    editRipple = true
                },
            )
            ClickablePreference(
                label = stringResource(R.string.z0live_open_ripple),
                onClick = { launch(Intent(context, RippleActivity::class.java)) },
            )
        }
        PreferenceGroup(heading = stringResource(R.string.z0live_phone_tools)) {
            SwitchPreference(
                checked = navigationAllowed,
                onCheckedChange = { enabled ->
                    if (enabled) {
                        navigationConsent = true
                    } else {
                        try {
                            Z0LiveAccessibilityService.allowCurrentSession(false)
                            navigationAllowed = false
                        } catch (_: RuntimeException) {
                            message = R.string.z0live_save_error
                        }
                    }
                },
                label = stringResource(R.string.z0live_phone_navigation),
                description = stringResource(R.string.z0live_phone_navigation_description),
            )
            ClickablePreference(
                label = stringResource(R.string.z0live_accessibility_settings),
                onClick = { launch(Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS)) },
            )
            ClickablePreference(
                label = stringResource(R.string.z0live_flashlight_permission),
                subtitle = stringResource(if (cameraGranted) R.string.z0live_flashlight_granted else R.string.z0live_flashlight_description),
                onClick = {
                    if (cameraGranted) {
                        launch(Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS, Uri.parse("package:${context.packageName}")))
                    } else {
                        cameraPermission.launch(Manifest.permission.CAMERA)
                    }
                },
            )
        }
    }

    if (navigationConsent) {
        AlertDialog(
            onDismissRequest = { navigationConsent = false },
            title = { Text(stringResource(R.string.z0live_phone_navigation)) },
            text = { Text(stringResource(R.string.z0live_navigation_consent)) },
            confirmButton = {
                TextButton(onClick = {
                    navigationConsent = false
                    try {
                        navigationAllowed = Z0LiveAccessibilityService.allowCurrentSession(true)
                        if (!navigationAllowed) message = R.string.z0live_control_permission_required
                    } catch (_: RuntimeException) {
                        message = R.string.z0live_save_error
                    }
                }) { Text(stringResource(R.string.z0live_navigation_allow)) }
            },
            dismissButton = {
                TextButton(onClick = { navigationConsent = false }) { Text(stringResource(android.R.string.cancel)) }
            },
        )
    }
    if (editRipple) {
        AlertDialog(
            onDismissRequest = {
                editRipple = false
                rippleDraft = ""
            },
            properties = DialogProperties(securePolicy = SecureFlagPolicy.SecureOn),
            title = { Text(stringResource(R.string.z0live_ripple_hint)) },
            text = {
                OutlinedTextField(
                    value = rippleDraft,
                    onValueChange = {
                        rippleDraft = it
                        rippleError = null
                    },
                    isError = rippleError != null,
                    supportingText = rippleError?.let { { Text(stringResource(it)) } },
                    label = { Text(stringResource(R.string.z0live_ripple_hint)) },
                    singleLine = true,
                    keyboardOptions = KeyboardOptions(autoCorrectEnabled = false, keyboardType = KeyboardType.Uri),
                    modifier = Modifier.fillMaxWidth(),
                )
            },
            confirmButton = {
                TextButton(onClick = {
                    try {
                        checkNotNull(store).setRippleUrl(rippleDraft)
                        ripple = store.rippleUrl().orEmpty()
                        editRipple = false
                        rippleDraft = ""
                        message = null
                    } catch (_: IllegalArgumentException) {
                        rippleError = R.string.z0live_ripple_invalid
                    } catch (_: RuntimeException) {
                        rippleError = R.string.z0live_save_error
                    }
                }) { Text(stringResource(R.string.z0live_ripple_save)) }
            },
            dismissButton = {
                TextButton(onClick = {
                    editRipple = false
                    rippleDraft = ""
                }) { Text(stringResource(android.R.string.cancel)) }
            },
        )
    }
}
