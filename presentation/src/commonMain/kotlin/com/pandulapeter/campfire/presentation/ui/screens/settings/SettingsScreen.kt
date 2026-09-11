/*
 * This file is part of Campfire.
 * Copyright (c) Pandula Péter 2017-2026.
 * https://github.com/pandulapeter/campfire
 *
 * This Source Code Form is subject to the terms of the Mozilla Public License, v. 2.0.
 * If a copy of the MPL was not distributed with this file, You can obtain one at
 * https://mozilla.org/MPL/2.0/.
 */
package com.pandulapeter.campfire.presentation.ui.screens.settings

import androidx.compose.foundation.layout.Arrangement
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
import androidx.compose.runtime.produceState
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Modifier
import androidx.compose.ui.Alignment
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.painter.Painter
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.pandulapeter.campfire.data.model.domain.UserPreferences
import com.pandulapeter.campfire.data.source.remote.api.model.AuthorizationCompletionPage
import com.pandulapeter.campfire.presentation.CAMPFIRE_VERSION_NAME
import com.pandulapeter.campfire.presentation.resources.Res
import com.pandulapeter.campfire.presentation.resources.ic_campfire
import com.pandulapeter.campfire.presentation.resources.ic_coffee
import com.pandulapeter.campfire.presentation.resources.ic_export
import com.pandulapeter.campfire.presentation.resources.ic_git_hub
import com.pandulapeter.campfire.presentation.resources.ic_import
import com.pandulapeter.campfire.presentation.resources.ic_phone
import com.pandulapeter.campfire.presentation.resources.ic_privacy_policy
import com.pandulapeter.campfire.presentation.resources.ic_refresh
import com.pandulapeter.campfire.presentation.resources.ic_website
import com.pandulapeter.campfire.presentation.resources.settings
import com.pandulapeter.campfire.presentation.resources.settings_about
import com.pandulapeter.campfire.presentation.resources.settings_accidentals
import com.pandulapeter.campfire.presentation.resources.settings_accidentals_description
import com.pandulapeter.campfire.presentation.resources.settings_accidentals_flats
import com.pandulapeter.campfire.presentation.resources.settings_accidentals_original
import com.pandulapeter.campfire.presentation.resources.settings_accidentals_sharps
import com.pandulapeter.campfire.presentation.resources.settings_german_notation
import com.pandulapeter.campfire.presentation.resources.settings_german_notation_description
import com.pandulapeter.campfire.presentation.resources.settings_created_by
import com.pandulapeter.campfire.presentation.resources.settings_export_all
import com.pandulapeter.campfire.presentation.resources.settings_git_hub
import com.pandulapeter.campfire.presentation.resources.settings_import
import com.pandulapeter.campfire.presentation.resources.settings_library
import com.pandulapeter.campfire.presentation.resources.settings_library_location
import com.pandulapeter.campfire.presentation.resources.settings_library_location_files_app
import com.pandulapeter.campfire.presentation.resources.settings_library_storage
import com.pandulapeter.campfire.presentation.resources.settings_library_storage_best_effort
import com.pandulapeter.campfire.presentation.resources.settings_library_storage_granted
import com.pandulapeter.campfire.presentation.resources.settings_library_summary
import com.pandulapeter.campfire.presentation.resources.settings_lyrics_only_mode
import com.pandulapeter.campfire.presentation.resources.settings_lyrics_only_mode_description
import com.pandulapeter.campfire.presentation.resources.settings_horizontal_section_flow
import com.pandulapeter.campfire.presentation.resources.settings_horizontal_section_flow_description
import com.pandulapeter.campfire.presentation.resources.settings_privacy_policy
import com.pandulapeter.campfire.presentation.resources.settings_song_display
import com.pandulapeter.campfire.presentation.resources.settings_support
import com.pandulapeter.campfire.presentation.resources.settings_sync
import com.pandulapeter.campfire.presentation.resources.settings_sync_redirect_page_message
import com.pandulapeter.campfire.presentation.resources.settings_sync_redirect_page_title
import com.pandulapeter.campfire.presentation.resources.settings_user_interface
import com.pandulapeter.campfire.presentation.resources.settings_user_interface_language
import com.pandulapeter.campfire.presentation.resources.settings_user_interface_language_english
import com.pandulapeter.campfire.presentation.resources.settings_user_interface_language_hungarian
import com.pandulapeter.campfire.presentation.resources.settings_user_interface_language_system_default
import com.pandulapeter.campfire.presentation.resources.settings_user_interface_theme
import com.pandulapeter.campfire.presentation.resources.settings_user_interface_theme_color
import com.pandulapeter.campfire.presentation.resources.settings_user_interface_theme_color_blue
import com.pandulapeter.campfire.presentation.resources.settings_user_interface_theme_color_campfire
import com.pandulapeter.campfire.presentation.resources.settings_user_interface_theme_color_green
import com.pandulapeter.campfire.presentation.resources.settings_user_interface_theme_color_pink
import com.pandulapeter.campfire.presentation.resources.settings_user_interface_theme_color_purple
import com.pandulapeter.campfire.presentation.resources.settings_user_interface_theme_color_red
import com.pandulapeter.campfire.presentation.resources.settings_user_interface_theme_color_system
import com.pandulapeter.campfire.presentation.resources.settings_user_interface_theme_color_teal
import com.pandulapeter.campfire.presentation.resources.settings_user_interface_theme_color_yellow
import com.pandulapeter.campfire.presentation.resources.settings_user_interface_theme_dark
import com.pandulapeter.campfire.presentation.resources.settings_user_interface_theme_light
import com.pandulapeter.campfire.presentation.resources.settings_user_interface_theme_system_default
import com.pandulapeter.campfire.presentation.resources.settings_version
import com.pandulapeter.campfire.presentation.resources.settings_website
import com.pandulapeter.campfire.presentation.resources.songs_rescan
import com.pandulapeter.campfire.presentation.ui.CampfireViewModel
import com.pandulapeter.campfire.presentation.ui.components.ActionListItem
import com.pandulapeter.campfire.presentation.ui.components.CampfireTopAppBar
import com.pandulapeter.campfire.presentation.ui.components.ColorChoice
import com.pandulapeter.campfire.presentation.ui.components.ColorChoiceOption
import com.pandulapeter.campfire.presentation.ui.components.ImportProgress
import com.pandulapeter.campfire.presentation.ui.components.LinkListItem
import com.pandulapeter.campfire.presentation.ui.components.SECTION_HEADER_GAP
import com.pandulapeter.campfire.presentation.ui.components.SectionHeader
import com.pandulapeter.campfire.presentation.ui.components.SegmentedChoice
import com.pandulapeter.campfire.presentation.ui.components.SettingsSectionTitle
import com.pandulapeter.campfire.presentation.ui.components.SwitchListItem
import com.pandulapeter.campfire.presentation.ui.components.animateScrollToKey
import com.pandulapeter.campfire.presentation.ui.platform.LocalFilePicker
import com.pandulapeter.campfire.presentation.ui.platform.canAskForDonations
import com.pandulapeter.campfire.presentation.ui.platform.LibraryLocation
import com.pandulapeter.campfire.presentation.ui.platform.LibraryPersistence
import com.pandulapeter.campfire.presentation.ui.platform.libraryLocation
import com.pandulapeter.campfire.presentation.ui.platform.requestLibraryPersistence
import com.pandulapeter.campfire.presentation.ui.theme.isDarkTheme
import com.pandulapeter.campfire.presentation.ui.theme.themeColorOptions
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
    urlOpener: (String) -> Unit,
) {
    val scrollBehavior = TopAppBarDefaults.pinnedScrollBehavior()
    val isImporting by viewModel.isImporting.collectAsStateWithLifecycle()
    val filePicker = LocalFilePicker.current
    Column(
        modifier = modifier.fillMaxSize().nestedScroll(scrollBehavior.nestedScrollConnection)
    ) {
        CampfireTopAppBar(
            scrollBehavior = scrollBehavior,
            title = { Text(stringResource(Res.string.settings)) },
        )
        ImportProgress(isImporting = isImporting)
        val userPreferences by viewModel.userPreferences.collectAsStateWithLifecycle()
        val syncState by viewModel.syncState.collectAsStateWithLifecycle()
        // Null until the library has been read, so that the row fades in with real counts instead of showing zeroes.
        val librarySummary by viewModel.librarySummary.collectAsStateWithLifecycle()
        // The app asked for this as it started; asking again only reads back the answer, see requestLibraryPersistence.
        val libraryPersistence by produceState<LibraryPersistence?>(null) { value = requestLibraryPersistence() }
        val layoutDirection = LocalLayoutDirection.current
        val listState = rememberLazyListState()
        val coroutineScope = rememberCoroutineScope()
        // Resolved out here rather than in the data layer, which can see neither the translations nor the language
        // the user picked, and out of the list because a lazy list's scope is not a composable one.
        val completionPage = AuthorizationCompletionPage(
            title = stringResource(Res.string.settings_sync_redirect_page_title),
            message = stringResource(Res.string.settings_sync_redirect_page_message),
        )
        LazyColumn(
            modifier = Modifier.fillMaxSize(),
            state = listState,
            contentPadding = PaddingValues(
                start = contentPadding.calculateStartPadding(layoutDirection),
                top = SECTION_HEADER_GAP,
                end = contentPadding.calculateEndPadding(layoutDirection),
                bottom = contentPadding.calculateBottomPadding() + 16.dp,
            ),
        ) {
            sectionHeader(
                key = "header_library",
                listState = listState,
                coroutineScope = coroutineScope,
            ) { stringResource(Res.string.settings_library) }
            librarySummary?.let { summary ->
                item(key = "library_summary") {
                    ListItem(
                        modifier = Modifier.animateItem(),
                        colors = ListItemDefaults.colors(containerColor = Color.Transparent),
                        headlineContent = { Text(stringResource(Res.string.settings_library_summary, summary.songCount, summary.setlistCount)) },
                    )
                }
            }
            libraryLocation?.let { location ->
                item(key = "library_location") {
                    ListItem(
                        modifier = Modifier.animateItem(),
                        colors = ListItemDefaults.colors(containerColor = Color.Transparent),
                        headlineContent = { Text(stringResource(Res.string.settings_library_location)) },
                        supportingContent = {
                            Text(
                                when (location) {
                                    is LibraryLocation.Folder -> location.path
                                    LibraryLocation.FilesApp -> stringResource(Res.string.settings_library_location_files_app)
                                }
                            )
                        },
                    )
                }
            }
            // Only where the answer is not a foregone conclusion, which is the web: the other three platforms keep
            // the library in a file system of their own and have the location row above instead.
            if (libraryPersistence != null && libraryPersistence != LibraryPersistence.GUARANTEED) {
                item(key = "library_storage") {
                    ListItem(
                        modifier = Modifier.animateItem(),
                        colors = ListItemDefaults.colors(containerColor = Color.Transparent),
                        headlineContent = { Text(stringResource(Res.string.settings_library_storage)) },
                        supportingContent = {
                            Text(
                                text = when (libraryPersistence) {
                                    LibraryPersistence.GRANTED -> stringResource(Res.string.settings_library_storage_granted)
                                    else -> stringResource(Res.string.settings_library_storage_best_effort)
                                },
                                color = if (libraryPersistence == LibraryPersistence.GRANTED) Color.Unspecified else MaterialTheme.colorScheme.error,
                            )
                        },
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
                    onClick = { viewModel.importFiles(filePicker) },
                )
            }
            item(key = "library_export") {
                ActionListItem(
                    modifier = Modifier.animateItem(),
                    title = stringResource(Res.string.settings_export_all),
                    icon = painterResource(Res.drawable.ic_export),
                    isEmphasized = false,
                    onClick = { viewModel.exportLibrary(filePicker) },
                )
            }
            item(key = "library_rescan") {
                ActionListItem(
                    modifier = Modifier.animateItem(),
                    title = stringResource(Res.string.songs_rescan),
                    icon = painterResource(Res.drawable.ic_refresh),
                    isEmphasized = false,
                    onClick = viewModel::refresh,
                )
            }
            sectionHeader(
                key = "header_sync",
                listState = listState,
                coroutineScope = coroutineScope,
            ) { stringResource(Res.string.settings_sync) }
            syncSettings(
                viewModel = viewModel,
                syncState = syncState,
                completionPage = completionPage,
            )
            sectionHeader(
                key = "header_song_display",
                listState = listState,
                coroutineScope = coroutineScope,
            ) { stringResource(Res.string.settings_song_display) }
            item(key = "lyrics_only_mode") {
                SwitchListItem(
                    modifier = Modifier.animateItem(),
                    title = stringResource(Res.string.settings_lyrics_only_mode),
                    description = stringResource(Res.string.settings_lyrics_only_mode_description),
                    isChecked = userPreferences?.isLyricsOnlyModeEnabled == true,
                    onCheckedChange = viewModel::setLyricsOnlyModeEnabled,
                )
            }
            item(key = "horizontal_section_flow") {
                SwitchListItem(
                    modifier = Modifier.animateItem(),
                    title = stringResource(Res.string.settings_horizontal_section_flow),
                    description = stringResource(Res.string.settings_horizontal_section_flow_description),
                    isChecked = userPreferences?.isHorizontalSectionFlowEnabled == true,
                    onCheckedChange = viewModel::setHorizontalSectionFlowEnabled,
                )
            }
            // Both of these only decide how a chord is written, so lyrics only mode leaves them with nothing to
            // say. They stay in the list rather than disappearing from it: what they are set to is still what the
            // chords will look like as soon as they are shown again.
            val isChordSpellingEnabled = userPreferences?.isLyricsOnlyModeEnabled != true
            item(key = "german_notation") {
                SwitchListItem(
                    modifier = Modifier.animateItem(),
                    title = stringResource(Res.string.settings_german_notation),
                    description = stringResource(Res.string.settings_german_notation_description),
                    isChecked = userPreferences?.chordSpelling?.isGermanNotationEnabled == true,
                    isEnabled = isChordSpellingEnabled,
                    onCheckedChange = viewModel::setGermanNotationEnabled,
                )
            }
            item(key = "accidentals") {
                Subsection(
                    modifier = Modifier.animateItem().padding(vertical = SUBSECTION_GAP),
                    title = stringResource(Res.string.settings_accidentals),
                    description = stringResource(Res.string.settings_accidentals_description),
                    isEnabled = isChordSpellingEnabled,
                ) {
                    SegmentedChoice(
                        options = listOf(
                            UserPreferences.Accidentals.ORIGINAL to stringResource(Res.string.settings_accidentals_original),
                            UserPreferences.Accidentals.FLATS to stringResource(Res.string.settings_accidentals_flats),
                            UserPreferences.Accidentals.SHARPS to stringResource(Res.string.settings_accidentals_sharps),
                        ),
                        selected = userPreferences?.chordSpelling?.accidentals,
                        isEnabled = isChordSpellingEnabled,
                        onSelected = viewModel::setAccidentals,
                    )
                }
            }
            sectionHeader(
                key = "header_user_interface",
                listState = listState,
                coroutineScope = coroutineScope,
            ) { stringResource(Res.string.settings_user_interface) }
            // One item, so that the gaps between the subsections are set once, next to each other, rather than by
            // each subsection padding itself and every pair of them then adding up to twice the gap.
            item(key = "user_interface") {
                Column(
                    modifier = Modifier.animateItem().padding(vertical = SUBSECTION_GAP),
                    verticalArrangement = Arrangement.spacedBy(SUBSECTION_GAP),
                ) {
                    Subsection(title = stringResource(Res.string.settings_user_interface_theme)) {
                        SegmentedChoice(
                            options = listOf(
                                UserPreferences.UiMode.SYSTEM_DEFAULT to stringResource(Res.string.settings_user_interface_theme_system_default),
                                UserPreferences.UiMode.LIGHT to stringResource(Res.string.settings_user_interface_theme_light),
                                UserPreferences.UiMode.DARK to stringResource(Res.string.settings_user_interface_theme_dark),
                            ),
                            selected = userPreferences?.uiMode,
                            onSelected = viewModel::setUiMode,
                        )
                    }
                    Subsection(title = stringResource(Res.string.settings_user_interface_theme_color)) {
                        // Each disc is painted in the half of its palette that is on screen, so that they all change
                        // with the light and dark switch above them instead of advertising colors nothing would
                        // actually be drawn in.
                        val isDarkTheme = userPreferences?.uiMode.isDarkTheme()
                        ColorChoice(
                            options = themeColorOptions().map { (themeColor, colorSchemePair) ->
                                val colorScheme = if (isDarkTheme) colorSchemePair.dark else colorSchemePair.light
                                ColorChoiceOption(
                                    value = themeColor,
                                    color = colorScheme.primary,
                                    contentColor = colorScheme.onPrimary,
                                    label = themeColor.label(),
                                    icon = themeColor.icon(),
                                )
                            },
                            selected = userPreferences?.themeColor,
                            onSelected = viewModel::setThemeColor,
                        )
                    }
                    Subsection(title = stringResource(Res.string.settings_user_interface_language)) {
                        SegmentedChoice(
                            options = listOf(
                                UserPreferences.Language.SYSTEM_DEFAULT to stringResource(Res.string.settings_user_interface_language_system_default),
                                UserPreferences.Language.ENGLISH to stringResource(Res.string.settings_user_interface_language_english),
                                UserPreferences.Language.HUNGARIAN to stringResource(Res.string.settings_user_interface_language_hungarian),
                            ),
                            selected = userPreferences?.language,
                            onSelected = viewModel::setLanguage,
                        )
                    }
                }
            }
            sectionHeader(
                key = "header_about",
                listState = listState,
                coroutineScope = coroutineScope,
            ) { stringResource(Res.string.settings_about) }
            item(key = "website") {
                LinkListItem(
                    modifier = Modifier.animateItem(),
                    title = stringResource(Res.string.settings_website),
                    icon = painterResource(Res.drawable.ic_website),
                    onClick = { urlOpener("https:,//pandulapeter.com/") }
                )
            }
            item(key = "github") {
                LinkListItem(
                    modifier = Modifier.animateItem(),
                    title = stringResource(Res.string.settings_git_hub),
                    icon = painterResource(Res.drawable.ic_git_hub),
                    onClick = { urlOpener("https:,//github.com/pandulapeter") }
                )
            }
            item(key = "privacy_policy") {
                LinkListItem(
                    modifier = Modifier.animateItem(),
                    title = stringResource(Res.string.settings_privacy_policy),
                    icon = painterResource(Res.drawable.ic_privacy_policy),
                    onClick = { urlOpener("https:,//pandulapeter.com/legal/privacy_policy-campfire.html") }
                )
            }
            if (canAskForDonations) {
                item(key = "donate") {
                    LinkListItem(
                        modifier = Modifier.animateItem(),
                        title = stringResource(Res.string.settings_support),
                        icon = painterResource(Res.drawable.ic_coffee),
                        onClick = { urlOpener("https:,//buymeacoffee.com/pandulapeter") }
                    )
                }
            }
            item(key = "footer") {
                Column(
                    modifier = Modifier.animateItem().fillMaxWidth().padding(horizontal = 16.dp, vertical = 24.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                ) {
                    Text(
                        text = stringResource(Res.string.settings_created_by),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        textAlign = TextAlign.Center,
                    )
                    Text(
                        modifier = Modifier.padding(top = 4.dp),
                        text = stringResource(Res.string.settings_version, CAMPFIRE_VERSION_NAME),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        textAlign = TextAlign.Center,
                    )
                }
            }
        }
    }
}

/**
 * What a color offered by the theme is called. It is only ever read out by an accessibility service, since the
 * settings screen shows each color as itself.
 */
@Composable
private fun UserPreferences.ThemeColor.label() = stringResource(
    when (this) {
        UserPreferences.ThemeColor.CAMPFIRE -> Res.string.settings_user_interface_theme_color_campfire
        UserPreferences.ThemeColor.SYSTEM -> Res.string.settings_user_interface_theme_color_system
        UserPreferences.ThemeColor.RED -> Res.string.settings_user_interface_theme_color_red
        UserPreferences.ThemeColor.YELLOW -> Res.string.settings_user_interface_theme_color_yellow
        UserPreferences.ThemeColor.GREEN -> Res.string.settings_user_interface_theme_color_green
        UserPreferences.ThemeColor.TEAL -> Res.string.settings_user_interface_theme_color_teal
        UserPreferences.ThemeColor.BLUE -> Res.string.settings_user_interface_theme_color_blue
        UserPreferences.ThemeColor.PURPLE -> Res.string.settings_user_interface_theme_color_purple
        UserPreferences.ThemeColor.PINK -> Res.string.settings_user_interface_theme_color_pink
    }
)

/**
 * The icon a color offered by the theme carries while it is not selected, for the two whose color is not what picks
 * them out: the app's own palette, and the one the operating system hands over. The rest are only a color, and a
 * glyph on each of them would say nothing the disc does not.
 */
@Composable
private fun UserPreferences.ThemeColor.icon(): Painter? = when (this) {
    UserPreferences.ThemeColor.CAMPFIRE -> painterResource(Res.drawable.ic_campfire)
    UserPreferences.ThemeColor.SYSTEM -> painterResource(Res.drawable.ic_phone)
    else -> null
}

/**
 * A section header of the settings list. The same pill as the section headers of the song lists, so that the three
 * main screens look and behave the same way: clicking it scrolls back to the start of its own section.
 */
private fun LazyListScope.sectionHeader(
    key: String,
    listState: LazyListState,
    coroutineScope: CoroutineScope,
    text: @Composable () -> String,
) = item(key = key) {
    SectionHeader(
        modifier = Modifier.animateItem(),
        text = text(),
        onClick = { coroutineScope.launch { listState.animateScrollToKey(key) } },
    )
}

/**
 * A named group of controls inside a section: the title and the control it names. The title is close enough to its
 * control ([SUBSECTION_TITLE_GAP]) to read as its label rather than as another group; the gaps to everything around
 * it are [SUBSECTION_GAP], set by the caller.
 *
 * @param description What a [SwitchListItem]'s supporting text says for a switch: there for a group whose title is
 *   not the whole story, left out where the options speak for themselves.
 * @param isEnabled Dims the title and the description the way a disabled row is dimmed; the control inside is left
 *   to disable itself, so that it is dimmed once rather than twice.
 */
@Composable
private fun Subsection(
    modifier: Modifier = Modifier,
    title: String,
    description: String? = null,
    isEnabled: Boolean = true,
    content: @Composable () -> Unit,
) = Column(modifier = modifier) {
    val labelAlpha = if (isEnabled) 1f else 0.5f
    SettingsSectionTitle(
        modifier = Modifier.alpha(labelAlpha),
        text = title,
        contentPadding = PaddingValues(start = 16.dp, end = 16.dp, bottom = SUBSECTION_TITLE_GAP),
    )
    description?.let {
        Text(
            modifier = Modifier.alpha(labelAlpha).padding(start = 16.dp, end = 16.dp, bottom = SUBSECTION_DESCRIPTION_GAP),
            text = it,
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
    content()
}

/**
 * The gap between two [Subsection]s, and between a [Subsection] and the section header pill above or below it, which
 * adds up with the pill's own [SECTION_HEADER_GAP] to about the distance a pill keeps from a [ListItem] row.
 */
private val SUBSECTION_GAP = 12.dp

/** The gap between a [Subsection]'s title and its control, small enough that the two read as one thing. */
private val SUBSECTION_TITLE_GAP = 4.dp

/** The gap below a [Subsection]'s description, which stands between the title and the control rather than beside them. */
private val SUBSECTION_DESCRIPTION_GAP = 8.dp
