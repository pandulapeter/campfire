package com.pandulapeter.campfire.shared.ui.screenComponents.settings

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.pandulapeter.campfire.data.model.domain.Database
import com.pandulapeter.campfire.data.model.domain.UserPreferences
import com.pandulapeter.campfire.shared.ui.catalogue.components.CheckboxItem
import com.pandulapeter.campfire.shared.ui.catalogue.components.ClickableControlItem
import com.pandulapeter.campfire.shared.ui.catalogue.components.HeaderItem
import com.pandulapeter.campfire.shared.ui.catalogue.components.RadioButtonItem
import com.pandulapeter.campfire.shared.ui.catalogue.resources.CampfireStrings


@OptIn(ExperimentalFoundationApi::class)
@Composable
fun SettingsContentList(
    modifier: Modifier = Modifier,
    uiStrings: CampfireStrings,
    databases: List<Database>,
    onDatabaseEnabledChanged: (Database, Boolean) -> Unit,
    selectedUiMode: UserPreferences.UiMode?,
    onSelectedUiModeChanged: (UserPreferences.UiMode) -> Unit
) = LazyColumn(
    modifier = modifier.widthIn(max = 300.dp),
    horizontalAlignment = Alignment.CenterHorizontally
) {
    item(key = "header_databases") {
        HeaderItem(
            modifier = Modifier.animateItemPlacement(),
            text = uiStrings.settingsAllDatabases
        )
    }
    itemsIndexed(
        items = databases,
        key = { _, database -> "database_${database.url}" }
    ) { _, database ->
        CheckboxItem(
            modifier = Modifier.animateItemPlacement(),
            text = database.name,
            isChecked = database.isEnabled,
            onCheckedChanged = { onDatabaseEnabledChanged(database, it) }
        )
    }
    item(key = "add_database") {
        ClickableControlItem(
            modifier = Modifier.animateItemPlacement(),
            text = uiStrings.settingsAddNewDatabase,
            onClick = { } // TODO
        )
    }
    item(key = "header_user_interface_theme") {
        HeaderItem(
            modifier = Modifier.animateItemPlacement(),
            text = uiStrings.settingsUserInterfaceTheme
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
    item(key = "header_user_interface_theme_system_default") {
        RadioButtonItem(
            modifier = Modifier.animateItemPlacement(),
            text = uiStrings.settingsUserInterfaceThemeSystemDefault,
            isChecked = selectedUiMode == UserPreferences.UiMode.SYSTEM_DEFAULT,
            onClick = { onSelectedUiModeChanged(UserPreferences.UiMode.SYSTEM_DEFAULT) }
        )
    }
}