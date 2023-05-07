package com.pandulapeter.campfire.shared.ui.screenComponents.settings

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material.DismissDirection
import androidx.compose.material.DismissValue
import androidx.compose.material.ExperimentalMaterialApi
import androidx.compose.material.FractionalThreshold
import androidx.compose.material.Icon
import androidx.compose.material.IconButton
import androidx.compose.material.SwipeToDismiss
import androidx.compose.material.rememberDismissState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import com.pandulapeter.campfire.data.model.domain.Database
import com.pandulapeter.campfire.data.model.domain.UserPreferences
import com.pandulapeter.campfire.shared.ui.catalogue.components.CheckboxItem
import com.pandulapeter.campfire.shared.ui.catalogue.components.ClickableControlItem
import com.pandulapeter.campfire.shared.ui.catalogue.components.HeaderItem
import com.pandulapeter.campfire.shared.ui.catalogue.components.RadioButtonItem
import com.pandulapeter.campfire.shared.ui.catalogue.resources.CampfireIcons
import com.pandulapeter.campfire.shared.ui.catalogue.resources.CampfireStrings


@OptIn(ExperimentalFoundationApi::class, ExperimentalMaterialApi::class)
@Composable
fun SettingsContentList(
    modifier: Modifier = Modifier,
    uiStrings: CampfireStrings,
    databases: List<Database>,
    onDatabaseEnabledChanged: (Database, Boolean) -> Unit,
    onAddDatabaseClicked: () -> Unit,
    onDatabaseRemoved: (String) -> Unit,
    selectedUiMode: UserPreferences.UiMode?,
    onSelectedUiModeChanged: (UserPreferences.UiMode) -> Unit,
    selectedLanguage: UserPreferences.Language?,
    onSelectedLanguageChanged: (UserPreferences.Language) -> Unit
) = LazyColumn(
    modifier = modifier.fillMaxWidth(),
    horizontalAlignment = Alignment.CenterHorizontally
) {
    item(key = "header_databases") {
        HeaderItem(
            modifier = Modifier.fillMaxWidth().animateItemPlacement(),
            text = uiStrings.settingsActiveDatabases
        )
    }
    databases.forEach { database ->
        val key = DatabaseItemKey(database.url)
        item(key = key.key) {
            val currentItem = rememberUpdatedState(key)
            SwipeToDismiss(
                directions = if (database.isAddedByUser) setOf(DismissDirection.StartToEnd) else emptySet(),
                dismissThresholds = { FractionalThreshold(0.4f) },
                state = rememberDismissState(
                    confirmStateChange = { dismissValue ->
                        when (dismissValue) {
                            DismissValue.Default -> false
                            else -> {
                                onDatabaseRemoved(currentItem.value.databaseUrl)
                                true
                            }
                        }
                    }
                ),
                background = {
                    if (database.isAddedByUser) {
                        IconButton(onClick = {}) {
                            Icon(
                                imageVector = CampfireIcons.remove,
                                contentDescription = uiStrings.setlistsRemoveSong
                            )
                        }
                    }
                }
            ) {
                CheckboxItem(
                    modifier = Modifier.animateItemPlacement(),
                    text = database.name,
                    isChecked = database.isEnabled,
                    onCheckedChanged = { onDatabaseEnabledChanged(database, it) }
                )
            }
        }
    }
    item(key = "add_database") {
        ClickableControlItem(
            modifier = Modifier.animateItemPlacement(),
            text = uiStrings.settingsAddNewDatabase,
            icon = {
                Icon(
                    imageVector = CampfireIcons.add,
                    contentDescription = uiStrings.settingsAddNewDatabase
                )
            },
            onClick = onAddDatabaseClicked
        )
    }
    item(key = "header_user_interface_theme") {
        HeaderItem(
            modifier = Modifier.fillMaxWidth().animateItemPlacement(),
            text = uiStrings.settingsUserInterfaceTheme
        )
    }
    item(key = "header_user_interface_theme_system_default") {
        RadioButtonItem(
            modifier = Modifier.animateItemPlacement(),
            text = uiStrings.settingsUserInterfaceThemeSystemDefault,
            isChecked = selectedUiMode == UserPreferences.UiMode.SYSTEM_DEFAULT,
            onClick = { onSelectedUiModeChanged(UserPreferences.UiMode.SYSTEM_DEFAULT) }
        )
    }
    item(key = "header_user_interface_theme_dark") {
        RadioButtonItem(
            modifier = Modifier.animateItemPlacement(),
            text = uiStrings.settingsUserInterfaceThemeDark,
            isChecked = selectedUiMode == UserPreferences.UiMode.DARK,
            onClick = { onSelectedUiModeChanged(UserPreferences.UiMode.DARK) }
        )
    }
    item(key = "header_user_interface_theme_light") {
        RadioButtonItem(
            modifier = Modifier.animateItemPlacement(),
            text = uiStrings.settingsUserInterfaceThemeLight,
            isChecked = selectedUiMode == UserPreferences.UiMode.LIGHT,
            onClick = { onSelectedUiModeChanged(UserPreferences.UiMode.LIGHT) }
        )
    }
    item(key = "header_user_interface_language") {
        HeaderItem(
            modifier = Modifier.fillMaxWidth().animateItemPlacement(),
            text = uiStrings.settingsUserInterfaceLanguage
        )
    }
    item(key = "header_user_interface_language_system_default") {
        RadioButtonItem(
            modifier = Modifier.animateItemPlacement(),
            text = uiStrings.settingsUserInterfaceLanguageSystemDefault,
            isChecked = selectedLanguage == UserPreferences.Language.SYSTEM_DEFAULT,
            onClick = { onSelectedLanguageChanged(UserPreferences.Language.SYSTEM_DEFAULT) }
        )
    }
    item(key = "header_user_interface_language_english") {
        RadioButtonItem(
            modifier = Modifier.animateItemPlacement(),
            text = uiStrings.settingsUserInterfaceLanguageEnglish,
            isChecked = selectedLanguage == UserPreferences.Language.ENGLISH,
            onClick = { onSelectedLanguageChanged(UserPreferences.Language.ENGLISH) }
        )
    }
    item(key = "header_user_interface_language_hungarian") {
        RadioButtonItem(
            modifier = Modifier.animateItemPlacement(),
            text = uiStrings.settingsUserInterfaceLanguageHungarian,
            isChecked = selectedLanguage == UserPreferences.Language.HUNGARIAN,
            onClick = { onSelectedLanguageChanged(UserPreferences.Language.HUNGARIAN) }
        )
    }
}

private data class DatabaseItemKey(
    val databaseUrl: String
) {
    val key = "database_$databaseUrl"
}