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

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.EnterTransition
import androidx.compose.animation.core.VisibilityThreshold
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.animation.SizeTransform
import androidx.compose.animation.expandHorizontally
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.Badge
import androidx.compose.material3.BadgedBox
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigationevent.NavigationEventInfo
import androidx.navigationevent.compose.NavigationBackHandler
import androidx.navigationevent.compose.rememberNavigationEventState
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
import com.pandulapeter.campfire.presentation.ui.components.appBar.AppBarOverlap
import com.pandulapeter.campfire.presentation.ui.components.appBar.PillIconButton
import com.pandulapeter.campfire.presentation.ui.components.songFilters.ControlsSidePanel
import com.pandulapeter.campfire.presentation.ui.screens.songs.DismissSheetWhenSidePanelAppears
import com.pandulapeter.campfire.presentation.ui.components.HideKeyboardWhenScrolledDown
import com.pandulapeter.campfire.presentation.ui.components.ImportProgress
import com.pandulapeter.campfire.presentation.ui.components.list.ListLayout
import com.pandulapeter.campfire.presentation.ui.components.list.ListUnderAppBar
import com.pandulapeter.campfire.presentation.ui.components.menu.LocalOverlayState
import com.pandulapeter.campfire.presentation.ui.components.menu.NewItemMenu
import com.pandulapeter.campfire.presentation.ui.platform.rememberClipboardTextReader
import com.pandulapeter.campfire.presentation.ui.components.appBar.SearchableTopAppBar
import com.pandulapeter.campfire.presentation.ui.components.songFilters.SongFilters
import com.pandulapeter.campfire.presentation.ui.components.menu.SongSortMenu
import com.pandulapeter.campfire.presentation.ui.components.list.allowsNewItemMenu
import com.pandulapeter.campfire.presentation.ui.components.appBar.animateAppBarReveal
import com.pandulapeter.campfire.presentation.ui.components.songFilters.besideSidePanel
import com.pandulapeter.campfire.presentation.ui.components.menu.overlappingAction
import com.pandulapeter.campfire.presentation.ui.components.list.rememberHasLoadedLibrary
import com.pandulapeter.campfire.presentation.ui.components.rememberRetainedLazyGridState
import com.pandulapeter.campfire.presentation.ui.components.appBar.searchTravelSpec
import com.pandulapeter.campfire.presentation.ui.components.appBar.underAppBar
import com.pandulapeter.campfire.presentation.ui.dialogs.DialogType
import com.pandulapeter.campfire.presentation.ui.navigation.CampfireDestination
import com.pandulapeter.campfire.presentation.ui.screens.rememberSongFilterActions
import com.pandulapeter.campfire.presentation.ui.screens.rememberSongFilterUiState
import kotlinx.coroutines.flow.collectLatest
import org.jetbrains.compose.resources.painterResource

@Composable
internal fun SongsScreen(
    modifier: Modifier = Modifier,
    viewModel: CampfireViewModel,
    layout: ListLayout,
    contentPadding: PaddingValues,
) {
    val isSearchOpen by viewModel.songListState.songsSearch.isOpen.collectAsStateWithLifecycle()
    val songGroups by viewModel.songListState.songGroups.collectAsStateWithLifecycle()
    val placeholder by viewModel.songListState.songsPlaceholder.collectAsStateWithLifecycle()
    val isImporting by viewModel.importController.isImporting.collectAsStateWithLifecycle()
    val isPerformanceModeEnabled by viewModel.preferencesController.isPerformanceModeEnabled.collectAsStateWithLifecycle()
    val visibleDialog by viewModel.dialogHost.visibleDialog.collectAsStateWithLifecycle()
    val isSongFilterActive by viewModel.songListState.isSongFilterActive.collectAsStateWithLifecycle()
    val hasSongFilters by viewModel.songListState.hasSongFilters.collectAsStateWithLifecycle()
    val listState = rememberRetainedLazyGridState(viewModel.songListState.songsScrollPosition)
    val isEditingSongs by viewModel.selectionEditing.isEditingSongs.collectAsStateWithLifecycle()
    // Snapshot state, read here so that a tick recomposes the screen it changes and nothing above it.
    val selection = viewModel.songSelection.selectedSongFileNames
    val isSelecting = selection != null && viewModel.songSelection.isSongSelecting && !isPerformanceModeEnabled
    val shownSongGroups = if (isSelecting) {
        key(viewModel.songSelection.session) { rememberSongGroupsWithSelection(songGroups, selection, viewModel.songSelection.selectAllCount) }
    } else {
        songGroups
    }
    val isSidePanelVisible = hasSongFilters && layout.hasRoomForSidePanel
    val listContentPadding = contentPadding.besideSidePanel(isSidePanelVisible)
    val columnCount = if (isSidePanelVisible) layout.columnCountBesideSidePanel else layout.columnCount
    val hasLoadedLibrary = rememberHasLoadedLibrary(isLoading = !songGroups.isLibraryRead)
    val clipboardReader = rememberClipboardTextReader()
    val badgeRecession = songFilterBackRecession(
        isBackEnabled = isSongFilterActive && !isSelecting && !isSearchOpen && visibleDialog == null &&
            !LocalOverlayState.current.isAnyMenuOpen && viewModel.navigator.backStack.size == 1,
        isSongFilterActive = isSongFilterActive,
        onBackCompleted = viewModel.songListState::clearSongFilter,
    )
    HideKeyboardWhenScrolledDown(listState, isEnabled = visibleDialog == null)
    LaunchedEffect(viewModel, listState) {
        // Latest, for the reason the settings screen gives.
        viewModel.navigator.scrollToTopRequests.collectLatest { if (it.destination == CampfireDestination.Songs) listState.animateScrollToItem(0) }
    }
    DismissSheetWhenSidePanelAppears(
        isSidePanelVisible = isSidePanelVisible,
        isSheetVisible = visibleDialog == DialogType.SongFilters,
        onDismiss = viewModel.dialogHost::dismissDialog,
    )
    // A sync run can take the last tag out of the library under an open sheet, which would leave it empty.
    LaunchedEffect(hasSongFilters) {
        if (!hasSongFilters && visibleDialog == DialogType.SongFilters) viewModel.dialogHost.dismissDialog()
    }
    // The widest the app bar's buttons reach in over the list, which a pinned header keeps clear of - nothing once
    // the bar has filled in and moved the list down under itself.
    var appBarReach by remember { mutableStateOf(0.dp) }
    val appBarReveal = animateAppBarReveal(
        searchState = viewModel.songListState.songsSearch,
        // The ranked results of a search come in one group with no header, and a placeholder has none either.
        isShownWithoutSearch = placeholder != null || shownSongGroups.groups.none { it.header != null },
    )
    // Loading can replace a headerless placeholder with the first section. Before search has ever opened,
    // lay that section out fully expanded on its first frame so the grid cannot retain a collapsed first row.
    var hasOpenedSearch by remember { mutableStateOf(isSearchOpen) }
    SideEffect { if (isSearchOpen) hasOpenedSearch = true }
    val isHeaderless = placeholder != null || shownSongGroups.groups.none { it.header != null }
    val appBarProgress = remember(appBarReveal, isSearchOpen, hasOpenedSearch, isHeaderless) {
        { if (isSearchOpen || hasOpenedSearch) appBarReveal.value else if (isHeaderless) 1f else 0f }
    }
    // The list inset and header height read one spring, which KeepCardsInPlaceAcrossSearch narrates its jump on, so
    // that the card it keeps stands still as the list moves under the bar.
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
                        songGroups = shownSongGroups,
                        columnCount = columnCount,
                        hasLoadedLibrary = hasLoadedLibrary,
                        contentPadding = listContentPadding,
                        appBarOverlap = appBarOverlap,
                        isSelecting = isSelecting,
                        selection = selection.orEmpty(),
                    )
                },
            ) {
                SearchableTopAppBar(
                    contentPadding = listContentPadding,
                    appBarReveal = appBarProgress,
                    placeholder = stringResource(Res.string.songs_search),
                    searchState = viewModel.songListState.songsSearch,
                    onReachChanged = { appBarReach = it },
                    areClosedSearchActionsShown = isSelecting || !isPerformanceModeEnabled && placeholder.allowsNewItemMenu,
                    // The search is where most of a selection is put together, so its menu stays while the search is open.
                    areClosedSearchActionsKeptInSearch = isSelecting,
                    areClosedSearchActionsDetached = !isSelecting,
                    closedSearchActions = {
                        AnimatedContent(
                            targetState = isSelecting,
                            // Unclipped, so that the New button's circle shrinks away whole rather than cut by the narrower
                            // menu, and scaled, so that it is out of the way of the menu fading in where it stood. Resized on the
                            // search's spring, which the bar moves the search action and the pill's end on as the two swap.
                            transitionSpec = {
                                fadeIn() + scaleIn(initialScale = SELECTION_MENU_SWAP_SCALE) togetherWith
                                    fadeOut(tween(SELECTION_MENU_FADE_OUT_MILLIS)) + scaleOut(targetScale = SELECTION_MENU_SWAP_SCALE) using
                                    SizeTransform(clip = false) { _, _ -> searchTravelSpec(visibilityThreshold = IntSize.VisibilityThreshold) }
                            },
                        ) { isShowingSelectionMenu ->
                            if (isShowingSelectionMenu) {
                                val userPreferences by viewModel.userPreferences.collectAsStateWithLifecycle()
                                val listedFileNames = remember(songGroups) { songGroups.groups.flatMap { group -> group.songs.map { it.fileName } } }
                                val ticked = selection.orEmpty()
                                SongSelectionMenu(
                                    isRoomy = layout.columnCount > 1,
                                    canSelectAll = listedFileNames.any { it !in ticked },
                                    hasSelection = ticked.isNotEmpty(),
                                    areActionsEnabled = ticked.isNotEmpty() && !isEditingSongs,
                                    areSetlistsEnabled = userPreferences?.areSetlistsEnabled != false,
                                    onSelectAll = { viewModel.songSelection.select(listedFileNames) },
                                    onDeselectAll = viewModel.songSelection::deselectAll,
                                    onChooseSetlists = viewModel::showSelectionSetlists,
                                    onManageTags = viewModel::showSelectionTags,
                                    onManageLanguages = viewModel::showSelectionLanguages,
                                    onExport = viewModel::exportSelection,
                                )
                            } else {
                                NewItemMenu(
                                    contentDescription = stringResource(Res.string.songs_new_song),
                                    createLabel = stringResource(Res.string.songs_create_song),
                                    onCreate = { viewModel.dialogHost.showDialog(DialogType.NewSong) },
                                    onImport = viewModel.importController::importFiles,
                                    onPaste = { viewModel.pastedSongController.newSongFromClipboard(clipboardReader) },
                                )
                            }
                        }
                    },
                    actions = {
                        // Read here rather than by the screen, so that a preference changing recomposes the bar's
                        // actions alone.
                        val userPreferences by viewModel.userPreferences.collectAsStateWithLifecycle()
                        SongSortMenu(
                            modifier = Modifier.overlappingAction(),
                            selected = userPreferences?.sortingMode,
                            onSelected = viewModel.preferencesController::setSortingMode,
                        )
                        // The last tag leaving the library takes the filters with it while the list is being looked at,
                        // but the first one arriving with the library is no change anybody made.
                        AnimatedVisibility(
                            modifier = Modifier.overlappingAction(),
                            visible = hasSongFilters && !isSidePanelVisible,
                            enter = if (hasLoadedLibrary) fadeIn() + expandHorizontally() else EnterTransition.None,
                        ) {
                            SongFiltersAction(
                                isSongFilterActive = isSongFilterActive,
                                badgeRecession = badgeRecession,
                                onClick = { viewModel.dialogHost.showDialog(DialogType.SongFilters) },
                            )
                        }
                    },
                )
            }
            ControlsSidePanel(
                isVisible = isSidePanelVisible,
                isAnimated = hasLoadedLibrary,
                contentPadding = contentPadding,
            ) { panelModifier, panelContentPadding ->
                SongFilters(
                    modifier = panelModifier,
                    state = rememberSongFilterUiState(viewModel),
                    actions = rememberSongFilterActions(viewModel),
                    normalizeForSearch = viewModel.songRenderer::normalizeForSearch,
                    contentPadding = panelContentPadding,
                    fadeBackgroundColor = NavigationBarDefaults.containerColor,
                )
            }
        }
        ImportProgress(isImporting = isImporting || isEditingSongs)
    }
    val backGesture = rememberNavigationEventState(currentInfo = NavigationEventInfo.None)
    NavigationBackHandler(
        state = backGesture,
        isBackEnabled = isSelecting && !isSearchOpen && visibleDialog == null && !LocalOverlayState.current.isAnyMenuOpen,
        onBackCompleted = viewModel.songSelection::end,
    )
}

/**
 * The action that opens the filters where there is no room for them beside the list. While a filter is on its funnel
 * is filled in and carries a badge in the accent color, since a filter hides songs for the moment rather than as a
 * standing preference, and a list that is shorter than the library with nothing on screen saying why reads as songs
 * having gone missing. The badge is not Material's default error red: nothing is wrong, the list is only narrowed.
 * The side panel needs no such mark, since the selected chips are in it. A back gesture that would reset the filters
 * fades the badge and the fill with the finger ([badgeRecession], see [songFilterBackRecession]).
 */
@Composable
private fun SongFiltersAction(
    modifier: Modifier = Modifier,
    isSongFilterActive: Boolean,
    badgeRecession: () -> Float,
    onClick: () -> Unit,
) = PillIconButton(
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
                Badge(
                    modifier = Modifier.graphicsLayer { alpha = 1f - badgeRecession() },
                    containerColor = MaterialTheme.colorScheme.primary,
                )
            }
        },
    ) {
        // The two funnels are faded by hand rather than by a Crossfade, so that a back gesture can take the fill away
        // with the finger and the fade a completed one starts carries on from wherever the gesture left it.
        val activeFill = animateFloatAsState(
            targetValue = if (isSongFilterActive) 1f else 0f,
            animationSpec = MaterialTheme.motionScheme.defaultEffectsSpec(),
        )
        val fill = { minOf(activeFill.value, 1f - badgeRecession()) }
        Box {
            Icon(
                modifier = Modifier.graphicsLayer { alpha = 1f - fill() },
                painter = painterResource(Res.drawable.ic_filter_outline),
                contentDescription = if (isSongFilterActive) null else stringResource(Res.string.songs_filter),
            )
            Icon(
                modifier = Modifier.graphicsLayer { alpha = fill() },
                painter = painterResource(Res.drawable.ic_filter),
                // The badge and the fill are drawn and nothing else, so a screen reader would otherwise never hear that
                // the list is narrowed.
                contentDescription = if (isSongFilterActive) stringResource(Res.string.songs_filter_active) else null,
            )
        }
    }
}

/** How small the New button and the selection menu are as they cross fade into each other. */
private const val SELECTION_MENU_SWAP_SCALE = 0.6f

/** How long the outgoing one of the two takes to fade, quicker than the pill reaches under it. */
private const val SELECTION_MENU_FADE_OUT_MILLIS = 120
