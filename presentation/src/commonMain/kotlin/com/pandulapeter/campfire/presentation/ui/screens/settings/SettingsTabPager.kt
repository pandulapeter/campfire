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

import androidx.compose.foundation.gestures.Orientation
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.unit.Dp
import com.pandulapeter.campfire.presentation.ui.CampfireViewModel
import com.pandulapeter.campfire.presentation.ui.components.ImportProgress
import com.pandulapeter.campfire.presentation.ui.navigation.SettingsTab
import com.pandulapeter.campfire.presentation.ui.components.bounceScrollableContent
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.filterNotNull
import kotlinx.coroutines.launch

/**
 * The tabs of the settings screen where the window is too narrow for [SettingsCategoryPane]: a row of tabs with one
 * page of a pager under each. It keeps its own pager state, so that the tab set by the pane is what it opens on when a
 * window narrows and the pane's choice is never overwritten by a pager that was left behind.
 *
 * Every page is composed with the screen ([HorizontalPager]'s `beyondViewportPageCount` covers them all), so a tab is
 * never first composed as it is swiped to.
 *
 * @param backProgress How far a predictive back gesture towards General has come, or null while there is none. The
 *   pages follow it the whole way from the open tab to General, the tabs between them passing by the way a press on
 *   General's tab passes them.
 */
@Composable
internal fun SettingsTabPager(
    modifier: Modifier = Modifier,
    viewModel: CampfireViewModel,
    tabs: List<SettingsTab>,
    badgedTab: SettingsTab?,
    isImporting: Boolean,
    startPadding: Dp,
    endPadding: Dp,
    backProgress: () -> Float?,
    onSelectedTabPressed: (SettingsTab) -> Unit,
    page: @Composable (SettingsTab) -> Unit,
) {
    val pagerState = rememberPagerState(initialPage = tabs.indexOf(viewModel.navigator.settingsTab).coerceAtLeast(0)) { tabs.size }
    val coroutineScope = rememberCoroutineScope()
    // Every time the pages come to rest rather than once as the screen is left, because the web build's address names
    // the tab that is open. The settled page, so that a swipe is one change of address rather than two.
    LaunchedEffect(pagerState) {
        snapshotFlow { pagerState.settledPage }.collect { viewModel.navigator.settingsTab = tabs[it] }
    }
    // The other direction: the wide layout's pane and the web build's address set the tab from outside, and the pager
    // has to follow it, which it only does for a value it did not settle on itself. Latest, so that a swipe interrupting
    // the animation ends that animation rather than the collector (see the settings screen's scroll to top requests).
    LaunchedEffect(pagerState) {
        snapshotFlow { viewModel.navigator.settingsTab }.collectLatest {
            val page = tabs.indexOf(it)
            if (page >= 0 && page != pagerState.targetPage) pagerState.animateScrollToPage(page)
        }
    }
    // The gesture scrolls the pages inside one scroll that lasts as long as it does, rather than snapping them frame by
    // frame, since the settled page only stays put while a scroll is in progress and would otherwise write every tab
    // passed on the way into settingsTab - General among them, which would end the gesture's handler halfway through.
    // Letting go ends that scroll wherever the pages are, and they animate on from there: to General, which the handler
    // has just made the tab, or back to the tab the gesture started on when it was cancelled.
    LaunchedEffect(pagerState) {
        snapshotFlow { backProgress() != null }.collectLatest { isGestureInProgress ->
            val tabPage = tabs.indexOf(viewModel.navigator.settingsTab).coerceAtLeast(0)
            if (isGestureInProgress) {
                pagerState.scroll {
                    snapshotFlow { backProgress() }.filterNotNull().collect { progress ->
                        val pageWidth = pagerState.layoutInfo.pageSize + pagerState.layoutInfo.pageSpacing
                        val position = pagerState.currentPage + pagerState.currentPageOffsetFraction
                        scrollBy((tabPage * (1f - progress) - position) * pageWidth)
                    }
                }
            } else if (pagerState.currentPage != tabPage || pagerState.currentPageOffsetFraction != 0f) {
                pagerState.animateScrollToPage(tabPage)
            }
        }
    }
    Column(modifier = modifier.fillMaxSize()) {
        // The tab being headed for rather than the one on screen, so that its name lights up the moment it is pressed
        // instead of waiting for the pages to pass the halfway point; the indicator follows the pages themselves.
        SettingsTabRow(
            tabs = tabs,
            selectedTab = tabs[pagerState.targetPage],
            indicatorPosition = { pagerState.currentPage + pagerState.currentPageOffsetFraction },
            // The library is not synced until a run that stopped to ask whether its deletions are meant has been
            // answered, and the question is in the sync section, which may be a tab away.
            badgedTab = badgedTab,
            label = { it.label() },
            startPadding = startPadding,
            endPadding = endPadding,
            onTabSelected = {
                val page = tabs.indexOf(it)
                if (page == pagerState.targetPage) onSelectedTabPressed(it) else coroutineScope.launch { pagerState.animateScrollToPage(page) }
            },
        )
        ImportProgress(isImporting = isImporting)
        HorizontalPager(
            modifier = Modifier.bounceScrollableContent(pagerState, Orientation.Horizontal)
                .weight(1f)
                .fillMaxWidth(),
            state = pagerState,
            beyondViewportPageCount = tabs.size - 1,
            key = { tabs[it] },
        ) { page(tabs[it]) }
    }
}
