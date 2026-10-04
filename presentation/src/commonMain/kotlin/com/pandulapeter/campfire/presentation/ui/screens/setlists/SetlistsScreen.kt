/*
 * This file is part of Campfire.
 * Copyright (c) Pandula Péter 2017-2026.
 * https://github.com/pandulapeter/campfire
 *
 * This Source Code Form is subject to the terms of the Mozilla Public License, v. 2.0.
 * If a copy of the MPL was not distributed with this file, You can obtain one at
 * https://mozilla.org/MPL/2.0/.
 */
package com.pandulapeter.campfire.presentation.ui.screens.setlists

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.expandHorizontally
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.animation.shrinkHorizontally
import androidx.compose.animation.animateContentSize
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.calculateEndPadding
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.grid.GridItemSpan
import androidx.compose.foundation.lazy.grid.LazyGridState
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.itemsIndexed
import androidx.compose.material3.ExtendedFloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.platform.LocalWindowInfo
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.semantics.CustomAccessibilityAction
import androidx.compose.ui.semantics.customActions
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigationevent.NavigationEventInfo
import androidx.navigationevent.compose.NavigationBackHandler
import androidx.navigationevent.compose.rememberNavigationEventState
import com.pandulapeter.campfire.presentation.ui.components.isAnyOverflowMenuOpen
import com.pandulapeter.campfire.data.model.domain.Setlist
import com.pandulapeter.campfire.data.model.domain.UserPreferences
import com.pandulapeter.campfire.presentation.resources.Res
import com.pandulapeter.campfire.presentation.resources.ic_add
import com.pandulapeter.campfire.presentation.resources.ic_reorder_songs_done
import com.pandulapeter.campfire.presentation.resources.ic_archive
import com.pandulapeter.campfire.presentation.resources.ic_move_down
import com.pandulapeter.campfire.presentation.resources.ic_move_up
import com.pandulapeter.campfire.presentation.resources.ic_setlists_remove
import com.pandulapeter.campfire.presentation.resources.setlists_add_songs
import com.pandulapeter.campfire.presentation.resources.setlists_archived
import com.pandulapeter.campfire.presentation.resources.setlists_countdown_days_ago
import com.pandulapeter.campfire.presentation.resources.setlists_countdown_in_days
import com.pandulapeter.campfire.presentation.resources.setlists_countdown_today
import com.pandulapeter.campfire.presentation.resources.setlists_countdown_tomorrow
import com.pandulapeter.campfire.presentation.resources.setlists_countdown_yesterday
import com.pandulapeter.campfire.presentation.resources.setlists_create_setlist
import com.pandulapeter.campfire.presentation.resources.setlists_done_reordering
import com.pandulapeter.campfire.presentation.resources.setlists_move_down
import com.pandulapeter.campfire.presentation.resources.setlists_move_up
import com.pandulapeter.campfire.presentation.resources.setlists_new_setlist
import com.pandulapeter.campfire.presentation.resources.setlists_search
import com.pandulapeter.campfire.presentation.resources.setlists_show_archived
import com.pandulapeter.campfire.presentation.resources.setlists_remove_song
import com.pandulapeter.campfire.presentation.ui.CampfireViewModel
import com.pandulapeter.campfire.presentation.ui.navigation.CampfireDestination
import com.pandulapeter.campfire.presentation.ui.components.ActionListItem
import com.pandulapeter.campfire.presentation.ui.components.AppBarOverlap
import com.pandulapeter.campfire.presentation.ui.components.ActionsMenu
import com.pandulapeter.campfire.presentation.ui.components.ActionsMenuItem
import com.pandulapeter.campfire.presentation.ui.components.CheckboxListItem
import com.pandulapeter.campfire.presentation.ui.components.DragHandle
import com.pandulapeter.campfire.presentation.ui.components.FastScroller
import com.pandulapeter.campfire.presentation.ui.components.FAST_SCROLLER_WIDTH
import com.pandulapeter.campfire.presentation.ui.components.ListColumns
import com.pandulapeter.campfire.presentation.ui.components.ListPlaceholder
import com.pandulapeter.campfire.presentation.ui.components.ListUnderAppBar
import com.pandulapeter.campfire.presentation.ui.components.NewItemMenu
import com.pandulapeter.campfire.presentation.ui.components.HideKeyboardWhenScrolledDown
import com.pandulapeter.campfire.presentation.ui.components.ListLayout
import com.pandulapeter.campfire.presentation.ui.components.SHORT_WINDOW_HEIGHT
import com.pandulapeter.campfire.presentation.ui.components.ScrollToTopWhenChanged
import com.pandulapeter.campfire.presentation.ui.components.SearchableTopAppBar
import com.pandulapeter.campfire.presentation.ui.components.SectionHeader
import com.pandulapeter.campfire.presentation.ui.components.SectionHeaderState
import com.pandulapeter.campfire.presentation.ui.components.SetlistActions
import com.pandulapeter.campfire.presentation.ui.components.SetlistSortMenu
import com.pandulapeter.campfire.presentation.ui.components.MissingSongListItem
import com.pandulapeter.campfire.presentation.ui.components.SongListItem
import com.pandulapeter.campfire.presentation.ui.components.SongActions
import com.pandulapeter.campfire.presentation.ui.components.allowsNewItemMenu
import com.pandulapeter.campfire.presentation.ui.components.animateAppBarReveal
import com.pandulapeter.campfire.presentation.ui.components.belowAppBarOverlap
import com.pandulapeter.campfire.presentation.ui.components.draggedListItemContainerColor
import com.pandulapeter.campfire.presentation.ui.components.listItemAnimation
import com.pandulapeter.campfire.presentation.ui.components.rememberListTopFade
import com.pandulapeter.campfire.presentation.ui.components.listTopFadeViewport
import com.pandulapeter.campfire.presentation.ui.components.fadingUnderListTop
import com.pandulapeter.campfire.presentation.ui.components.only
import com.pandulapeter.campfire.presentation.ui.components.overlappingAction
import com.pandulapeter.campfire.presentation.ui.components.pushedSectionHeader
import com.pandulapeter.campfire.presentation.ui.components.pushedSectionHeaderPlacement
import com.pandulapeter.campfire.presentation.ui.components.RelativeDay
import com.pandulapeter.campfire.presentation.ui.components.relativeDay
import com.pandulapeter.campfire.presentation.ui.components.rememberToday
import com.pandulapeter.campfire.presentation.ui.components.rememberHasLoadedLibrary
import com.pandulapeter.campfire.presentation.ui.components.rememberRetainedLazyGridState
import com.pandulapeter.campfire.presentation.ui.components.rememberSectionHeaderState
import com.pandulapeter.campfire.presentation.ui.components.songCardPadding
import com.pandulapeter.campfire.presentation.ui.components.underAppBar
import com.pandulapeter.campfire.presentation.ui.platform.LocalFilePicker
import com.pandulapeter.campfire.presentation.localization.pluralStringResource
import com.pandulapeter.campfire.presentation.localization.stringResource
import kotlinx.coroutines.launch
import kotlinx.datetime.LocalDate
import org.jetbrains.compose.resources.painterResource
import sh.calvin.reorderable.ReorderableItem
import sh.calvin.reorderable.rememberReorderableLazyGridState
import com.pandulapeter.campfire.presentation.ui.platform.bounceScrollableContent

@Composable
internal fun SetlistsScreen(
    modifier: Modifier = Modifier,
    viewModel: CampfireViewModel,
    layout: ListLayout,
    contentPadding: PaddingValues,
) {
    val listState = rememberRetainedLazyGridState(viewModel.setlistsScrollPosition)
    val isPerformanceModeEnabled by viewModel.isPerformanceModeEnabled.collectAsStateWithLifecycle()
    val setlistsPlaceholder by viewModel.setlistsPlaceholder.collectAsStateWithLifecycle()
    val visibleDialog by viewModel.visibleDialog.collectAsStateWithLifecycle()
    val keyboardController = LocalSoftwareKeyboardController.current
    // Not saveable: returning to this screen always starts in browsing mode.
    val reorderingSetlistFileName = viewModel.reorderingSetlistFileName
    val isScreenActive = viewModel.backStack.lastOrNull() == CampfireDestination.Setlists
    val isReordering = reorderingSetlistFileName != null && isScreenActive && !isPerformanceModeEnabled
    val isSearchOpen by viewModel.setlistsSearch.isOpen.collectAsStateWithLifecycle()
    val userPreferences by viewModel.userPreferences.collectAsStateWithLifecycle()
    LaunchedEffect(isSearchOpen) {
        if (isSearchOpen) viewModel.reorderingSetlistFileName = null
    }
    LaunchedEffect(userPreferences?.setlistSortingMode) {
        viewModel.reorderingSetlistFileName = null
    }
    val columnCount = layout.columnCount
    HideKeyboardWhenScrolledDown(listState, isEnabled = visibleDialog == null)
    LaunchedEffect(viewModel, listState) {
        viewModel.scrollToTopRequests.collect { if (it == CampfireDestination.Setlists) listState.animateScrollToItem(0) }
    }
    // See the songs screen: how far the app bar's buttons reach in over a pinned header, which it narrows to clear.
    var appBarReach by remember { mutableStateOf(0.dp) }
    val appBarReveal = animateAppBarReveal(
        searchState = viewModel.setlistsSearch,
        isShownWithoutSearch = setlistsPlaceholder != null,
    )
    // Built lazily and read only while the lists lay out, so that the bar's spring moves the headers and the scroller
    // without recomposing the list on every one of its frames.
    val appBarOverlap: () -> AppBarOverlap = remember(appBarReveal) {
        { AppBarOverlap.of(reach = appBarReach, appBarReveal = appBarReveal.value) }
    }
    ListUnderAppBar(
        modifier = modifier.fillMaxSize(),
        list = {
            SetlistList(
                modifier = Modifier.fillMaxSize().underAppBar { appBarReveal.value },
                viewModel = viewModel,
                listState = listState,
                columnCount = columnCount,
                contentPadding = contentPadding,
                appBarOverlap = appBarOverlap,
                reorderingSetlistFileName = reorderingSetlistFileName,
                isReordering = isReordering,
                isScreenActive = isScreenActive,
                onReorderingSetlistChanged = {
                    viewModel.reorderingSetlistFileName = it
                    if (it != null) {
                        keyboardController?.hide()
                        viewModel.setlistsSearch.close()
                    }
                },
            )
        },
    ) {
        SearchableTopAppBar(
            contentPadding = contentPadding,
            appBarReveal = { appBarReveal.value },
            placeholder = stringResource(Res.string.setlists_search),
            searchState = viewModel.setlistsSearch,
            onReachChanged = { appBarReach = it },
            areClosedSearchActionsShown = !isPerformanceModeEnabled && setlistsPlaceholder.allowsNewItemMenu,
            closedSearchActions = {
                NewItemMenu(
                    viewModel = viewModel,
                    contentDescription = stringResource(Res.string.setlists_new_setlist),
                    createLabel = stringResource(Res.string.setlists_create_setlist),
                    onCreate = { viewModel.showDialog(CampfireViewModel.DialogType.NewSetlist) },
                    onItemSelected = { viewModel.reorderingSetlistFileName = null },
                )
            },
            actions = {
                SetlistSortMenu(
                    modifier = Modifier.overlappingAction(),
                    viewModel = viewModel,
                    onSortingModeChanged = { viewModel.reorderingSetlistFileName = null },
                )
            },
        )
    }
    val backGesture = rememberNavigationEventState(currentInfo = NavigationEventInfo.None)
    NavigationBackHandler(
        state = backGesture,
        isBackEnabled = isReordering && visibleDialog == null && !isAnyOverflowMenuOpen,
        onBackCompleted = { viewModel.reorderingSetlistFileName = null },
    )
}

@Composable
private fun SetlistList(
    modifier: Modifier = Modifier,
    viewModel: CampfireViewModel,
    listState: LazyGridState,
    columnCount: Int,
    contentPadding: PaddingValues,
    appBarOverlap: () -> AppBarOverlap,
    reorderingSetlistFileName: String?,
    isReordering: Boolean,
    isScreenActive: Boolean,
    onReorderingSetlistChanged: (String?) -> Unit,
) {
    val setlistsWithSongs by viewModel.setlistsWithSongs.collectAsStateWithLifecycle()
    val setlists by viewModel.setlists.collectAsStateWithLifecycle()
    val userPreferences by viewModel.userPreferences.collectAsStateWithLifecycle()
    val transpositions by viewModel.transpositions.collectAsStateWithLifecycle()
    val labelsOnEverySong by viewModel.labelsOnEverySong.collectAsStateWithLifecycle()
    val isLoading by viewModel.isLoading.collectAsStateWithLifecycle()
    val hasLoadedLibrary = rememberHasLoadedLibrary(isLoading)
    val topFade = rememberListTopFade(listState)
    // Remembered, since a new modifier every time the list recomposes would recompose the grid with it.
    val gridModifier = remember(topFade) { Modifier.fillMaxSize().listTopFadeViewport(topFade) }
    val isPerformanceModeEnabled by viewModel.isPerformanceModeEnabled.collectAsStateWithLifecycle()
    // Read by the headers alone, so midnight recomposes the ones counting down and not the list around them.
    val today by rememberToday()
    // Read once each, so that the branches below and the placeholders they render can never disagree about them.
    val setlistsPlaceholder = viewModel.setlistsPlaceholder.collectAsStateWithLifecycle().value
    LaunchedEffect(isScreenActive, isPerformanceModeEnabled, setlistsWithSongs) {
        if (!isScreenActive || isPerformanceModeEnabled ||
            setlistsWithSongs.none { it.setlist.fileName == reorderingSetlistFileName && it.entries.size > 1 }
        ) {
            onReorderingSetlistChanged(null)
        }
    }
    // Lyrics only mode takes the chords out of the viewer, and the key is the shortest way of writing them down.
    val shouldShowChords = userPreferences?.isLyricsOnlyModeEnabled != true
    val isCoverArtEnabled = userPreferences?.isCoverArtEnabled == true
    val chordSpelling = userPreferences?.chordSpelling ?: UserPreferences.ChordSpelling.Default
    // The order the rows are drawn in while a drag is in flight, before any of it has been written down. The
    // reorderable state has to see every move answered in the frame it reports it, and the library is several frames
    // away: a move that had to go to disk and come back through the repository left the row under the finger
    // snapping between where it was and where it had been put.
    var draggedSetlist by remember { mutableStateOf<DraggedSetlist?>(null) }
    // The setlist a drag was started in, known from the press that starts it rather than from its first move: the rows
    // of every other setlist stop being places to drop it for as long as it lasts. A move onto one of them is refused
    // below, but only after the reorderable state has locked itself for up to a second waiting for the list to answer
    // it, which froze the rows under the finger.
    var draggingSetlistFileName by remember { mutableStateOf<String?>(null) }
    val reorderableState = rememberReorderableLazyGridState(listState) { from, to ->
        val fromKey = SetlistItemKey(from.key as? String)
        val toKey = SetlistItemKey(to.key as? String)
        // Songs can only be reordered within their own setlist.
        if (fromKey.setlistFileName != null && fromKey.setlistFileName == toKey.setlistFileName && fromKey.songFileName != null && toKey.songFileName != null) {
            val songFileNames = draggedSetlist?.takeIf { it.setlistFileName == fromKey.setlistFileName }?.songFileNames
                ?: setlistsWithSongs.firstOrNull { it.setlist.fileName == fromKey.setlistFileName }?.entries?.map { it.songFileName }
            if (songFileNames != null) {
                draggedSetlist = DraggedSetlist(
                    setlistFileName = fromKey.setlistFileName,
                    songFileNames = songFileNames.toMutableList().apply {
                        val fromIndex = indexOf(fromKey.songFileName)
                        val toIndex = indexOf(toKey.songFileName)
                        if (fromIndex >= 0 && toIndex >= 0) add(toIndex, removeAt(fromIndex))
                    },
                )
            }
        }
    }
    // Written once the finger lifts rather than on every move, so that one drag is one write.
    val onDragStopped = {
        draggingSetlistFileName = null
        draggedSetlist?.let { viewModel.reorderSetlist(setlistFileName = it.setlistFileName, songFileNames = it.songFileNames) }
        Unit
    }
    // The drawn order is given up only once the library agrees with it, not the moment the finger lifts: the write
    // has to reach the disk and come back, and the rows would sit in their old order until it did. It is given up
    // just as readily when the setlist turns out to hold different songs than the drag was working from, which is
    // what a removal or a sync landing mid drag looks like.
    LaunchedEffect(setlistsWithSongs, draggedSetlist) {
        draggedSetlist?.let { dragged ->
            val songFileNames = setlistsWithSongs.firstOrNull { it.setlist.fileName == dragged.setlistFileName }?.entries?.map { it.songFileName }
            if (songFileNames == dragged.songFileNames || songFileNames?.toSet() != dragged.songFileNames.toSet()) {
                draggedSetlist = null
            }
        }
    }
    val filePicker = LocalFilePicker.current
    val coroutineScope = rememberCoroutineScope()
    val rowsCache = remember { SetlistRowsCache() }
    rowsCache.retainOnly(setlistsWithSongs)

    val isSearchOpen by viewModel.setlistsSearch.isOpen.collectAsStateWithLifecycle()
    val query = if (isSearchOpen) viewModel.setlistsSearch.textFieldState.text.toString() else ""
    val searchedQuery = remember(query) { viewModel.normalizeForSearch(query) }
    ScrollToTopWhenChanged(
        listState = listState,
        // A closed search narrows nothing whatever its field still holds, as on the songs screen, and the query is
        // the one searched for there too. Sorting sends the list to its new first setlist, but the archive toggle
        // lives at the end and must stay within reach there.
        key = "$searchedQuery|${userPreferences?.setlistSortingMode?.name}",
        contents = setlistsWithSongs,
        // Closing search to rearrange restores the full list around its visible item, not from the top.
        scrollToTopOnKeyChange = !isReordering,
    )

    // The header row reaches through the grid's end padding to the edge the app bar's reach is measured from, while
    // cards stop before the scroller's touch column.
    val headerEndPadding = FAST_SCROLLER_WIDTH + contentPadding.calculateEndPadding(LocalLayoutDirection.current)
    Box(modifier = modifier) {
        LazyVerticalGrid(
            columns = ListColumns(columnCount),
            modifier = gridModifier.bounceScrollableContent(listState),
            state = listState,
            contentPadding = contentPadding.only(start = true, end = true, bottom = true, extraEnd = FAST_SCROLLER_WIDTH, extraBottom = if (isReordering) 88.dp else 8.dp),
        ) {
            // The setlists are what this screen is about, and they are listed whenever there are any - an empty library
            // included, where they are simply empty and each offers to be filled. The library's own empty state belongs
            // to the songs screen. Every state goes through the same slot, so that "still loading" turning out to be
            // "you have no setlists" cross fades instead of being swapped in a single frame.
            val placeholder = setlistsPlaceholder
            when {
                placeholder != null -> item(
                    key = "placeholder",
                    span = { GridItemSpan(maxLineSpan) },
                    contentType = "placeholder",
                ) {
                    ListPlaceholder(
                        modifier = listItemAnimation(listState, hasLoadedLibrary).fillMaxWidth(),
                        placeholder = placeholder,
                        onRetry = viewModel::refresh,
                        onNewSetlist = if (isPerformanceModeEnabled) null else {
                            { viewModel.showDialog(CampfireViewModel.DialogType.NewSetlist) }
                        },
                        onImport = if (isPerformanceModeEnabled) null else {
                            { viewModel.importFiles(filePicker) }
                        },
                    )
                }

                else -> setlistsWithSongs.forEach { setlistWithSongs ->
                    stickyHeader(
                        key = "setlist_${setlistWithSongs.setlist.fileName}",
                        contentType = "setlist_header",
                    ) { headerIndex ->
                        val headerState = rememberSectionHeaderState(listState, headerIndex)
                        SectionHeader(
                            modifier = listItemAnimation(listState, hasLoadedLibrary),
                            state = { headerState.value },
                            endPadding = headerEndPadding,
                            text = setlistWithSongs.setlist.title,
                            subtitle = setlistWithSongs.setlist.countdownText(today),
                            // A setlist is only ever on this screen archived because the filter was asked to show them,
                            // so the mark is what tells it from the ones still in use.
                            icon = if (setlistWithSongs.setlist.isArchived) painterResource(Res.drawable.ic_archive) else null,
                            iconContentDescription = stringResource(Res.string.setlists_archived),
                            onClick = { coroutineScope.launch { listState.animateScrollToItem(headerIndex) } },
                            action = if (isPerformanceModeEnabled) null else {
                                { actionModifier, buttonModifier ->
                                    SetlistActions(
                                        modifier = actionModifier,
                                        buttonModifier = buttonModifier,
                                        viewModel = viewModel,
                                        setlist = setlistWithSongs.setlist,
                                        isReordering = isReordering && reorderingSetlistFileName == setlistWithSongs.setlist.fileName,
                                        onReorder = if (setlistWithSongs.entries.size > 1) {
                                            {
                                                onReorderingSetlistChanged(
                                                    if (reorderingSetlistFileName == setlistWithSongs.setlist.fileName) null
                                                    else setlistWithSongs.setlist.fileName,
                                                )
                                            }
                                        } else null,
                                    )
                                }
                            },
                            opacity = { if (headerState.value.visibleFraction < 1f) 0f else 1f },
                            appBarOverlap = appBarOverlap,
                        )
                    }
                    // Under the header: the row names the setlist, then this is the first thing to read about it.
                    if (setlistWithSongs.setlist.description.isNotBlank()) {
                        item(
                            key = "description_${setlistWithSongs.setlist.fileName}",
                            span = { GridItemSpan(maxLineSpan) },
                            contentType = "setlist_description",
                        ) {
                            // A description of four lines is most of what a short window shows, and with the keyboard
                            // of a search up it pushes the setlist's songs out of sight, so there it starts at two
                            // lines and opens on a tap. The keyboard counts, since that is when the room runs out.
                            var isExpanded by rememberSaveable { mutableStateOf(false) }
                            val isCompactHeight = LocalWindowInfo.current.containerDpSize.height - contentPadding.calculateBottomPadding() < SHORT_WINDOW_HEIGHT
                            Text(
                                modifier = listItemAnimation(listState, hasLoadedLibrary).fadingUnderListTop(topFade)
                                    .animateContentSize().clickable(enabled = isCompactHeight) { isExpanded = !isExpanded }
                                    .padding(horizontal = 24.dp, vertical = 8.dp),
                                maxLines = if (isCompactHeight && !isExpanded) 2 else Int.MAX_VALUE,
                                overflow = TextOverflow.Ellipsis,
                                text = setlistWithSongs.setlist.description,
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                    }
                    val setlistRows = rowsCache.rowsFor(setlistWithSongs, draggedSetlist)
                    val rows = setlistRows.rows
                    val songFileNames = setlistRows.songFileNames
                    itemsIndexed(
                        items = rows,
                        key = { _, row -> SetlistItemKey(setlistFileName = setlistWithSongs.setlist.fileName, songFileName = row.entry.songFileName).string.orEmpty() },
                        contentType = { _, _ -> "song" },
                    ) { rowIndex, row ->
                        val entry = row.entry
                        val key = SetlistItemKey(setlistFileName = setlistWithSongs.setlist.fileName, songFileName = entry.songFileName)
                        // Explicit move actions remain available while browsing; only drag gestures require the mode.
                        val canMove = !isPerformanceModeEnabled && setlistWithSongs.entries.size > 1
                        val isReorderable = canMove && isReordering && reorderingSetlistFileName == setlistWithSongs.setlist.fileName
                        // The drag written as two steps, for whoever cannot drag: a screen reader, a keyboard. Each is the
                        // same single write a finished drag makes, and a row with nowhere to go in a direction is offered
                        // no step that way.
                        val onMoveUp: (() -> Unit)? = if (canMove && rowIndex > 0) {
                            { viewModel.reorderSetlist(setlistFileName = setlistWithSongs.setlist.fileName, songFileNames = songFileNames.movedOnePlace(rowIndex, by = -1)) }
                        } else null
                        val onMoveDown: (() -> Unit)? = if (canMove && rowIndex < rows.lastIndex) {
                            { viewModel.reorderSetlist(setlistFileName = setlistWithSongs.setlist.fileName, songFileNames = songFileNames.movedOnePlace(rowIndex, by = 1)) }
                        } else null
                        val moveUpLabel = stringResource(Res.string.setlists_move_up)
                        val moveDownLabel = stringResource(Res.string.setlists_move_down)
                        // Merged so that a missing song's row, which has no clickable of its own, is still the one node a
                        // screen reader lands on; a present row is merged by its own click handling already.
                        val moveActions = Modifier.semantics(mergeDescendants = true) {
                            customActions = listOfNotNull(
                                onMoveUp?.let { CustomAccessibilityAction(moveUpLabel) { it(); true } },
                                onMoveDown?.let { CustomAccessibilityAction(moveDownLabel) { it(); true } },
                            )
                        }
                        val onDragStarted: (Offset) -> Unit = { draggingSetlistFileName = setlistWithSongs.setlist.fileName }
                        // The placement animation goes to ReorderableItem rather than onto the item itself, because it
                        // is what decides which rows may have one: the row under the finger is placed by the drag's own
                        // translation, and a placement animation on top of that animates it back towards the slot it is
                        // being dragged out of, which is the jumping. Every other row still slides into place.
                        ReorderableItem(
                            state = reorderableState,
                            key = key.string.orEmpty(),
                            enabled = draggingSetlistFileName.let { it == null || it == setlistWithSongs.setlist.fileName },
                            animateItemModifier = listItemAnimation(
                                listState = listState,
                                isEnabled = hasLoadedLibrary,
                                isRearranging = draggedSetlist != null,
                            ),
                        ) { isBeingDragged ->
                            val elevation by animateDpAsState(if (isBeingDragged) 8.dp else 0.dp)
                            val containerColor = draggedListItemContainerColor(isBeingDragged)
                            // Keep the overflow button available on touch platforms too, including while a
                            // long press belongs to reordering rather than the song's actions.
                            val actions: (@Composable () -> Unit)? = if (isPerformanceModeEnabled) {
                                null
                            } else {
                                {
                                    Row(
                                        verticalAlignment = Alignment.CenterVertically,
                                    ) {
                                        // The grip goes in front of the overflow button rather than after it, so that
                                        // the button lands exactly where the songs screen has its own.
                                        AnimatedVisibility(
                                            visible = isReorderable,
                                            enter = fadeIn() + expandHorizontally(),
                                            exit = fadeOut() + shrinkHorizontally(),
                                        ) {
                                            DragHandle(
                                                modifier = if (isReorderable) Modifier.draggableHandle(onDragStarted = onDragStarted, onDragStopped = onDragStopped)
                                                else Modifier,
                                            )
                                        }
                                        SetlistEntryActions(
                                            viewModel = viewModel,
                                            entry = entry,
                                            setlistFileName = setlistWithSongs.setlist.fileName,
                                            onMoveUp = onMoveUp,
                                            onMoveDown = onMoveDown,
                                        )
                                    }
                                }
                            }
                            when (entry) {
                                is CampfireViewModel.SetlistWithSongs.Entry.Present -> SongListItem(
                                    modifier = moveActions.fadingUnderListTop(topFade).longPressDraggableHandle(enabled = isReorderable, onDragStarted = onDragStarted, onDragStopped = onDragStopped),
                                    song = entry.song,
                                    index = row.index,
                                    cardPadding = songCardPadding(rowIndex, columnCount),
                                    // The setlist's own transposition of this song, which is why the same song
                                    // can be listed in one key here and in another one two setlists down.
                                    key = viewModel.renderKey(
                                        song = entry.song,
                                        transposition = transpositions[entry.song.fileName, setlistWithSongs.setlist.fileName],
                                        spelling = chordSpelling,
                                    ),
                                    shouldShowChords = shouldShowChords,
                                    duration = entry.song.duration,
                                    coverArtUrl = entry.song.coverArtUrl?.takeIf { isCoverArtEnabled },
                                    labelsOnEverySong = labelsOnEverySong,
                                    shouldShowLabels = false,
                                    containerColor = containerColor,
                                    shadowElevation = elevation,
                                    onClick = { viewModel.openSongInSetlist(setlistWithSongs, entry.song) },
                                    actions = actions,
                                )

                                // Nothing to open, but it still takes its place in the order and can be removed.
                                is CampfireViewModel.SetlistWithSongs.Entry.Missing -> MissingSongListItem(
                                    modifier = moveActions.fadingUnderListTop(topFade).longPressDraggableHandle(enabled = isReorderable, onDragStarted = onDragStarted, onDragStopped = onDragStopped),
                                    index = row.index,
                                    songFileName = entry.songFileName,
                                    cardPadding = songCardPadding(rowIndex, columnCount),
                                    containerColor = containerColor,
                                    shadowElevation = elevation,
                                    actions = actions,
                                )
                            }
                        }
                    }
                    // Every setlist ends in the way to more songs, which for an empty one is also all there is under its
                    // header, so the way to them is where the songs will be rather than behind the header's menu.
                    if (!isPerformanceModeEnabled) {
                        item(
                            key = "add_songs_${setlistWithSongs.setlist.fileName}",
                            span = { GridItemSpan(maxLineSpan) },
                            contentType = "setlist_action",
                        ) {
                            ActionListItem(
                                modifier = listItemAnimation(listState, hasLoadedLibrary).fadingUnderListTop(topFade),
                                title = stringResource(Res.string.setlists_add_songs),
                                icon = painterResource(Res.drawable.ic_add),
                                onClick = { viewModel.showDialog(CampfireViewModel.DialogType.SongPicker(setlistWithSongs.setlist)) },
                            )
                        }
                    }
                }
            }
            if (setlists.any { it.isArchived }) {
                item(
                    key = "show_archived_setlists",
                    span = { GridItemSpan(maxLineSpan) },
                    contentType = "setlist_archive_toggle",
                ) {
                    CheckboxListItem(
                        modifier = listItemAnimation(listState, hasLoadedLibrary).fadingUnderListTop(topFade).fillMaxWidth(),
                        title = stringResource(Res.string.setlists_show_archived),
                        isChecked = userPreferences?.shouldShowArchivedSetlists == true,
                        onCheckedChange = viewModel::setShouldShowArchivedSetlists,
                    )
                }
            }
        }
        // The visual copy can pass above the grid's clipped viewport, under the transparent app bar, as it fades away.
        PushedSetlistHeader(
            viewModel = viewModel,
            listState = listState,
            setlistsWithSongs = setlistsWithSongs,
            endPadding = headerEndPadding,
            isPerformanceModeEnabled = isPerformanceModeEnabled,
            reorderingSetlistFileName = reorderingSetlistFileName.takeIf { isReordering },
            appBarOverlap = appBarOverlap,
        )
        AnimatedVisibility(
            visible = isReordering,
            modifier = Modifier.align(Alignment.BottomEnd)
                .padding(contentPadding.only(end = true, bottom = true, extraEnd = FAST_SCROLLER_WIDTH))
                .padding(16.dp),
            enter = fadeIn() + scaleIn(),
            exit = fadeOut() + scaleOut(),
        ) {
            ExtendedFloatingActionButton(
                onClick = { onReorderingSetlistChanged(null) },
                icon = { Icon(painterResource(Res.drawable.ic_reorder_songs_done), contentDescription = null) },
                text = { Text(stringResource(Res.string.setlists_done_reordering)) },
            )
        }
        FastScroller(
            modifier = Modifier.align(Alignment.TopEnd).belowAppBarOverlap(appBarOverlap).padding(contentPadding.only(top = true, end = true, bottom = true)),
            gridState = listState,
        )
    }
}

/**
 * The outgoing setlist header, drawn over the grid while the next one pushes it up. A composable of its own for the
 * reason the songs screen's is: the push restarts nothing but it, and it only once per setlist.
 */
@Composable
private fun PushedSetlistHeader(
    viewModel: CampfireViewModel,
    listState: LazyGridState,
    setlistsWithSongs: List<CampfireViewModel.SetlistWithSongs>,
    endPadding: Dp,
    isPerformanceModeEnabled: Boolean,
    reorderingSetlistFileName: String?,
    appBarOverlap: () -> AppBarOverlap,
) {
    val pushed = pushedSectionHeader(listState, contentType = "setlist_header")
    val key by remember(pushed) { derivedStateOf { pushed.value?.key } }
    val setlistsByKey = remember(setlistsWithSongs) { setlistsWithSongs.associateBy { "setlist_${it.setlist.fileName}" } }
    val setlistWithSongs = key?.let { setlistsByKey[it] } ?: return
    val setlist = setlistWithSongs.setlist
    val today by rememberToday()
    SectionHeader(
        modifier = Modifier.pushedSectionHeaderPlacement(pushed).clearAndSetSemantics {},
        text = setlist.title,
        subtitle = setlist.countdownText(today),
        // Pinned for as long as it is being pushed away: it keeps the width it had in the bar's place rather than
        // widening again as it leaves.
        state = { SectionHeaderState(visibleFraction = pushed.value?.visibleFraction ?: 0f, pinnedFraction = 1f) },
        endPadding = endPadding,
        icon = if (setlist.isArchived) painterResource(Res.drawable.ic_archive) else null,
        onClick = null,
        action = if (isPerformanceModeEnabled) null else {
            { actionModifier, _ ->
                SetlistActions(
                    modifier = actionModifier,
                    viewModel = viewModel,
                    setlist = setlist,
                    isDecorative = true,
                    isReordering = reorderingSetlistFileName == setlist.fileName,
                    onReorder = if (setlistWithSongs.entries.size > 1) ({}) else null,
                )
            }
        },
        contentOpacity = { pushed.value?.visibleFraction ?: 0f },
        pushedDistancePx = { pushed.value?.pushedDistance ?: 0 },
        appBarOverlap = appBarOverlap,
    )
}

/**
 * One setlist's rows in the order they are drawn, which is the order a drag in flight has put them in rather than
 * the setlist's own. Each row takes the number of the slot it now sits in instead of the one it arrived with, so
 * that the rows a drag passes are renumbered as it passes them - and since the slots are the ones the visible rows
 * already occupied, the numbers do not change again when [CampfireViewModel.reorderSetlist] writes the same
 * dealing out to the file.
 */
private fun CampfireViewModel.SetlistWithSongs.rows(dragOrder: List<String>?): List<SetlistRow> {
    val songFileNames = dragOrder ?: return entries.map { SetlistRow(entry = it, index = it.index) }
    val entriesBySongFileName = entries.associateBy { it.songFileName }
    return songFileNames.mapIndexedNotNull { position, songFileName ->
        entriesBySongFileName[songFileName]?.let { entry ->
            SetlistRow(entry = entry, index = entries.getOrNull(position)?.index ?: entry.index)
        }
    }
}

/** Swap the row at [from] with its neighbor in the already checked direction. */
private fun List<String>.movedOnePlace(from: Int, by: Int): List<String> {
    val result = toMutableList()
    val to = from + by
    val neighbor = result[to]
    result[to] = result[from]
    result[from] = neighbor
    return result
}

/** Keeps unchanged setlists' row objects through each drag update. */
private class SetlistRowsCache {
    private val cached = mutableMapOf<String, CachedRows>()

    fun retainOnly(setlists: List<CampfireViewModel.SetlistWithSongs>) {
        cached.keys.retainAll(setlists.mapTo(mutableSetOf()) { it.setlist.fileName })
    }

    fun rowsFor(setlist: CampfireViewModel.SetlistWithSongs, draggedSetlist: DraggedSetlist?): SetlistRows {
        val dragOrder = draggedSetlist?.takeIf { it.setlistFileName == setlist.setlist.fileName }?.songFileNames
        val previous = cached[setlist.setlist.fileName]
        if (previous != null && previous.setlist === setlist && previous.dragOrder == dragOrder) return previous.rows

        val rows = setlist.rows(dragOrder)
        val result = SetlistRows(rows = rows, songFileNames = rows.map { it.entry.songFileName })
        cached[setlist.setlist.fileName] = CachedRows(setlist = setlist, dragOrder = dragOrder, rows = result)
        return result
    }

    private class CachedRows(
        val setlist: CampfireViewModel.SetlistWithSongs,
        val dragOrder: List<String>?,
        val rows: SetlistRows,
    )
}

private class SetlistRows(
    val rows: List<SetlistRow>,
    val songFileNames: List<String>,
)

/** One row of a setlist as it is drawn: the entry, and the place it sits in right now. */
private data class SetlistRow(
    val entry: CampfireViewModel.SetlistWithSongs.Entry,
    val index: Int,
)

/** The songs of one setlist in the order a drag has put them, which nothing outside this screen knows about yet. */
private data class DraggedSetlist(
    val setlistFileName: String,
    val songFileNames: List<String>,
)

/**
 * The overflow button of one row of a setlist and the actions behind it. A row whose file has gone missing has no
 * song to act on, so it is offered only what still applies to it - moving it and taking it out of the setlist -
 * rather than a list full of entries that would all fail.
 *
 * @param onMoveUp Null where the row cannot move that way, or cannot be moved at all.
 * @param onMoveDown Null where the row cannot move that way, or cannot be moved at all.
 */
@Composable
private fun SetlistEntryActions(
    viewModel: CampfireViewModel,
    entry: CampfireViewModel.SetlistWithSongs.Entry,
    setlistFileName: String,
    onMoveUp: (() -> Unit)?,
    onMoveDown: (() -> Unit)?,
) {
    val onRemove: () -> Unit = { viewModel.removeSongFromSetlist(songFileName = entry.songFileName, setlistFileName = setlistFileName) }
    when (entry) {
        is CampfireViewModel.SetlistWithSongs.Entry.Present -> SongActions(
            viewModel = viewModel,
            song = entry.song,
            isDeletable = false,
            setlistFileName = setlistFileName,
            leadingItems = setlistRowActions(onMoveUp, onMoveDown, onRemove),
        )

        is CampfireViewModel.SetlistWithSongs.Entry.Missing -> ActionsMenu(
            isExpandable = false,
            items = setlistRowActions(onMoveUp, onMoveDown, onRemove),
        )
    }
}

/**
 * The actions of a setlist row that are about the row rather than the song in it: the two steps it can be
 * moved by without a drag, for as far as it can go each way, and taking it out of the setlist. The last one carries
 * the setlists tab's list with a minus rather than the bin, since the song stays in the library and every other
 * setlist, and a menu that also offers Delete elsewhere in the app must not have the two read alike.
 */
@Composable
private fun setlistRowActions(
    onMoveUp: (() -> Unit)?,
    onMoveDown: (() -> Unit)?,
    onRemove: () -> Unit,
) = listOfNotNull(
    onMoveUp?.let { onClick ->
        ActionsMenuItem(
            title = stringResource(Res.string.setlists_move_up),
            icon = painterResource(Res.drawable.ic_move_up),
            onClick = onClick,
        )
    },
    onMoveDown?.let { onClick ->
        ActionsMenuItem(
            title = stringResource(Res.string.setlists_move_down),
            icon = painterResource(Res.drawable.ic_move_down),
            onClick = onClick,
        )
    },
    ActionsMenuItem(
        title = stringResource(Res.string.setlists_remove_song),
        icon = painterResource(Res.drawable.ic_setlists_remove),
        onClick = onRemove,
    ),
)

/**
 * The lazy list key of a song inside a setlist, encoded as a string so that the list can save it.
 */
/** The grid key of one song inside one setlist, since a song can appear in several of them. */
private class SetlistItemKey(val string: String?) {

    constructor(setlistFileName: String, songFileName: String) : this("$setlistFileName$TOKEN$songFileName")

    private val parts = string?.split(TOKEN)?.takeIf { it.size == 2 }

    val setlistFileName: String? = parts?.first()

    val songFileName: String? = parts?.last()

    companion object {
        // Contains characters no normalized name can hold, so it can never occur inside a setlist's file name.
        private const val TOKEN = "#*#"
    }
}

/**
 * How far the setlist's day is, for the subtitle of its header, where the user asked for it: the header is pinned while
 * its songs are read, so the day stays in sight in performance mode too, where nothing else on the screen shows it.
 */
@Composable
private fun Setlist.countdownText(today: LocalDate): String? {
    val date = date?.takeIf { isCountdownShown } ?: return null
    return when (val day = relativeDay(date = date, today = today)) {
        RelativeDay.Today -> stringResource(Res.string.setlists_countdown_today)
        RelativeDay.Tomorrow -> stringResource(Res.string.setlists_countdown_tomorrow)
        RelativeDay.Yesterday -> stringResource(Res.string.setlists_countdown_yesterday)
        is RelativeDay.InDays -> pluralStringResource(Res.plurals.setlists_countdown_in_days, day.days, day.days)
        is RelativeDay.DaysAgo -> pluralStringResource(Res.plurals.setlists_countdown_days_ago, day.days, day.days)
    }
}
