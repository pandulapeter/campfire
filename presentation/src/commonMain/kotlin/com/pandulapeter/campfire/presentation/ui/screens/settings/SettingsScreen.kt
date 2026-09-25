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

import androidx.compose.animation.Crossfade
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.ScrollState
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.calculateEndPadding
import androidx.compose.foundation.layout.calculateStartPadding
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.ListItem
import androidx.compose.material3.ListItemDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.unit.Dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.pandulapeter.campfire.data.model.domain.SyncOutcome
import com.pandulapeter.campfire.data.model.domain.SyncState
import com.pandulapeter.campfire.data.model.domain.UserPreferences
import com.pandulapeter.campfire.presentation.CAMPFIRE_VERSION_NAME
import com.pandulapeter.campfire.presentation.localization.AppLocale
import com.pandulapeter.campfire.presentation.localization.LocalizedStrings
import com.pandulapeter.campfire.presentation.localization.stringResource
import com.pandulapeter.campfire.presentation.resources.Res
import com.pandulapeter.campfire.presentation.resources.add_demo_songs
import com.pandulapeter.campfire.presentation.resources.ic_bug
import com.pandulapeter.campfire.presentation.resources.ic_campfire
import com.pandulapeter.campfire.presentation.resources.ic_coffee
import com.pandulapeter.campfire.presentation.resources.ic_export
import com.pandulapeter.campfire.presentation.resources.ic_git_hub
import com.pandulapeter.campfire.presentation.resources.ic_import
import com.pandulapeter.campfire.presentation.resources.ic_phone
import com.pandulapeter.campfire.presentation.resources.ic_privacy_policy
import com.pandulapeter.campfire.presentation.resources.ic_songs
import com.pandulapeter.campfire.presentation.resources.ic_star
import com.pandulapeter.campfire.presentation.resources.settings_about
import com.pandulapeter.campfire.presentation.resources.settings_accidentals
import com.pandulapeter.campfire.presentation.resources.settings_accidentals_description
import com.pandulapeter.campfire.presentation.resources.settings_accidentals_flats
import com.pandulapeter.campfire.presentation.resources.settings_accidentals_original
import com.pandulapeter.campfire.presentation.resources.settings_accidentals_sharps
import com.pandulapeter.campfire.presentation.resources.settings_app_icon_browser_tab
import com.pandulapeter.campfire.presentation.resources.settings_app_icon_browser_tab_description
import com.pandulapeter.campfire.presentation.resources.settings_app_icon_dock
import com.pandulapeter.campfire.presentation.resources.settings_app_icon_dock_description
import com.pandulapeter.campfire.presentation.resources.settings_app_icon_home_screen
import com.pandulapeter.campfire.presentation.resources.settings_app_icon_home_screen_description
import com.pandulapeter.campfire.presentation.resources.settings_app_icon_launcher
import com.pandulapeter.campfire.presentation.resources.settings_app_icon_launcher_description
import com.pandulapeter.campfire.presentation.resources.settings_app_icon_taskbar
import com.pandulapeter.campfire.presentation.resources.settings_app_icon_taskbar_description
import com.pandulapeter.campfire.presentation.resources.settings_app_icon_window
import com.pandulapeter.campfire.presentation.resources.settings_app_icon_window_description
import com.pandulapeter.campfire.presentation.resources.settings_created_by
import com.pandulapeter.campfire.presentation.resources.settings_distribution_app_store
import com.pandulapeter.campfire.presentation.resources.settings_distribution_mac_app_store
import com.pandulapeter.campfire.presentation.resources.settings_distribution_microsoft_store
import com.pandulapeter.campfire.presentation.resources.settings_distribution_play_store
import com.pandulapeter.campfire.presentation.resources.settings_distributions_all
import com.pandulapeter.campfire.presentation.resources.settings_distributions_all_description
import com.pandulapeter.campfire.presentation.resources.settings_export_all
import com.pandulapeter.campfire.presentation.resources.settings_general
import com.pandulapeter.campfire.presentation.resources.settings_german_notation
import com.pandulapeter.campfire.presentation.resources.settings_german_notation_description
import com.pandulapeter.campfire.presentation.resources.settings_git_hub
import com.pandulapeter.campfire.presentation.resources.settings_git_hub_description
import com.pandulapeter.campfire.presentation.resources.settings_horizontal_section_flow
import com.pandulapeter.campfire.presentation.resources.settings_horizontal_section_flow_description
import com.pandulapeter.campfire.presentation.resources.settings_import
import com.pandulapeter.campfire.presentation.resources.settings_library
import com.pandulapeter.campfire.presentation.resources.settings_library_location
import com.pandulapeter.campfire.presentation.resources.settings_library_location_files_app
import com.pandulapeter.campfire.presentation.resources.settings_library_size
import com.pandulapeter.campfire.presentation.resources.settings_library_size_bytes
import com.pandulapeter.campfire.presentation.resources.settings_library_size_decimal_separator
import com.pandulapeter.campfire.presentation.resources.settings_library_size_gigabytes
import com.pandulapeter.campfire.presentation.resources.settings_library_size_kilobytes
import com.pandulapeter.campfire.presentation.resources.settings_library_size_megabytes
import com.pandulapeter.campfire.presentation.resources.settings_library_storage
import com.pandulapeter.campfire.presentation.resources.settings_library_storage_best_effort
import com.pandulapeter.campfire.presentation.resources.settings_library_storage_granted
import com.pandulapeter.campfire.presentation.resources.settings_library_summary
import com.pandulapeter.campfire.presentation.resources.settings_lyrics_only_mode
import com.pandulapeter.campfire.presentation.resources.settings_lyrics_only_mode_description
import com.pandulapeter.campfire.presentation.resources.settings_performance_mode
import com.pandulapeter.campfire.presentation.resources.settings_performance_mode_description
import com.pandulapeter.campfire.presentation.resources.settings_privacy_policy
import com.pandulapeter.campfire.presentation.resources.settings_privacy_policy_description
import com.pandulapeter.campfire.presentation.resources.settings_rate
import com.pandulapeter.campfire.presentation.resources.settings_rate_description
import com.pandulapeter.campfire.presentation.resources.settings_report_issue
import com.pandulapeter.campfire.presentation.resources.settings_report_issue_description
import com.pandulapeter.campfire.presentation.resources.settings_songs
import com.pandulapeter.campfire.presentation.resources.settings_support
import com.pandulapeter.campfire.presentation.resources.settings_support_description
import com.pandulapeter.campfire.presentation.resources.settings_sync
import com.pandulapeter.campfire.presentation.resources.settings_user_interface_language
import com.pandulapeter.campfire.presentation.resources.settings_user_interface_language_english
import com.pandulapeter.campfire.presentation.resources.settings_user_interface_language_hungarian
import com.pandulapeter.campfire.presentation.resources.settings_user_interface_language_more_coming
import com.pandulapeter.campfire.presentation.resources.settings_user_interface_language_system_default
import com.pandulapeter.campfire.presentation.resources.settings_user_interface_language_with_own_name
import com.pandulapeter.campfire.presentation.resources.settings_user_interface_theme
import com.pandulapeter.campfire.presentation.resources.settings_user_interface_theme_color
import com.pandulapeter.campfire.presentation.resources.settings_version
import com.pandulapeter.campfire.presentation.ui.CampfireViewModel
import com.pandulapeter.campfire.presentation.ui.components.ActionListItem
import com.pandulapeter.campfire.presentation.ui.components.ImportProgress
import com.pandulapeter.campfire.presentation.ui.components.LinkListItem
import com.pandulapeter.campfire.presentation.ui.components.RadioListItem
import com.pandulapeter.campfire.presentation.ui.components.SegmentedChoice
import com.pandulapeter.campfire.presentation.ui.components.SwitchListItem
import com.pandulapeter.campfire.presentation.ui.components.ThemeColorChoice
import com.pandulapeter.campfire.presentation.ui.components.UiModeChoice
import com.pandulapeter.campfire.presentation.ui.components.fadingLeftEdge
import com.pandulapeter.campfire.presentation.ui.components.rememberRetainedScrollState
import com.pandulapeter.campfire.presentation.ui.navigation.CampfireDestination
import com.pandulapeter.campfire.presentation.ui.platform.AppIconSurface
import com.pandulapeter.campfire.presentation.ui.platform.Distribution
import com.pandulapeter.campfire.presentation.ui.platform.LibraryLocation
import com.pandulapeter.campfire.presentation.ui.platform.LibraryPersistence
import com.pandulapeter.campfire.presentation.ui.platform.LocalFilePicker
import com.pandulapeter.campfire.presentation.ui.platform.appIconSurface
import com.pandulapeter.campfire.presentation.ui.platform.canAskForDonations
import com.pandulapeter.campfire.presentation.ui.platform.libraryLocation
import com.pandulapeter.campfire.presentation.ui.platform.platformStore
import com.pandulapeter.campfire.presentation.ui.theme.CampfireColorScheme
import com.pandulapeter.campfire.presentation.ui.theme.colorSchemePair
import kotlinx.coroutines.launch
import org.jetbrains.compose.resources.painterResource

/**
 * The settings of the app as four [SettingsTab]s. Once the screen is wider than the tab row's maximum width they are the entries
 * of a [SettingsCategoryPane] at the start of the screen with the selected page next to it, the way a list and its
 * detail sit side by side; anywhere narrower they are a row of tabs at the top with one page of a pager under each
 * (see [SettingsTabPager]), so that they stay in reach however far a page is scrolled. Either way there is no app bar
 * above them: the screen has nothing to put in one but its name, which the navigation bar or rail already says. Each
 * tab holds a section or two, laid out side by side where the room next to the pane, or the whole window, has room for
 * them (see [SettingsPage]).
 *
 * The tabs lie flat on the screen and never tint or lift as a page scrolls under them, a page scrolled under them
 * fading out into them instead.
 *
 * Every page is composed as the screen is rather than as it is first swiped to, and everything a section draws is a
 * state the view model already holds by then, so the first frame of a tab is the tab as it is rather than a guess that
 * the next frame corrects - and what does change afterwards (a sync run starting, the demo songs arriving) is animated
 * inside its section by [AnimatedSettingsRow].
 *
 * @param settledWidth The width of the screen once the navigation bars have finished animating, which is what the
 *   layout and the number of columns are decided from.
 */
@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
internal fun SettingsScreen(
    modifier: Modifier = Modifier,
    viewModel: CampfireViewModel,
    settledWidth: Dp,
    contentPadding: PaddingValues,
    isNavigationRailVisible: Boolean,
    urlOpener: (String) -> Unit,
) {
    val scrollStates = SettingsTab.entries.map { rememberRetainedScrollState(viewModel.settingsScrollPositions.getValue(it)) }
    val coroutineScope = rememberCoroutineScope()
    val scrollTabToTop = { tab: SettingsTab -> coroutineScope.launch { scrollStates[tab.ordinal].animateScrollTo(0) }; Unit }
    LaunchedEffect(viewModel, scrollStates) {
        viewModel.scrollToTopRequests.collect {
            if (it == CampfireDestination.Settings) {
                viewModel.settingsTab = SettingsTab.GENERAL
                scrollStates[SettingsTab.GENERAL.ordinal].animateScrollTo(0)
            }
        }
    }
    val isImporting by viewModel.isImporting.collectAsStateWithLifecycle()
    val isPerformanceModeEnabled by viewModel.isPerformanceModeEnabled.collectAsStateWithLifecycle()
    val userPreferences by viewModel.userPreferences.collectAsStateWithLifecycle()
    // Read through a derived state, so that a run reporting every file it moves recomposes the sync section alone
    // rather than the whole screen for the one thing the tabs want to know about it.
    val syncState = viewModel.syncState.collectAsStateWithLifecycle()
    val isSyncAnswerPending by remember(syncState) {
        derivedStateOf { (syncState.value as? SyncState.Connected)?.lastOutcome is SyncOutcome.DeletionsNeedConfirmation }
    }
    val layoutDirection = LocalLayoutDirection.current
    val startPadding = contentPadding.calculateStartPadding(layoutDirection)
    val endPadding = contentPadding.calculateEndPadding(layoutDirection)
    val pageWidth = settledWidth - startPadding - endPadding
    val badgedTab = SettingsTab.LIBRARY.takeIf { isSyncAnswerPending }
    // Both layouts are drawn through the fade while a window is resized across the line between them, and so are the
    // pages of the wide one as a category is picked: the pager slides where a finger is dragging it, and everything
    // else changes in place, which is a change of what is on screen rather than of where.
    val fadeSpec = MaterialTheme.motionScheme.defaultEffectsSpec<Float>()
    Crossfade(
        modifier = modifier.fillMaxSize(),
        targetState = pageWidth > (SETTINGS_TAB_ROW_MAX_WIDTH + SETTINGS_CATEGORY_PANE_WIDTH),
        animationSpec = fadeSpec,
    ) { isWide ->
        if (isWide) {
            Column(modifier = Modifier.fillMaxSize()) {
                ImportProgress(isImporting = isImporting)
                Row(modifier = Modifier.weight(1f).fillMaxWidth()) {
                    SettingsCategoryPane(
                        modifier = Modifier.padding(start = startPadding),
                        selectedTab = viewModel.settingsTab,
                        badgedTab = badgedTab,
                        label = { it.label() },
                        onTabSelected = { if (it == viewModel.settingsTab) scrollTabToTop(it) else viewModel.settingsTab = it },
                    )
                    Crossfade(
                        modifier = Modifier.weight(1f),
                        targetState = viewModel.settingsTab,
                        animationSpec = fadeSpec,
                    ) { tab ->
                        SettingsTabPage(
                            viewModel = viewModel,
                            tab = tab,
                            settledWidth = pageWidth - SETTINGS_CATEGORY_PANE_WIDTH,
                            scrollState = scrollStates[tab.ordinal],
                            contentPadding = PaddingValues(end = endPadding, bottom = contentPadding.calculateBottomPadding()),
                            isImporting = isImporting,
                            isPerformanceModeEnabled = isPerformanceModeEnabled,
                            userPreferences = userPreferences,
                            urlOpener = urlOpener,
                        )
                    }
                }
            }
        } else {
            SettingsTabPager(
                viewModel = viewModel,
                badgedTab = badgedTab,
                isImporting = isImporting,
                startPadding = startPadding,
                endPadding = endPadding,
                isNavigationRailVisible = isNavigationRailVisible,
                onSelectedTabPressed = scrollTabToTop,
            ) { tab ->
                SettingsTabPage(
                    viewModel = viewModel,
                    tab = tab,
                    settledWidth = pageWidth,
                    scrollState = scrollStates[tab.ordinal],
                    contentPadding = contentPadding,
                    isImporting = isImporting,
                    isPerformanceModeEnabled = isPerformanceModeEnabled,
                    userPreferences = userPreferences,
                    urlOpener = urlOpener,
                )
            }
        }
    }
}

/**
 * The tabs of the settings screen where the window is too narrow for [SettingsCategoryPane]: a row of tabs with one
 * page of a pager under each. It keeps its own pager state, so that the tab set by the pane is what it opens on when a
 * window narrows and the pane's choice is never overwritten by a pager that was left behind.
 *
 * Every page is composed with the screen ([HorizontalPager]'s `beyondViewportPageCount` covers them all), so a tab is
 * never first composed as it is swiped to.
 */
@Composable
private fun SettingsTabPager(
    modifier: Modifier = Modifier,
    viewModel: CampfireViewModel,
    badgedTab: SettingsTab?,
    isImporting: Boolean,
    startPadding: Dp,
    endPadding: Dp,
    isNavigationRailVisible: Boolean,
    onSelectedTabPressed: (SettingsTab) -> Unit,
    page: @Composable (SettingsTab) -> Unit,
) {
    val pagerState = rememberPagerState(initialPage = viewModel.settingsTab.ordinal) { SettingsTab.entries.size }
    val coroutineScope = rememberCoroutineScope()
    // Every time the pages come to rest rather than once as the screen is left, because the web build's address names
    // the tab that is open. The settled page, so that a swipe is one change of address rather than two.
    LaunchedEffect(pagerState) {
        snapshotFlow { pagerState.settledPage }.collect { viewModel.settingsTab = SettingsTab.entries[it] }
    }
    // The other direction: the wide layout's pane and the web build's address set the tab from outside, and the pager
    // has to follow it, which it only does for a value it did not settle on itself.
    LaunchedEffect(pagerState) {
        snapshotFlow { viewModel.settingsTab }.collect {
            if (it.ordinal != pagerState.targetPage) pagerState.animateScrollToPage(it.ordinal)
        }
    }
    Column(modifier = modifier.fillMaxSize()) {
        // The tab being headed for rather than the one on screen, so that the indicator sets off the moment a tab is
        // pressed instead of waiting for the pages to pass the halfway point.
        SettingsTabRow(
            selectedTab = SettingsTab.entries[pagerState.targetPage],
            // The library is not synced until a run that stopped to ask whether its deletions are meant has been
            // answered, and the question is in the sync section, which may be a tab away.
            badgedTab = badgedTab,
            label = { it.label() },
            startPadding = startPadding,
            endPadding = endPadding,
            onTabSelected = {
                if (it.ordinal == pagerState.targetPage) onSelectedTabPressed(it) else coroutineScope.launch { pagerState.animateScrollToPage(it.ordinal) }
            },
        )
        ImportProgress(isImporting = isImporting)
        val fadeAlpha = animateFloatAsState(
            if (isNavigationRailVisible && (pagerState.isScrollInProgress || pagerState.currentPageOffsetFraction != 0f)) 1f else 0f
        )
        HorizontalPager(
            modifier = Modifier
                .weight(1f)
                .fillMaxWidth()
                .fadingLeftEdge(fadeAlpha.value),
            state = pagerState,
            beyondViewportPageCount = SettingsTab.entries.size - 1,
            key = { SettingsTab.entries[it] },
        ) { page(SettingsTab.entries[it]) }
    }
}

@Composable
private fun SettingsTabPage(
    modifier: Modifier = Modifier,
    viewModel: CampfireViewModel,
    tab: SettingsTab,
    settledWidth: Dp,
    scrollState: ScrollState,
    contentPadding: PaddingValues,
    isImporting: Boolean,
    isPerformanceModeEnabled: Boolean,
    userPreferences: UserPreferences?,
    urlOpener: (String) -> Unit,
) = when (tab) {
    SettingsTab.GENERAL -> SettingsPage(
        modifier = modifier,
        settledWidth = settledWidth,
        scrollState = scrollState,
        contentPadding = contentPadding,
        section = {
            GeneralSection(
                viewModel = viewModel,
                userPreferences = userPreferences,
                isPerformanceModeEnabled = isPerformanceModeEnabled,
            )
        },
    )

    SettingsTab.SONGS -> SettingsPage(
        modifier = modifier,
        settledWidth = settledWidth,
        scrollState = scrollState,
        contentPadding = contentPadding,
        section = { SongDisplaySection(viewModel = viewModel, userPreferences = userPreferences) },
    )

    SettingsTab.LIBRARY -> SettingsPage(
        modifier = modifier,
        settledWidth = settledWidth,
        scrollState = scrollState,
        contentPadding = contentPadding,
        section = { SyncSection(viewModel = viewModel) },
        secondSection = {
            LibrarySection(
                viewModel = viewModel,
                isImporting = isImporting,
                isPerformanceModeEnabled = isPerformanceModeEnabled,
            )
        },
    )

    SettingsTab.ABOUT -> SettingsPage(
        modifier = modifier,
        settledWidth = settledWidth,
        scrollState = scrollState,
        contentPadding = contentPadding,
        section = { AboutSection(urlOpener = urlOpener) },
    )
}

@Composable
private fun SongDisplaySection(
    viewModel: CampfireViewModel,
    userPreferences: UserPreferences?,
) = SettingsSection {
    SwitchListItem(
        title = stringResource(Res.string.settings_lyrics_only_mode),
        description = stringResource(Res.string.settings_lyrics_only_mode_description),
        isChecked = userPreferences?.isLyricsOnlyModeEnabled == true,
        onCheckedChange = viewModel::setLyricsOnlyModeEnabled,
    )
    SwitchListItem(
        title = stringResource(Res.string.settings_horizontal_section_flow),
        description = stringResource(Res.string.settings_horizontal_section_flow_description),
        isChecked = userPreferences?.isHorizontalSectionFlowEnabled == true,
        onCheckedChange = viewModel::setHorizontalSectionFlowEnabled,
    )
    // Both of these only decide how a chord is written, so lyrics only mode leaves them with nothing to say. They
    // stay in the section rather than disappearing from it: what they are set to is still what the chords will look
    // like as soon as they are shown again.
    val isChordSpellingEnabled = userPreferences?.isLyricsOnlyModeEnabled != true
    SwitchListItem(
        title = stringResource(Res.string.settings_german_notation),
        description = stringResource(Res.string.settings_german_notation_description),
        isChecked = userPreferences?.chordSpelling?.isGermanNotationEnabled == true,
        isEnabled = isChordSpellingEnabled,
        onCheckedChange = viewModel::setGermanNotationEnabled,
    )
    SettingsSubsection(
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

/**
 * Performance mode is the first row of the first tab: what it does is take controls out of the interface, which is
 * what files it next to the theme and the language, and it decides what the rest of the app is still allowed to do,
 * which is what puts it on top.
 */
@Composable
private fun GeneralSection(
    viewModel: CampfireViewModel,
    userPreferences: UserPreferences?,
    isPerformanceModeEnabled: Boolean,
) = SettingsSection {
    SwitchListItem(
        title = stringResource(Res.string.settings_performance_mode),
        description = stringResource(Res.string.settings_performance_mode_description),
        isChecked = isPerformanceModeEnabled,
        onCheckedChange = viewModel::setPerformanceModeEnabled,
    )
    SettingsSubsection(title = stringResource(Res.string.settings_user_interface_theme)) {
        UiModeChoice(
            selected = userPreferences?.uiMode,
            onSelected = viewModel::setUiMode,
        )
    }
    SettingsSubsection(title = stringResource(Res.string.settings_user_interface_theme_color)) {
        ThemeColorChoice(
            uiMode = userPreferences?.uiMode,
            selected = userPreferences?.themeColor,
            onSelected = viewModel::setThemeColor,
        )
    }
    // Named after the icon it colors, which is a different one on every platform, and described with what that
    // platform lets it do: the one thing all of them share is that a color picked here reaches the icon at all. It is
    // disabled wherever the theme is drawn in the app's own palette - the gray itself, or the System color on a device
    // that hands out none - since the icon is the gray one then whichever way it is set. The System color where it is
    // honored is a color like the rest, whose launcher icon the wallpaper tints. Disabled, it shows as on, since the
    // gray icon is then the icon of the color picked rather than the icon kept instead of it; the preference itself is
    // left as the user set it, for the next color that can reach the icon.
    val canColorAppIcon = colorSchemePair(userPreferences?.themeColor) !== CampfireColorScheme
    SwitchListItem(
        title = appIconSurface.title(),
        description = appIconSurface.description(),
        isChecked = !canColorAppIcon || userPreferences?.isAppIconThemed == true,
        isEnabled = canColorAppIcon,
        onCheckedChange = viewModel::setAppIconThemed,
    )
    // A list rather than a segmented control, since it is the one choice here that grows with every translation,
    // and a row of segments runs out of width after the third. Every language the app is not set to also carries its
    // name in itself, read out of its own string table, so that somebody who ended up in one they cannot read can
    // still find theirs; the one it is set to is already named in itself, and saying so twice reads as a mistake.
    SettingsSubsection(title = stringResource(Res.string.settings_user_interface_language)) {
        Column(modifier = Modifier.selectableGroup()) {
            listOf(
                UserPreferences.Language.SYSTEM_DEFAULT to Res.string.settings_user_interface_language_system_default,
                UserPreferences.Language.ENGLISH to Res.string.settings_user_interface_language_english,
                UserPreferences.Language.HUNGARIAN to Res.string.settings_user_interface_language_hungarian,
            ).forEach { (language, name) ->
                val isSelected = userPreferences?.language == language
                val localName = stringResource(name)
                val ownName = if (language == UserPreferences.Language.SYSTEM_DEFAULT) {
                    localName
                } else {
                    LocalizedStrings.get(name, locale = AppLocale.findByCode(language.id))
                }
                RadioListItem(
                    title = if (isSelected || ownName == localName) {
                        localName
                    } else {
                        stringResource(Res.string.settings_user_interface_language_with_own_name, localName, ownName)
                    },
                    isSelected = isSelected,
                    onSelected = { viewModel.setLanguage(language) },
                )
            }
        }
        SettingsMessage(text = stringResource(Res.string.settings_user_interface_language_more_coming))
    }
}

/**
 * What the library holds and where it is, then what can be done with the whole of it.
 *
 * The two actions are disabled rather than hidden by performance mode, like the chord spelling under lyrics only
 * mode: this screen is the one place the mode can be switched back off, and a settings screen whose rows come and go
 * with a switch on it is one nobody can find their way around.
 */
@Composable
private fun LibrarySection(
    viewModel: CampfireViewModel,
    isImporting: Boolean,
    isPerformanceModeEnabled: Boolean,
) = SettingsSection(title = stringResource(Res.string.settings_library)) {
    // Null until the library has been read, so that the row arrives with real counts instead of showing zeroes.
    val librarySummary by viewModel.librarySummary.collectAsStateWithLifecycle()
    val demoLibraryOffer by viewModel.demoLibraryOffer.collectAsStateWithLifecycle()
    val libraryPersistence by viewModel.libraryPersistence.collectAsStateWithLifecycle()
    val filePicker = LocalFilePicker.current
    AnimatedSettingsRow(value = librarySummary) { summary ->
        ListItem(
            colors = ListItemDefaults.colors(containerColor = Color.Transparent),
            headlineContent = { Text(stringResource(Res.string.settings_library_summary, summary.songCount, summary.setlistCount)) },
            supportingContent = { Text(stringResource(Res.string.settings_library_size, formattedSize(summary.size))) },
        )
    }
    libraryLocation?.let { location ->
        ListItem(
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
    // Only where the answer is not a foregone conclusion, which is the web: the other three platforms keep the
    // library in a file system of their own and have the location row above instead.
    AnimatedSettingsRow(value = libraryPersistence.takeIf { it != LibraryPersistence.GUARANTEED }) { persistence ->
        ListItem(
            colors = ListItemDefaults.colors(containerColor = Color.Transparent),
            headlineContent = { Text(stringResource(Res.string.settings_library_storage)) },
            supportingContent = {
                Text(
                    text = when (persistence) {
                        LibraryPersistence.GRANTED -> stringResource(Res.string.settings_library_storage_granted)
                        else -> stringResource(Res.string.settings_library_storage_best_effort)
                    },
                    color = if (persistence == LibraryPersistence.GRANTED) Color.Unspecified else MaterialTheme.colorScheme.error,
                )
            },
        )
    }
    ActionListItem(
        title = stringResource(Res.string.settings_import),
        icon = painterResource(Res.drawable.ic_import),
        isEnabled = !isImporting && !isPerformanceModeEnabled,
        isEmphasized = false,
        onClick = { viewModel.importFiles(filePicker) },
    )
    // Only until they are all in the library, which is also what brings it back for the ones a user who wanted none
    // of them has deleted. Null while the library is still being read, so the offer never appears for a moment over
    // a library that turns out to hold them.
    AnimatedSettingsRow(value = demoLibraryOffer) { offer ->
        ActionListItem(
            title = stringResource(Res.string.add_demo_songs),
            icon = painterResource(Res.drawable.ic_songs),
            isEnabled = offer == CampfireViewModel.DemoLibraryOffer.AVAILABLE && !isPerformanceModeEnabled,
            isEmphasized = false,
            onClick = viewModel::importDemoLibrary,
        )
    }
    ActionListItem(
        title = stringResource(Res.string.settings_export_all),
        icon = painterResource(Res.drawable.ic_export),
        isEnabled = !isPerformanceModeEnabled,
        isEmphasized = false,
        onClick = { viewModel.exportLibrary(filePicker) },
    )
}

/**
 * [bytes] in the largest unit that keeps the number at least one, in decimal units as the file browsers of Android and
 * Apple count them. One decimal below ten and none above, which is as precise as a number that changes with every
 * saved song is worth being. The separator comes from the strings rather than from the platform, so that it follows
 * the language chosen in the app like the words around it.
 */
@Composable
private fun formattedSize(bytes: Long): String {
    // The unit is settled on the rounded number, so that 999 960 bytes read as 1.0 MB rather than as 1000 KB.
    val unit = (1..SIZE_UNITS.lastIndex).firstOrNull { unit -> (bytes + SIZE_STEP.pow(unit) / 2) / SIZE_STEP.pow(unit + 1) == 0L }
        ?: SIZE_UNITS.lastIndex
    val divisor = SIZE_STEP.pow(unit)
    val tenths = (bytes * 10 + divisor / 2) / divisor
    val number = when {
        bytes < SIZE_STEP -> bytes.toString()
        tenths < 100 -> "${tenths / 10}${stringResource(Res.string.settings_library_size_decimal_separator)}${tenths % 10}"
        else -> ((bytes + divisor / 2) / divisor).toString()
    }
    return stringResource(if (bytes < SIZE_STEP) SIZE_UNITS.first() else SIZE_UNITS[unit], number)
}

private fun Long.pow(exponent: Int) = (1..exponent).fold(1L) { result, _ -> result * this }

private const val SIZE_STEP = 1000L

private val SIZE_UNITS = listOf(
    Res.string.settings_library_size_bytes,
    Res.string.settings_library_size_kilobytes,
    Res.string.settings_library_size_megabytes,
    Res.string.settings_library_size_gigabytes,
)

/**
 * Collects the sync state itself, so that a run reporting every file it moves recomposes this section and no other.
 * It is the first section of the library's tab, since it is the one with something going on in it.
 */
@Composable
private fun SyncSection(
    viewModel: CampfireViewModel,
) = SettingsSection(title = stringResource(Res.string.settings_sync)) {
    val syncState by viewModel.syncState.collectAsStateWithLifecycle()
    SyncSettings(viewModel = viewModel, syncState = syncState)
}

/**
 * What the app is and where it lives: GitHub is both its home page and where a problem is reported, and it is also
 * where every build of Campfire is listed, which is the whole of what the app says about the other platforms - a
 * page can be kept up to date without a release, and it is the one place App Review has nothing to say about. The row
 * that names the author is the link to the author's own site, since that is what a name with a link on it is expected
 * to lead to.
 *
 * The rating row leads to the store of the platform the app is running on, and only once that listing exists. It
 * says "rate", never "get it from the store": a store page has an install button on it, and somebody who has the
 * direct download would end up with a second copy of the app, sandboxed, with a library of its own.
 */
@Composable
private fun AboutSection(
    urlOpener: (String) -> Unit,
) = SettingsSection {
    // The README's "Get Campfire" section, whose anchor GitHub derives from the heading, so renaming that heading
    // means changing it here.
    LinkListItem(
        title = stringResource(Res.string.settings_distributions_all),
        description = stringResource(Res.string.settings_distributions_all_description),
        icon = painterResource(Res.drawable.ic_phone),
        onClick = { urlOpener("$GIT_HUB_URL#get-campfire") },
    )
    LinkListItem(
        title = stringResource(Res.string.settings_git_hub),
        description = stringResource(Res.string.settings_git_hub_description),
        icon = painterResource(Res.drawable.ic_git_hub),
        onClick = { urlOpener(GIT_HUB_URL) },
    )
    LinkListItem(
        title = stringResource(Res.string.settings_report_issue),
        description = stringResource(Res.string.settings_report_issue_description),
        icon = painterResource(Res.drawable.ic_bug),
        onClick = { urlOpener("$GIT_HUB_URL/issues") },
    )
    platformStore?.let { store ->
        store.listingUrl?.let { listingUrl ->
            LinkListItem(
                title = stringResource(Res.string.settings_rate),
                description = stringResource(Res.string.settings_rate_description, stringResource(store.storeName)),
                icon = painterResource(Res.drawable.ic_star),
                onClick = { urlOpener(listingUrl) },
            )
        }
    }
    LinkListItem(
        title = stringResource(Res.string.settings_privacy_policy),
        description = stringResource(Res.string.settings_privacy_policy_description),
        icon = painterResource(Res.drawable.ic_privacy_policy),
        onClick = { urlOpener("https://pandulapeter.com/legal/privacy_policy-campfire.html") },
    )
    LinkListItem(
        title = stringResource(Res.string.settings_created_by),
        description = stringResource(Res.string.settings_version, CAMPFIRE_VERSION_NAME),
        icon = painterResource(Res.drawable.ic_campfire),
        onClick = { urlOpener("https://pandulapeter.com/") },
    )
    if (canAskForDonations) {
        LinkListItem(
            title = stringResource(Res.string.settings_support),
            description = stringResource(Res.string.settings_support_description),
            icon = painterResource(Res.drawable.ic_coffee),
            onClick = { urlOpener("https://buymeacoffee.com/pandulapeter") },
        )
    }
}

/** What a tab of the settings screen is called. */
@Composable
private fun SettingsTab.label() = stringResource(
    when (this) {
        SettingsTab.GENERAL -> Res.string.settings_general
        SettingsTab.SONGS -> Res.string.settings_songs
        SettingsTab.LIBRARY -> Res.string.settings_library
        SettingsTab.ABOUT -> Res.string.settings_about
    }
)

/** The name a rating is left under, which is the one thing the app still has to call a store. */
private val Distribution.storeName
    get() = when (this) {
        Distribution.PLAY_STORE -> Res.string.settings_distribution_play_store
        Distribution.APP_STORE -> Res.string.settings_distribution_app_store
        Distribution.MAC_APP_STORE -> Res.string.settings_distribution_mac_app_store
        Distribution.MICROSOFT_STORE -> Res.string.settings_distribution_microsoft_store
    }

/** What the icon the theme color can reach is called on this platform. */
@Composable
private fun AppIconSurface.title() = stringResource(
    when (this) {
        AppIconSurface.LAUNCHER -> Res.string.settings_app_icon_launcher
        AppIconSurface.HOME_SCREEN -> Res.string.settings_app_icon_home_screen
        AppIconSurface.DOCK -> Res.string.settings_app_icon_dock
        AppIconSurface.TASKBAR -> Res.string.settings_app_icon_taskbar
        AppIconSurface.WINDOW -> Res.string.settings_app_icon_window
        AppIconSurface.BROWSER_TAB -> Res.string.settings_app_icon_browser_tab
    }
)

/** What coloring that icon does here, and where it stops. */
@Composable
private fun AppIconSurface.description() = stringResource(
    when (this) {
        AppIconSurface.LAUNCHER -> Res.string.settings_app_icon_launcher_description
        AppIconSurface.HOME_SCREEN -> Res.string.settings_app_icon_home_screen_description
        AppIconSurface.DOCK -> Res.string.settings_app_icon_dock_description
        AppIconSurface.TASKBAR -> Res.string.settings_app_icon_taskbar_description
        AppIconSurface.WINDOW -> Res.string.settings_app_icon_window_description
        AppIconSurface.BROWSER_TAB -> Res.string.settings_app_icon_browser_tab_description
    }
)

private const val GIT_HUB_URL = "https://github.com/pandulapeter/campfire"
