/*
 * This file is part of Campfire.
 * Copyright (c) Pandula Péter 2017-2026.
 * https://github.com/pandulapeter/campfire
 *
 * This Source Code Form is subject to the terms of the Mozilla Public License, v. 2.0.
 * If a copy of the MPL was not distributed with this file, You can obtain one at
 * https://mozilla.org/MPL/2.0/.
 */
package com.pandulapeter.campfire.presentation.ui.screens.songs

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.Crossfade
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.Badge
import androidx.compose.material3.BadgedBox
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.layout.layout
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.pandulapeter.campfire.presentation.localization.stringResource
import com.pandulapeter.campfire.presentation.resources.Res
import com.pandulapeter.campfire.presentation.resources.ic_filter
import com.pandulapeter.campfire.presentation.resources.ic_filter_outline
import com.pandulapeter.campfire.presentation.resources.songs_create_song
import com.pandulapeter.campfire.presentation.resources.songs_filter
import com.pandulapeter.campfire.presentation.resources.songs_filter_active
import com.pandulapeter.campfire.presentation.resources.songs_new_song
import com.pandulapeter.campfire.presentation.resources.songs_search
import com.pandulapeter.campfire.presentation.ui.CampfireViewModel
import com.pandulapeter.campfire.presentation.ui.components.AppBarOverlap
import com.pandulapeter.campfire.presentation.ui.components.ControlsSidePanel
import com.pandulapeter.campfire.presentation.ui.components.DismissSheetWhenSidePanelAppears
import com.pandulapeter.campfire.presentation.ui.components.HideKeyboardWhenScrolledDown
import com.pandulapeter.campfire.presentation.ui.components.ListLayout
import com.pandulapeter.campfire.presentation.ui.components.ImportProgress
import com.pandulapeter.campfire.presentation.ui.components.ListUnderAppBar
import com.pandulapeter.campfire.presentation.ui.components.NewItemMenu
import com.pandulapeter.campfire.presentation.ui.components.SearchableTopAppBar
import com.pandulapeter.campfire.presentation.ui.components.SongFilters
import com.pandulapeter.campfire.presentation.ui.components.SongSortMenu
import com.pandulapeter.campfire.presentation.ui.components.allowsNewItemMenu
import com.pandulapeter.campfire.presentation.ui.components.animateAppBarReveal
import com.pandulapeter.campfire.presentation.ui.components.besideSidePanel
import com.pandulapeter.campfire.presentation.ui.components.only
import com.pandulapeter.campfire.presentation.ui.components.overlappingAction
import com.pandulapeter.campfire.presentation.ui.components.rememberHasLoadedLibrary
import com.pandulapeter.campfire.presentation.ui.components.rememberRetainedLazyGridState
import com.pandulapeter.campfire.presentation.ui.components.underAppBar
import com.pandulapeter.campfire.presentation.ui.dialogs.DialogType
import com.pandulapeter.campfire.presentation.ui.navigation.CampfireDestination
import org.jetbrains.compose.resources.painterResource

@Composable
internal fun SongsScreen(
    modifier: Modifier = Modifier,
    viewModel: CampfireViewModel,
    layout: ListLayout,
    contentPadding: PaddingValues,
) {
    val isSearchOpen by viewModel.songsSearch.isOpen.collectAsStateWithLifecycle()
    val songGroups by viewModel.songGroups.collectAsStateWithLifecycle()
    val isLoading by viewModel.isLoading.collectAsStateWithLifecycle()
    val placeholder by viewModel.songsPlaceholder.collectAsStateWithLifecycle()
    val isImporting by viewModel.isImporting.collectAsStateWithLifecycle()
    val isPerformanceModeEnabled by viewModel.isPerformanceModeEnabled.collectAsStateWithLifecycle()
    val visibleDialog by viewModel.visibleDialog.collectAsStateWithLifecycle()
    val isSongFilterActive by viewModel.isSongFilterActive.collectAsStateWithLifecycle()
    val hasSongFilters by viewModel.hasSongFilters.collectAsStateWithLifecycle()
    val listState = rememberRetainedLazyGridState(viewModel.songsScrollPosition)
    val isSidePanelVisible = hasSongFilters && layout.hasRoomForSidePanel
    val listContentPadding = contentPadding.besideSidePanel(isSidePanelVisible)
    val columnCount = if (isSidePanelVisible) layout.columnCountBesideSidePanel else layout.columnCount
    val hasLoadedLibrary = rememberHasLoadedLibrary(isLoading)
    HideKeyboardWhenScrolledDown(listState, isEnabled = visibleDialog == null)
    LaunchedEffect(viewModel, listState) {
        viewModel.scrollToTopRequests.collect { if (it == CampfireDestination.Songs) listState.animateScrollToItem(0) }
    }
    DismissSheetWhenSidePanelAppears(
        isSidePanelVisible = isSidePanelVisible,
        isSheetVisible = visibleDialog == DialogType.SongFilters,
        onDismiss = viewModel::dismissDialog,
    )
    // A sync run can take the last tag out of the library under an open sheet, which would leave it empty.
    LaunchedEffect(hasSongFilters) {
        if (!hasSongFilters && visibleDialog == DialogType.SongFilters) viewModel.dismissDialog()
    }
    // The widest the app bar's buttons reach in over the list, which a pinned header keeps clear of - nothing once
    // the bar has filled in and moved the list down under itself.
    var appBarReach by remember { mutableStateOf(0.dp) }
    val appBarReveal = animateAppBarReveal(
        searchState = viewModel.songsSearch,
        // The ranked results of a search come in one group with no header, and a placeholder has none either.
        isShownWithoutSearch = placeholder != null || songGroups.groups.none { it.header != null },
    )
    // Loading can replace a headerless placeholder with the first section. Before search has ever opened,
    // lay that section out fully expanded on its first frame so the grid cannot retain a collapsed first row.
    var hasOpenedSearch by remember { mutableStateOf(isSearchOpen) }
    SideEffect { if (isSearchOpen) hasOpenedSearch = true }
    val isHeaderless = placeholder != null || songGroups.groups.none { it.header != null }
    val appBarProgress = remember(appBarReveal, isSearchOpen, hasOpenedSearch, isHeaderless) {
        { if (isSearchOpen || hasOpenedSearch) appBarReveal.value else if (isHeaderless) 1f else 0f }
    }
    // The list inset and header height read one spring. A scroll anchor compensates the inset exactly,
    // keeping the active section's cards still as later sections close up around them.
    val appBarOverlap: () -> AppBarOverlap = remember(appBarProgress) {
        { AppBarOverlap.of(reach = appBarReach, appBarReveal = appBarProgress()) }
    }
    Box(modifier = modifier.fillMaxSize()) {
        Row {
            // The bar spans the list alone rather than the whole screen, so that its buttons and the search stay at the
            // top of the list they act on instead of standing over the side panel beside it.
            ListUnderAppBar(
                modifier = Modifier.weight(1f).fillMaxHeight(),
                list = {
                    SongList(
                        modifier = Modifier.fillMaxSize().underAppBar { 1f - appBarOverlap().coverage },
                        viewModel = viewModel,
                        listState = listState,
                        placeholder = placeholder,
                        isSearchOpen = isSearchOpen,
                        songGroups = songGroups,
                        columnCount = columnCount,
                        hasLoadedLibrary = hasLoadedLibrary,
                        contentPadding = listContentPadding,
                        appBarOverlap = appBarOverlap,
                    )
                },
            ) {
                SearchableTopAppBar(
                    contentPadding = listContentPadding,
                    appBarReveal = appBarProgress,
                    placeholder = stringResource(Res.string.songs_search),
                    searchState = viewModel.songsSearch,
                    onReachChanged = { appBarReach = it },
                    areClosedSearchActionsShown = !isPerformanceModeEnabled && placeholder.allowsNewItemMenu,
                    closedSearchActions = {
                        NewItemMenu(
                            viewModel = viewModel,
                            contentDescription = stringResource(Res.string.songs_new_song),
                            createLabel = stringResource(Res.string.songs_create_song),
                            onCreate = { viewModel.showDialog(DialogType.NewSong) },
                        )
                    },
                    actions = {
                        SongSortMenu(
                            modifier = Modifier.overlappingAction(),
                            viewModel = viewModel,
                        )
                        // The last tag leaving the library takes the filters with it while the list is being looked at.
                        AnimatedVisibility(
                            modifier = Modifier.overlappingAction(),
                            visible = hasSongFilters && !isSidePanelVisible,
                        ) {
                            SongFiltersAction(
                                isSongFilterActive = isSongFilterActive,
                                onClick = { viewModel.showDialog(DialogType.SongFilters) },
                            )
                        }
                    },
                )
            }
            ControlsSidePanel(
                isVisible = isSidePanelVisible,
                contentPadding = contentPadding,
            ) { panelModifier, panelContentPadding ->
                SongFilters(
                    modifier = panelModifier,
                    viewModel = viewModel,
                    contentPadding = panelContentPadding,
                    fadeBackgroundColor = MaterialTheme.colorScheme.background,
                )
            }
        }
        ImportProgress(isImporting = isImporting)
    }
}

/**
 * The action that opens the filters where there is no room for them beside the list. While a filter is on its funnel
 * is filled in and carries a badge in the accent color, since a filter hides songs for the moment rather than as a
 * standing preference, and a list that is shorter than the library with nothing on screen saying why reads as songs
 * having gone missing. The badge is not Material's default error red: nothing is wrong, the list is only narrowed.
 * The side panel needs no such mark, since the selected chips are in it.
 */
@Composable
private fun SongFiltersAction(
    modifier: Modifier = Modifier,
    isSongFilterActive: Boolean,
    onClick: () -> Unit,
) = IconButton(
    modifier = modifier,
    onClick = onClick,
) {
    BadgedBox(
        badge = {
            AnimatedVisibility(
                visible = isSongFilterActive,
                enter = fadeIn() + scaleIn(),
                exit = fadeOut() + scaleOut(),
            ) {
                Badge(containerColor = MaterialTheme.colorScheme.primary)
            }
        },
    ) {
        Crossfade(targetState = isSongFilterActive) { isActive ->
            Icon(
                painter = painterResource(if (isActive) Res.drawable.ic_filter else Res.drawable.ic_filter_outline),
                // The badge and the fill are drawn and nothing else, so a screen reader would otherwise never hear that
                // the list is narrowed.
                contentDescription = if (isActive) {
                    stringResource(Res.string.songs_filter_active)
                } else {
                    stringResource(Res.string.songs_filter)
                },
            )
        }
    }
}
