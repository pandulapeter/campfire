package com.pandulapeter.campfire.presentation.screens

import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.pandulapeter.campfire.shared.ui.CampfireViewModelStateHolder
import com.pandulapeter.campfire.shared.ui.screenComponents.settings.SettingsContentList

@Composable
internal fun SettingsScreensDesktop(
    modifier: Modifier = Modifier,
    stateHolder: CampfireViewModelStateHolder
) = SettingsContentList(
    modifier = modifier.padding(8.dp),
    uiStrings = stateHolder.uiStrings.value,
    databases = stateHolder.databases.value,
    onDatabaseEnabledChanged = stateHolder::onDatabaseEnabledChanged,
    selectedUiMode = stateHolder.userPreferences.value?.uiMode,
    onSelectedUiModeChanged = stateHolder::onUiModeChanged
)