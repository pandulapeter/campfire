package com.pandulapeter.campfire.shared.ui.screens.settings

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.calculateEndPadding
import androidx.compose.foundation.layout.calculateStartPadding
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.SwipeToDismissBox
import androidx.compose.material3.SwipeToDismissBoxValue
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.material3.rememberSwipeToDismissBoxState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.pandulapeter.campfire.data.model.domain.UserPreferences
import com.pandulapeter.campfire.shared.resources.Res
import com.pandulapeter.campfire.shared.resources.settings
import com.pandulapeter.campfire.shared.resources.settings_about
import com.pandulapeter.campfire.shared.resources.settings_active_databases
import com.pandulapeter.campfire.shared.resources.settings_add_new_database
import com.pandulapeter.campfire.shared.resources.settings_git_hub
import com.pandulapeter.campfire.shared.resources.settings_lyrics_only_mode
import com.pandulapeter.campfire.shared.resources.settings_lyrics_only_mode_description
import com.pandulapeter.campfire.shared.resources.settings_privacy_policy
import com.pandulapeter.campfire.shared.resources.settings_remove_database
import com.pandulapeter.campfire.shared.resources.settings_song_display
import com.pandulapeter.campfire.shared.resources.settings_user_interface_language
import com.pandulapeter.campfire.shared.resources.settings_user_interface_language_english
import com.pandulapeter.campfire.shared.resources.settings_user_interface_language_hungarian
import com.pandulapeter.campfire.shared.resources.settings_user_interface_language_system_default
import com.pandulapeter.campfire.shared.resources.settings_user_interface_theme
import com.pandulapeter.campfire.shared.resources.settings_user_interface_theme_dark
import com.pandulapeter.campfire.shared.resources.settings_user_interface_theme_light
import com.pandulapeter.campfire.shared.resources.settings_user_interface_theme_system_default
import com.pandulapeter.campfire.shared.resources.settings_website
import com.pandulapeter.campfire.shared.ui.CampfireViewModel
import com.pandulapeter.campfire.shared.ui.components.ActionListItem
import com.pandulapeter.campfire.shared.ui.components.CampfireTopAppBar
import com.pandulapeter.campfire.shared.ui.components.CheckboxListItem
import com.pandulapeter.campfire.shared.ui.components.LinkListItem
import com.pandulapeter.campfire.shared.ui.components.SegmentedChoice
import com.pandulapeter.campfire.shared.ui.components.SettingsSectionTitle
import com.pandulapeter.campfire.shared.ui.components.SwitchListItem
import com.pandulapeter.campfire.shared.ui.platform.PlatformVerticalScrollbar
import com.pandulapeter.campfire.shared.ui.theme.CampfireIcons
import com.pandulapeter.campfire.shared.localization.stringResource

@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun SettingsScreen(
    modifier: Modifier = Modifier,
    viewModel: CampfireViewModel,
    contentPadding: PaddingValues,
    urlOpener: (String) -> Unit
) {
    val scrollBehavior = TopAppBarDefaults.pinnedScrollBehavior()
    Column(
        modifier = modifier.fillMaxSize().nestedScroll(scrollBehavior.nestedScrollConnection)
    ) {
        CampfireTopAppBar(
            scrollBehavior = scrollBehavior,
            title = { Text(stringResource(Res.string.settings)) }
        )
        val userPreferences by viewModel.userPreferences.collectAsStateWithLifecycle()
        val databases by viewModel.databases.collectAsStateWithLifecycle()
        val listState = rememberLazyListState()
        val layoutDirection = LocalLayoutDirection.current
        Box(
            modifier = Modifier.fillMaxSize()
        ) {
            LazyColumn(
                modifier = Modifier.fillMaxSize(),
                state = listState,
                contentPadding = PaddingValues(
                    start = contentPadding.calculateStartPadding(layoutDirection),
                    end = contentPadding.calculateEndPadding(layoutDirection),
                    bottom = contentPadding.calculateBottomPadding() + 16.dp
                )
            ) {
                item(key = "header_databases") {
                    SettingsSectionTitle(
                        modifier = Modifier.animateItem(),
                        text = stringResource(Res.string.settings_active_databases)
                    )
                }
                items(
                    items = databases,
                    key = { "database_${it.url}" }
                ) { database ->
                    DatabaseItem(
                        modifier = Modifier.animateItem(),
                        name = database.name,
                        isEnabled = database.isEnabled,
                        isRemovable = database.isAddedByUser,
                        onEnabledChanged = { viewModel.setDatabaseEnabled(database, it) },
                        onRemoved = { viewModel.removeDatabase(database) }
                    )
                }
                item(key = "add_database") {
                    ActionListItem(
                        modifier = Modifier.animateItem(),
                        title = stringResource(Res.string.settings_add_new_database),
                        icon = CampfireIcons.add,
                        onClick = { viewModel.showDialog(CampfireViewModel.DialogType.NewDatabase) }
                    )
                }
                item(key = "header_song_display") {
                    SettingsSectionTitle(
                        modifier = Modifier.animateItem(),
                        text = stringResource(Res.string.settings_song_display)
                    )
                }
                item(key = "lyrics_only_mode") {
                    SwitchListItem(
                        modifier = Modifier.animateItem(),
                        title = stringResource(Res.string.settings_lyrics_only_mode),
                        description = stringResource(Res.string.settings_lyrics_only_mode_description),
                        isChecked = userPreferences?.isLyricsOnlyModeEnabled == true,
                        onCheckedChange = viewModel::setLyricsOnlyModeEnabled
                    )
                }
                item(key = "header_theme") {
                    SettingsSectionTitle(
                        modifier = Modifier.animateItem(),
                        text = stringResource(Res.string.settings_user_interface_theme)
                    )
                }
                item(key = "theme") {
                    SegmentedChoice(
                        modifier = Modifier.animateItem(),
                        options = listOf(
                            UserPreferences.UiMode.SYSTEM_DEFAULT to stringResource(Res.string.settings_user_interface_theme_system_default),
                            UserPreferences.UiMode.LIGHT to stringResource(Res.string.settings_user_interface_theme_light),
                            UserPreferences.UiMode.DARK to stringResource(Res.string.settings_user_interface_theme_dark)
                        ),
                        selected = userPreferences?.uiMode,
                        onSelected = viewModel::setUiMode
                    )
                }
                item(key = "header_language") {
                    SettingsSectionTitle(
                        modifier = Modifier.animateItem(),
                        text = stringResource(Res.string.settings_user_interface_language)
                    )
                }
                item(key = "language") {
                    SegmentedChoice(
                        modifier = Modifier.animateItem(),
                        options = listOf(
                            UserPreferences.Language.SYSTEM_DEFAULT to stringResource(Res.string.settings_user_interface_language_system_default),
                            UserPreferences.Language.ENGLISH to stringResource(Res.string.settings_user_interface_language_english),
                            UserPreferences.Language.HUNGARIAN to stringResource(Res.string.settings_user_interface_language_hungarian)
                        ),
                        selected = userPreferences?.language,
                        onSelected = viewModel::setLanguage
                    )
                }
                item(key = "header_about") {
                    SettingsSectionTitle(
                        modifier = Modifier.animateItem(),
                        text = stringResource(Res.string.settings_about)
                    )
                }
                item(key = "website") {
                    LinkListItem(
                        modifier = Modifier.animateItem(),
                        title = stringResource(Res.string.settings_website),
                        icon = CampfireIcons.website,
                        onClick = { urlOpener("https://www.pandulapeter.com/") }
                    )
                }
                item(key = "github") {
                    LinkListItem(
                        modifier = Modifier.animateItem(),
                        title = stringResource(Res.string.settings_git_hub),
                        icon = CampfireIcons.gitHub,
                        onClick = { urlOpener("https://github.com/pandulapeter") }
                    )
                }
                item(key = "privacy_policy") {
                    LinkListItem(
                        modifier = Modifier.animateItem(),
                        title = stringResource(Res.string.settings_privacy_policy),
                        icon = CampfireIcons.privacyPolicy,
                        onClick = { urlOpener("https://pandulapeter.github.io/legal/privacy_policy-campfire.html") }
                    )
                }
            }
            PlatformVerticalScrollbar(
                listState = listState,
                modifier = Modifier.padding(contentPadding)
            )
        }
    }
}

@Composable
private fun DatabaseItem(
    modifier: Modifier = Modifier,
    name: String,
    isEnabled: Boolean,
    isRemovable: Boolean,
    onEnabledChanged: (Boolean) -> Unit,
    onRemoved: () -> Unit
) {
    val dismissState = rememberSwipeToDismissBoxState()
    LaunchedEffect(Unit) { dismissState.reset() }
    SwipeToDismissBox(
        modifier = modifier,
        state = dismissState,
        enableDismissFromStartToEnd = isRemovable,
        enableDismissFromEndToStart = false,
        gesturesEnabled = isRemovable,
        onDismiss = { if (it == SwipeToDismissBoxValue.StartToEnd) onRemoved() },
        backgroundContent = {
            AnimatedVisibility(visible = isRemovable) {
                Box(
                    modifier = Modifier.fillMaxSize().background(MaterialTheme.colorScheme.errorContainer).padding(horizontal = 24.dp),
                    contentAlignment = Alignment.CenterStart
                ) {
                    Icon(
                        imageVector = CampfireIcons.delete,
                        contentDescription = stringResource(Res.string.settings_remove_database),
                        tint = MaterialTheme.colorScheme.onErrorContainer
                    )
                }
            }
        }
    ) {
        CheckboxListItem(
            modifier = Modifier.fillMaxWidth().background(MaterialTheme.colorScheme.surface),
            title = name,
            isChecked = isEnabled,
            onCheckedChange = onEnabledChanged
        )
    }
}
