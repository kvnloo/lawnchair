package app.lawnchair.zer0.voice

import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.ComposeView
import androidx.compose.ui.platform.ViewCompositionStrategy
import androidx.compose.ui.res.stringResource
import app.lawnchair.ui.preferences.components.controls.ClickablePreference
import app.lawnchair.ui.preferences.components.layout.PreferenceGroup
import app.lawnchair.ui.preferences.components.layout.PreferenceLayout
import app.lawnchair.ui.preferences.components.layout.PreferenceTemplate
import app.lawnchair.ui.theme.LawnchairTheme
import app.lawnchair.ui.util.ProvideBottomSheetHandler
import app.lawnchair.util.ProvideLifecycleState
import com.android.launcher3.R

/**
 * Presents the existing Android on-device recognizer through Lawnchair's native
 * Compose preferences. No secondary hand-built button layout or mic owner.
 */
object OnDeviceVoiceUi {
    class UiState {
        var statusResource by mutableIntStateOf(R.string.zer0_assistant_on_device_info)
        var startEnabled by mutableStateOf(true)
    }

    @JvmStatic
    fun show(activity: OnDeviceVoiceActivity): UiState {
        val state = UiState()
        val view = ComposeView(activity)
        view.setViewCompositionStrategy(ViewCompositionStrategy.DisposeOnDetachedFromWindow)
        view.setContent {
            LawnchairTheme {
                ProvideLifecycleState {
                    ProvideBottomSheetHandler {
                        OnDeviceVoicePage(activity, state)
                    }
                }
            }
        }
        activity.setContentView(view)
        return state
    }
}

@Composable
private fun OnDeviceVoicePage(
    activity: OnDeviceVoiceActivity,
    state: OnDeviceVoiceUi.UiState,
) {
    PreferenceLayout(label = stringResource(R.string.zer0_assistant_on_device_title)) {
        PreferenceGroup {
            PreferenceTemplate(
                title = { Text(stringResource(state.statusResource)) },
                description = {
                    Text(stringResource(R.string.zer0_assistant_on_device_info))
                },
            )
            if (state.startEnabled) {
                ClickablePreference(
                    label = stringResource(R.string.zer0_assistant_on_device_start),
                    onClick = activity::startFromUserTap,
                )
            }
            ClickablePreference(
                label = stringResource(R.string.zer0_assistant_on_device_cancel),
                onClick = activity::cancelAndClose,
            )
        }
    }
}
