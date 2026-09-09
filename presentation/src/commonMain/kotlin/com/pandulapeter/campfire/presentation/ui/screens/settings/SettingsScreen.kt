package com.pandulapeter.campfire.presentation.ui.screens.settings

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.calculateEndPadding
import androidx.compose.foundation.layout.calculateStartPadding
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ListItem
import androidx.compose.material3.ListItemDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.pandulapeter.campfire.data.model.domain.UserPreferences
import com.pandulapeter.campfire.presentation.CAMPFIRE_VERSION_NAME
import com.pandulapeter.campfire.presentation.resources.Res
import com.pandulapeter.campfire.presentation.resources.ic_export
import com.pandulapeter.campfire.presentation.resources.ic_git_hub
import com.pandulapeter.campfire.presentation.resources.ic_import
import com.pandulapeter.campfire.presentation.resources.ic_privacy_policy
import com.pandulapeter.campfire.presentation.resources.ic_refresh
import com.pandulapeter.campfire.presentation.resources.ic_website
import com.pandulapeter.campfire.presentation.resources.settings
import com.pandulapeter.campfire.presentation.resources.settings_about
import com.pandulapeter.campfire.presentation.resources.settings_export_all
import com.pandulapeter.campfire.presentation.resources.settings_git_hub
import com.pandulapeter.campfire.presentation.resources.settings_import
import com.pandulapeter.campfire.presentation.resources.settings_library
import com.pandulapeter.campfire.presentation.resources.settings_library_location
import com.pandulapeter.campfire.presentation.resources.settings_library_summary
import com.pandulapeter.campfire.presentation.resources.settings_lyrics_only_mode
import com.pandulapeter.campfire.presentation.resources.settings_lyrics_only_mode_description
import com.pandulapeter.campfire.presentation.resources.settings_horizontal_section_flow
import com.pandulapeter.campfire.presentation.resources.settings_horizontal_section_flow_description
import com.pandulapeter.campfire.presentation.resources.settings_privacy_policy
import com.pandulapeter.campfire.presentation.resources.settings_song_display
import com.pandulapeter.campfire.presentation.resources.settings_user_interface_language
import com.pandulapeter.campfire.presentation.resources.settings_user_interface_language_english
import com.pandulapeter.campfire.presentation.resources.settings_user_interface_language_hungarian
import com.pandulapeter.campfire.presentation.resources.settings_user_interface_language_system_default
import com.pandulapeter.campfire.presentation.resources.settings_user_interface_theme
import com.pandulapeter.campfire.presentation.resources.settings_user_interface_theme_dark
import com.pandulapeter.campfire.presentation.resources.settings_user_interface_theme_light
import com.pandulapeter.campfire.presentation.resources.settings_user_interface_theme_system_default
import com.pandulapeter.campfire.presentation.resources.settings_version
import com.pandulapeter.campfire.presentation.resources.settings_website
import com.pandulapeter.campfire.presentation.resources.songs_rescan
import com.pandulapeter.campfire.presentation.ui.CampfireViewModel
import com.pandulapeter.campfire.presentation.ui.components.ActionListItem
import com.pandulapeter.campfire.presentation.ui.components.CampfireTopAppBar
import com.pandulapeter.campfire.presentation.ui.components.ImportProgress
import com.pandulapeter.campfire.presentation.ui.components.LinkListItem
import com.pandulapeter.campfire.presentation.ui.components.SECTION_HEADER_GAP
import com.pandulapeter.campfire.presentation.ui.components.SectionHeader
import com.pandulapeter.campfire.presentation.ui.components.SegmentedChoice
import com.pandulapeter.campfire.presentation.ui.components.SwitchListItem
import com.pandulapeter.campfire.presentation.ui.platform.LocalFilePicker
import com.pandulapeter.campfire.presentation.ui.platform.libraryLocationHint
import com.pandulapeter.campfire.presentation.localization.stringResource
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch
import org.jetbrains.compose.resources.painterResource

@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun SettingsScreen(
    modifier: Modifier = Modifier,
    viewModel: CampfireViewModel,
    contentPadding: PaddingValues,
    urlOpener: (String) -> Unit
) {
    val scrollBehavior = TopAppBarDefaults.pinnedScrollBehavior()
    val isImporting by viewModel.isImporting.collectAsStateWithLifecycle()
    val filePicker = LocalFilePicker.current
    Column(
        modifier = modifier.fillMaxSize().nestedScroll(scrollBehavior.nestedScrollConnection)
    ) {
        CampfireTopAppBar(
            scrollBehavior = scrollBehavior,
            title = { Text(stringResource(Res.string.settings)) }
        )
        ImportProgress(isImporting = isImporting)
        val userPreferences by viewModel.userPreferences.collectAsStateWithLifecycle()
        // Null until the library has been read, so that the row fades in with real counts instead of showing zeroes.
        val librarySummary by viewModel.librarySummary.collectAsStateWithLifecycle()
        val layoutDirection = LocalLayoutDirection.current
        val listState = rememberLazyListState()
        val coroutineScope = rememberCoroutineScope()
        LazyColumn(
            modifier = Modifier.fillMaxSize(),
            state = listState,
            contentPadding = PaddingValues(
                start = contentPadding.calculateStartPadding(layoutDirection),
                top = SECTION_HEADER_GAP,
                end = contentPadding.calculateEndPadding(layoutDirection),
                bottom = contentPadding.calculateBottomPadding() + 16.dp
            )
        ) {
            sectionHeader(
                key = "header_library",
                listState = listState,
                coroutineScope = coroutineScope
            ) { stringResource(Res.string.settings_library) }
            librarySummary?.let { summary ->
                item(key = "library_summary") {
                    ListItem(
                        modifier = Modifier.animateItem(),
                        colors = ListItemDefaults.colors(containerColor = Color.Transparent),
                        headlineContent = { Text(stringResource(Res.string.settings_library_summary, summary.songCount, summary.setlistCount)) }
                    )
                }
            }
            libraryLocationHint?.let { hint ->
                item(key = "library_location") {
                    ListItem(
                        modifier = Modifier.animateItem(),
                        colors = ListItemDefaults.colors(containerColor = Color.Transparent),
                        headlineContent = { Text(stringResource(Res.string.settings_library_location)) },
                        supportingContent = { Text(hint) }
                    )
                }
            }
            item(key = "library_import") {
                ActionListItem(
                    modifier = Modifier.animateItem(),
                    title = stringResource(Res.string.settings_import),
                    icon = painterResource(Res.drawable.ic_import),
                    isEnabled = !isImporting,
                    isEmphasized = false,
                    onClick = { viewModel.importFiles(filePicker) }
                )
            }
            item(key = "library_export") {
                ActionListItem(
                    modifier = Modifier.animateItem(),
                    title = stringResource(Res.string.settings_export_all),
                    icon = painterResource(Res.drawable.ic_export),
                    isEmphasized = false,
                    onClick = { viewModel.exportLibrary(filePicker) }
                )
            }
            item(key = "library_rescan") {
                ActionListItem(
                    modifier = Modifier.animateItem(),
                    title = stringResource(Res.string.songs_rescan),
                    icon = painterResource(Res.drawable.ic_refresh),
                    isEmphasized = false,
                    onClick = viewModel::refresh
                )
            }
            sectionHeader(
                key = "header_song_display",
                listState = listState,
                coroutineScope = coroutineScope
            ) { stringResource(Res.string.settings_song_display) }
            item(key = "lyrics_only_mode") {
                SwitchListItem(
                    modifier = Modifier.animateItem(),
                    title = stringResource(Res.string.settings_lyrics_only_mode),
                    description = stringResource(Res.string.settings_lyrics_only_mode_description),
                    isChecked = userPreferences?.isLyricsOnlyModeEnabled == true,
                    onCheckedChange = viewModel::setLyricsOnlyModeEnabled
                )
            }
            item(key = "horizontal_section_flow") {
                SwitchListItem(
                    modifier = Modifier.animateItem(),
                    title = stringResource(Res.string.settings_horizontal_section_flow),
                    description = stringResource(Res.string.settings_horizontal_section_flow_description),
                    isChecked = userPreferences?.isHorizontalSectionFlowEnabled == true,
                    onCheckedChange = viewModel::setHorizontalSectionFlowEnabled
                )
            }
            sectionHeader(
                key = "header_theme",
                listState = listState,
                coroutineScope = coroutineScope
            ) { stringResource(Res.string.settings_user_interface_theme) }
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
            sectionHeader(
                key = "header_language",
                listState = listState,
                coroutineScope = coroutineScope
            ) { stringResource(Res.string.settings_user_interface_language) }
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
            sectionHeader(
                key = "header_about",
                listState = listState,
                coroutineScope = coroutineScope
            ) { stringResource(Res.string.settings_about) }
            item(key = "website") {
                LinkListItem(
                    modifier = Modifier.animateItem(),
                    title = stringResource(Res.string.settings_website),
                    icon = painterResource(Res.drawable.ic_website),
                    onClick = { urlOpener("https://pandulapeter.com/") }
                )
            }
            item(key = "github") {
                LinkListItem(
                    modifier = Modifier.animateItem(),
                    title = stringResource(Res.string.settings_git_hub),
                    icon = painterResource(Res.drawable.ic_git_hub),
                    onClick = { urlOpener("https://github.com/pandulapeter") }
                )
            }
            item(key = "privacy_policy") {
                LinkListItem(
                    modifier = Modifier.animateItem(),
                    title = stringResource(Res.string.settings_privacy_policy),
                    icon = painterResource(Res.drawable.ic_privacy_policy),
                    onClick = { urlOpener("https://pandulapeter.com/legal/privacy_policy-campfire.html") }
                )
            }
            item(key = "version") {
                Text(
                    modifier = Modifier.animateItem().fillMaxWidth().padding(horizontal = 16.dp, vertical = 24.dp),
                    text = stringResource(Res.string.settings_version, CAMPFIRE_VERSION_NAME),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    textAlign = TextAlign.Center
                )
            }
        }
    }
}

/**
 * A sticky section header of the settings list. The same pill as the section headers of the song lists, so that the
 * three main screens look and behave the same way: clicking it scrolls back to the start of its own section.
 */
private fun LazyListScope.sectionHeader(
    key: String,
    listState: LazyListState,
    coroutineScope: CoroutineScope,
    text: @Composable () -> String
) = stickyHeader(key = key) { headerIndex ->
    SectionHeader(
        modifier = Modifier.animateItem(),
        text = text(),
        onClick = { coroutineScope.launch { listState.animateScrollToItem(headerIndex) } }
    )
}
