package app.lawnchair.zer0.spaces

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.ComposeView
import androidx.compose.ui.platform.ViewCompositionStrategy
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import app.lawnchair.ui.preferences.components.controls.ClickablePreference
import app.lawnchair.ui.preferences.components.layout.PreferenceGroup
import app.lawnchair.ui.preferences.components.layout.PreferenceLayout
import app.lawnchair.ui.preferences.components.layout.PreferenceTemplate
import app.lawnchair.ui.theme.LawnchairTheme
import app.lawnchair.ui.util.ProvideBottomSheetHandler
import app.lawnchair.util.ProvideLifecycleState
import com.android.launcher3.R

/**
 * HyprSpaces presentation reuses Lawnchair's actual Compose preference elements,
 * shapes, theme, haptics and navigation instead of a manually built button screen.
 */
object HyprSpacesUi {
    @JvmStatic
    fun show(activity: HyprSpacesActivity) {
        val view = ComposeView(activity)
        view.setViewCompositionStrategy(ViewCompositionStrategy.DisposeOnDetachedFromWindow)
        view.setContent {
            LawnchairTheme {
                ProvideLifecycleState {
                    ProvideBottomSheetHandler {
                        SpacesPage(activity)
                    }
                }
            }
        }
        activity.setContentView(view)
    }
}

@Composable
private fun SpacesPage(activity: HyprSpacesActivity) {
    var query by remember(activity) { mutableStateOf(activity.uiSearchQuery()) }
    val current = activity.uiActiveName()
    PreferenceLayout(
        label = stringResource(R.string.zer0_spaces_title),
        backArrowVisible = true,
    ) {
        PreferenceGroup(
            heading = stringResource(R.string.zer0_spaces_compact_heading),
            description = stringResource(R.string.zer0_spaces_compact_note),
        ) {
            activity.uiSpaceNames().forEach { name ->
                ClickablePreference(
                    label = name,
                    subtitle = if (current == name) {
                        stringResource(R.string.zer0_spaces_current)
                    } else {
                        null
                    },
                    onClick = { activity.uiActivate(name) },
                )
            }
            ClickablePreference(
                label = stringResource(R.string.zer0_space_scratch),
                subtitle = if (current == activity.uiScratchName()) {
                    stringResource(R.string.zer0_spaces_current)
                } else {
                    stringResource(R.string.zer0_spaces_scratch_note)
                },
                onClick = { activity.uiActivate(activity.uiScratchName()) },
            )
        }
        PreferenceGroup(heading = stringResource(R.string.zer0_pins_title)) {
            val pinned = activity.uiPinnedApps()
            if (pinned.isEmpty()) {
                PreferenceTemplate(
                    title = { Text(stringResource(R.string.zer0_pins_empty)) },
                )
            } else {
                pinned.forEach { app ->
                    PreferenceTemplate(
                        title = { Text(app.label) },
                        onClick = { activity.uiOpenApp(app.component) },
                        endWidget = {
                            TextButton(onClick = { activity.uiUnpinApp(app.component) }) {
                                Text(stringResource(R.string.zer0_spaces_unpin))
                            }
                        },
                    )
                }
            }
        }
        PreferenceGroup(heading = stringResource(R.string.zer0_search_title)) {
            OutlinedTextField(
                value = query,
                onValueChange = {
                    query = it
                    activity.uiSetSearchQuery(it)
                },
                label = { Text(stringResource(R.string.zer0_search_hint)) },
                singleLine = true,
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 4.dp),
            )
            if (query.isNotBlank()) {
                activity.uiSearchApps(query).forEach { app ->
                    PreferenceTemplate(
                        title = { Text(app.label) },
                        onClick = { activity.uiOpenApp(app.component) },
                        endWidget = {
                            TextButton(onClick = { activity.uiPinApp(app.component) }) {
                                Text(stringResource(R.string.zer0_spaces_pin))
                            }
                        },
                    )
                }
            }
        }
        PreferenceGroup(heading = stringResource(R.string.zer0_spaces_actions_heading)) {
            ClickablePreference(
                label = stringResource(R.string.zer0_space_create),
                onClick = activity::uiCreateSpace,
            )
            if (activity.uiHasValidSavedPair()) {
                ClickablePreference(
                    label = stringResource(R.string.zer0_spaces_launch_pair),
                    subtitle = stringResource(R.string.zer0_spaces_pair_note),
                    onClick = activity::uiRequestSavedPair,
                )
            }
            ClickablePreference(
                label = stringResource(R.string.zer0_spaces_manage),
                subtitle = stringResource(R.string.zer0_spaces_manage_note),
                onClick = activity::uiShowAdvanced,
            )
        }
    }
}
