package com.pandulapeter.campfire.presentation.screens

import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import com.pandulapeter.campfire.shared.ui.CampfireViewModelStateHolder
import com.pandulapeter.campfire.shared.ui.screenComponents.settings.SettingsContentList

@Composable
internal fun SettingsScreensDesktop(
    modifier: Modifier = Modifier,
    stateHolder: CampfireViewModelStateHolder,
    urlOpener: (String) -> Unit
) = SettingsContentList(
    modifier = modifier,
    uiStrings = stateHolder.uiStrings.value,
    databases = stateHolder.databases.value,
    onDatabaseEnabledChanged = stateHolder::onDatabaseEnabledChanged,
    onAddDatabaseClicked = stateHolder::onAddDatabaseClicked,
    onDatabaseRemoved = stateHolder::onDatabaseRemoved,
    selectedUiMode = stateHolder.userPreferences.value?.uiMode,
    onSelectedUiModeChanged = stateHolder::onUiModeChanged,
    selectedLanguage = stateHolder.userPreferences.value?.language,
    onSelectedLanguageChanged = stateHolder::onLanguageChanged,
    urlOpener = urlOpener
)