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
import androidx.compose.animation.ExperimentalAnimationApi
import androidx.compose.animation.core.SeekableTransitionState
import androidx.compose.animation.core.rememberTransition
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.calculateEndPadding
import androidx.compose.foundation.layout.calculateStartPadding
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigationevent.NavigationEventInfo
import androidx.navigationevent.NavigationEventTransitionState
import androidx.navigationevent.compose.NavigationBackHandler
import androidx.navigationevent.compose.rememberNavigationEventState
import com.pandulapeter.campfire.data.model.domain.SyncOutcome
import com.pandulapeter.campfire.data.model.domain.SyncState
import com.pandulapeter.campfire.presentation.ui.CampfireViewModel
import com.pandulapeter.campfire.presentation.ui.components.ImportProgress
import com.pandulapeter.campfire.presentation.ui.components.only
import com.pandulapeter.campfire.presentation.ui.components.rememberRetainedScrollState
import com.pandulapeter.campfire.presentation.ui.navigation.CampfireDestination
import com.pandulapeter.campfire.presentation.ui.navigation.SettingsTab
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch

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
 * @param layout What the width the screen settles at decides: the layout and the number of columns.
 */
@OptIn(ExperimentalAnimationApi::class, ExperimentalMaterial3ExpressiveApi::class)
@Composable
internal fun SettingsScreen(
    modifier: Modifier = Modifier,
    viewModel: CampfireViewModel,
    layout: SettingsWidthLayout,
    contentPadding: PaddingValues,
    isNavigationRailVisible: Boolean,
    urlOpener: (String) -> Unit,
) {
    val scrollStates = SettingsTab.entries.map { rememberRetainedScrollState(viewModel.settingsScrollPositions.getValue(it)) }
    val coroutineScope = rememberCoroutineScope()
    val scrollTabToTop = { tab: SettingsTab -> coroutineScope.launch { scrollStates[tab.ordinal].animateScrollTo(0) }; Unit }
    LaunchedEffect(viewModel, scrollStates) {
        // Latest rather than plain collect: a finger that touches the page while it scrolls takes the scroll over, and
        // the animation it interrupts ends in a CancellationException, which inside a plain collect would end the whole
        // collector, and every later press of the item with it, without a word. collectLatest runs each request in a
        // child of its own, so an interruption ends that request alone.
        viewModel.scrollToTopRequests.collectLatest {
            if (it == CampfireDestination.Settings) {
                viewModel.settingsTab = SettingsTab.GENERAL
                scrollStates[SettingsTab.GENERAL.ordinal].animateScrollTo(0)
            }
        }
    }
    // Claims the gesture rather than leaving it to the back stack's handler, which would preview the screen giving way to
    // the songs while the finger is still down and then only change the tab once it lifts. Registered whether or not
    // it is enabled, since a handler that comes and goes changes the order the dispatcher picks between handlers in.
    val backGesture = rememberNavigationEventState(currentInfo = NavigationEventInfo.None)
    NavigationBackHandler(
        state = backGesture,
        isBackEnabled = viewModel.isSettingsBackToGeneral,
        onBackCompleted = viewModel::navigateBack,
    )
    // How far a predictive back gesture towards General has come, or null while there is none: both layouts preview
    // the step with it, so that the drag says what letting go will do, and the animation that follows a release picks
    // up from wherever the drag left the pages.
    val backProgress = remember(backGesture) {
        {
            (backGesture.transitionState as? NavigationEventTransitionState.InProgress)
                ?.takeIf { it.direction == NavigationEventTransitionState.TRANSITIONING_BACK }
                ?.latestEvent
                ?.progress
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
    val badgedTab = SettingsTab.LIBRARY.takeIf { isSyncAnswerPending }
    // Both layouts are drawn through the fade while a window is resized across the line between them, and so are the
    // pages of the wide one as a category is picked: the pager slides where a finger is dragging it, and everything
    // else changes in place, which is a change of what is on screen rather than of where.
    val fadeSpec = MaterialTheme.motionScheme.defaultEffectsSpec<Float>()
    Crossfade(
        modifier = modifier.fillMaxSize(),
        targetState = layout.isWide,
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
                    // Seekable, so that a back gesture can hold the fade to General at the fraction the finger has
                    // reached; letting go carries it on to General, and a cancelled gesture fades back from there.
                    val pageTransition = remember { SeekableTransitionState(viewModel.settingsTab) }
                    LaunchedEffect(pageTransition) {
                        snapshotFlow { viewModel.settingsTab to backProgress() }.collectLatest { (tab, progress) ->
                            if (progress != null && tab != SettingsTab.GENERAL) {
                                pageTransition.seekTo(fraction = progress, targetState = SettingsTab.GENERAL)
                            } else {
                                pageTransition.animateTo(tab)
                            }
                        }
                    }
                    rememberTransition(pageTransition).Crossfade(
                        modifier = Modifier.weight(1f),
                        animationSpec = fadeSpec,
                    ) { tab ->
                        SettingsTabPage(
                            viewModel = viewModel,
                            tab = tab,
                            sectionColumns = layout.sectionColumnsBesidePane,
                            scrollState = scrollStates[tab.ordinal],
                            contentPadding = contentPadding.only(bottom = true, extraEnd = endPadding),
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
                backProgress = backProgress,
                onSelectedTabPressed = scrollTabToTop,
            ) { tab ->
                SettingsTabPage(
                    viewModel = viewModel,
                    tab = tab,
                    sectionColumns = layout.sectionColumns,
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
