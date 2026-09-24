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
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.calculateEndPadding
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.grid.GridItemSpan
import androidx.compose.foundation.lazy.grid.LazyGridState
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.itemsIndexed
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.semantics.CustomAccessibilityAction
import androidx.compose.ui.semantics.customActions
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.pandulapeter.campfire.data.model.domain.UserPreferences
import com.pandulapeter.campfire.presentation.resources.Res
import com.pandulapeter.campfire.presentation.resources.ic_add
import com.pandulapeter.campfire.presentation.resources.ic_archive
import com.pandulapeter.campfire.presentation.resources.ic_move_down
import com.pandulapeter.campfire.presentation.resources.ic_move_up
import com.pandulapeter.campfire.presentation.resources.ic_more
import com.pandulapeter.campfire.presentation.resources.ic_setlists_remove
import com.pandulapeter.campfire.presentation.resources.ic_tune
import com.pandulapeter.campfire.presentation.resources.setlists_add_songs
import com.pandulapeter.campfire.presentation.resources.setlists_archived
import com.pandulapeter.campfire.presentation.resources.setlists_create_setlist
import com.pandulapeter.campfire.presentation.resources.setlists_move_down
import com.pandulapeter.campfire.presentation.resources.setlists_move_up
import com.pandulapeter.campfire.presentation.resources.setlists_new_setlist
import com.pandulapeter.campfire.presentation.resources.setlists_search
import com.pandulapeter.campfire.presentation.resources.setlists_sort_and_filter
import com.pandulapeter.campfire.presentation.resources.setlists_remove_song
import com.pandulapeter.campfire.presentation.ui.CampfireViewModel
import com.pandulapeter.campfire.presentation.ui.components.ActionListItem
import com.pandulapeter.campfire.presentation.ui.components.AppBarOverlap
import com.pandulapeter.campfire.presentation.ui.components.ActionsMenu
import com.pandulapeter.campfire.presentation.ui.components.ActionsMenuItem
import com.pandulapeter.campfire.presentation.ui.components.ControlsSidePanel
import com.pandulapeter.campfire.presentation.ui.components.DismissSheetWhenSidePanelAppears
import com.pandulapeter.campfire.presentation.ui.components.DragHandle
import com.pandulapeter.campfire.presentation.ui.components.FastScroller
import com.pandulapeter.campfire.presentation.ui.components.FAST_SCROLLER_WIDTH
import com.pandulapeter.campfire.presentation.ui.components.ListColumns
import com.pandulapeter.campfire.presentation.ui.components.ListPlaceholder
import com.pandulapeter.campfire.presentation.ui.components.NewItemMenu
import com.pandulapeter.campfire.presentation.ui.components.HideKeyboardWhenScrolledDown
import com.pandulapeter.campfire.presentation.ui.components.ScrollToTopWhenChanged
import com.pandulapeter.campfire.presentation.ui.components.SearchableTopAppBar
import com.pandulapeter.campfire.presentation.ui.components.SectionHeader
import com.pandulapeter.campfire.presentation.ui.components.SectionHeaderState
import com.pandulapeter.campfire.presentation.ui.components.SetlistActionsMenu
import com.pandulapeter.campfire.presentation.ui.components.SetlistsControls
import com.pandulapeter.campfire.presentation.ui.components.MissingSongListItem
import com.pandulapeter.campfire.presentation.ui.components.SongListItem
import com.pandulapeter.campfire.presentation.ui.components.SongActionsButton
import com.pandulapeter.campfire.presentation.ui.components.allowsNewItemMenu
import com.pandulapeter.campfire.presentation.ui.components.animateAppBarReveal
import com.pandulapeter.campfire.presentation.ui.components.besideSidePanel
import com.pandulapeter.campfire.presentation.ui.components.draggedListItemContainerColor
import com.pandulapeter.campfire.presentation.ui.components.hasRoomForSidePanel
import com.pandulapeter.campfire.presentation.ui.components.listItemAnimation
import com.pandulapeter.campfire.presentation.ui.components.rememberListTopFade
import com.pandulapeter.campfire.presentation.ui.components.listTopFadeViewport
import com.pandulapeter.campfire.presentation.ui.components.fadingUnderListTop
import com.pandulapeter.campfire.presentation.ui.components.only
import com.pandulapeter.campfire.presentation.ui.components.pushedSectionHeader
import com.pandulapeter.campfire.presentation.ui.components.rememberHasLoadedLibrary
import com.pandulapeter.campfire.presentation.ui.components.rememberRetainedLazyGridState
import com.pandulapeter.campfire.presentation.ui.components.sectionHeaderState
import com.pandulapeter.campfire.presentation.ui.components.songCardPadding
import com.pandulapeter.campfire.presentation.ui.components.songListColumnCount
import com.pandulapeter.campfire.presentation.ui.components.underAppBar
import com.pandulapeter.campfire.presentation.ui.platform.LocalFilePicker
import com.pandulapeter.campfire.presentation.localization.stringResource
import kotlinx.coroutines.launch
import org.jetbrains.compose.resources.painterResource
import sh.calvin.reorderable.ReorderableItem
import sh.calvin.reorderable.rememberReorderableLazyGridState

@Composable
internal fun SetlistsScreen(
    modifier: Modifier = Modifier,
    viewModel: CampfireViewModel,
    settledWidth: Dp,
    contentPadding: PaddingValues,
) {
    val listState = rememberRetainedLazyGridState(viewModel.setlistsScrollPosition)
    val isPerformanceModeEnabled by viewModel.isPerformanceModeEnabled.collectAsStateWithLifecycle()
    val setlistsPlaceholder by viewModel.setlistsPlaceholder.collectAsStateWithLifecycle()
    val visibleDialog by viewModel.visibleDialog.collectAsStateWithLifecycle()
    val isSidePanelVisible = hasRoomForSidePanel(settledWidth)
    val columnCount = songListColumnCount(
        settledWidth = settledWidth,
        contentPadding = contentPadding,
        isSidePanelVisible = isSidePanelVisible,
    )
    HideKeyboardWhenScrolledDown(listState)
    DismissSheetWhenSidePanelAppears(
        isSidePanelVisible = isSidePanelVisible,
        isSheetVisible = visibleDialog == CampfireViewModel.DialogType.SetlistsControls,
        onDismiss = viewModel::dismissDialog,
    )
    // See the songs screen: how far the app bar's buttons reach in over a pinned header, which it narrows to clear.
    var appBarReach by remember { mutableStateOf(0.dp) }
    val appBarReveal = animateAppBarReveal(
        searchState = viewModel.setlistsSearch,
        isShownWithoutSearch = setlistsPlaceholder != null,
    )
    val listContentPadding = contentPadding.besideSidePanel(isSidePanelVisible)
    Box(modifier = modifier.fillMaxSize()) {
        Row {
            // The bar spans the list alone rather than the whole screen, so that its buttons and the search stay at the
            // top of the list they act on instead of standing over the side panel beside it.
            Box(modifier = Modifier.weight(1f).fillMaxHeight()) {
                SetlistList(
                    modifier = Modifier.fillMaxSize().underAppBar { appBarReveal.value },
                    viewModel = viewModel,
                    listState = listState,
                    columnCount = columnCount,
                    contentPadding = listContentPadding,
                    appBarOverlap = AppBarOverlap.of(reach = appBarReach, appBarReveal = appBarReveal.value),
                )
                SearchableTopAppBar(
                    contentPadding = listContentPadding,
                    appBarReveal = { appBarReveal.value },
                    placeholder = stringResource(Res.string.setlists_search),
                    searchState = viewModel.setlistsSearch,
                    onReachChanged = { appBarReach = it },
                    closedSearchActions = {
                        // The setlists being read and performance mode being switched both happen while the bar is being
                        // looked at, so the button makes room for itself rather than appearing between two frames, as on
                        // the songs screen.
                        AnimatedVisibility(visible = !isPerformanceModeEnabled && setlistsPlaceholder.allowsNewItemMenu) {
                            NewItemMenu(
                                viewModel = viewModel,
                                contentDescription = stringResource(Res.string.setlists_new_setlist),
                                createLabel = stringResource(Res.string.setlists_create_setlist),
                                onCreate = { viewModel.showDialog(CampfireViewModel.DialogType.NewSetlist) },
                            )
                        }
                    },
                    actions = {
                        if (!isSidePanelVisible) {
                            IconButton(onClick = { viewModel.showDialog(CampfireViewModel.DialogType.SetlistsControls) }) {
                                Icon(
                                    painter = painterResource(Res.drawable.ic_tune),
                                    contentDescription = stringResource(Res.string.setlists_sort_and_filter),
                                )
                            }
                        }
                    },
                )
            }
            ControlsSidePanel(
                isVisible = isSidePanelVisible,
                contentPadding = contentPadding,
            ) { panelModifier, panelContentPadding ->
                SetlistsControls(
                    modifier = panelModifier,
                    viewModel = viewModel,
                    contentPadding = panelContentPadding,
                )
            }
        }
    }
}

@Composable
private fun SetlistList(
    modifier: Modifier = Modifier,
    viewModel: CampfireViewModel,
    listState: LazyGridState,
    columnCount: Int,
    contentPadding: PaddingValues,
    appBarOverlap: AppBarOverlap,
) {
    val setlistsWithSongs by viewModel.setlistsWithSongs.collectAsStateWithLifecycle()
    val userPreferences by viewModel.userPreferences.collectAsStateWithLifecycle()
    val transpositions by viewModel.transpositions.collectAsStateWithLifecycle()
    val labelsOnEverySong by viewModel.labelsOnEverySong.collectAsStateWithLifecycle()
    val isLoading by viewModel.isLoading.collectAsStateWithLifecycle()
    val hasLoadedLibrary = rememberHasLoadedLibrary(isLoading)
    val pushedHeader = pushedSectionHeader(listState, contentType = "setlist_header")
    val topFade = rememberListTopFade(listState)
    val isPerformanceModeEnabled by viewModel.isPerformanceModeEnabled.collectAsStateWithLifecycle()
    // Read once each, so that the branches below and the placeholders they render can never disagree about them.
    val setlistsPlaceholder = viewModel.setlistsPlaceholder.collectAsStateWithLifecycle().value
    // Lyrics only mode takes the chords out of the viewer, and the key is the shortest way of writing them down.
    val shouldShowChords = userPreferences?.isLyricsOnlyModeEnabled != true
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

    val isSearchOpen by viewModel.setlistsSearch.isOpen.collectAsStateWithLifecycle()
    ScrollToTopWhenChanged(
        listState = listState,
        // A closed search narrows nothing whatever its field still holds, as on the songs screen. The order and the
        // archived setlists are the rest of what the screen's controls change about the list.
        key = "${if (isSearchOpen) viewModel.setlistsSearch.textFieldState.text.toString() else ""}|${userPreferences?.setlistSortingMode?.name}|${userPreferences?.shouldShowArchivedSetlists}",
        contents = setlistsWithSongs,
    )

    // The header row reaches through the grid's end padding to the edge the app bar's reach is measured from, while
    // cards stop before the scroller's touch column.
    val headerEndPadding = FAST_SCROLLER_WIDTH + contentPadding.calculateEndPadding(LocalLayoutDirection.current)
    Box(modifier = modifier) {
        LazyVerticalGrid(
            columns = ListColumns(columnCount),
            modifier = Modifier.fillMaxSize().listTopFadeViewport(topFade),
            state = listState,
            contentPadding = contentPadding.only(start = true, end = true, bottom = true, extraEnd = FAST_SCROLLER_WIDTH, extraBottom = 8.dp),
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
                        val headerState = sectionHeaderState(listState, headerIndex)
                        SectionHeader(
                            modifier = listItemAnimation(listState, hasLoadedLibrary),
                            state = headerState,
                            endPadding = headerEndPadding,
                            text = setlistWithSongs.setlist.title,
                            // A setlist is only ever on this screen archived because the filter was asked to show them,
                            // so the mark is what tells it from the ones still in use.
                            icon = if (setlistWithSongs.setlist.isArchived) painterResource(Res.drawable.ic_archive) else null,
                            iconContentDescription = stringResource(Res.string.setlists_archived),
                            onClick = { coroutineScope.launch { listState.animateScrollToItem(headerIndex) } },
                            action = if (isPerformanceModeEnabled) null else {
                                { actionModifier ->
                                    SetlistActionsMenu(
                                        modifier = actionModifier,
                                        viewModel = viewModel,
                                        setlist = setlistWithSongs.setlist,
                                    )
                                }
                            },
                            opacity = if (headerState.visibleFraction < 1f) 0f else 1f,
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
                            Text(
                                modifier = listItemAnimation(listState, hasLoadedLibrary).fadingUnderListTop(topFade).padding(horizontal = 24.dp, vertical = 8.dp),
                                text = setlistWithSongs.setlist.description,
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                    }
                    // An empty setlist is one that is waiting for its songs, so the way to them takes the place the songs
                    // will take, rather than staying behind the header's menu.
                    if (setlistWithSongs.entries.isEmpty() && !isPerformanceModeEnabled) {
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
                    val rows = setlistWithSongs.rows(draggedSetlist)
                    itemsIndexed(
                        items = rows,
                        key = { _, row -> SetlistItemKey(setlistFileName = setlistWithSongs.setlist.fileName, songFileName = row.entry.songFileName).string.orEmpty() },
                        contentType = { _, _ -> "song" },
                    ) { rowIndex, row ->
                        val entry = row.entry
                        val key = SetlistItemKey(setlistFileName = setlistWithSongs.setlist.fileName, songFileName = entry.songFileName)
                        // A setlist of one song has no order to change, so its row offers no grip and no long press to
                        // drag it by: a handle that can only put the row back where it was promises something it cannot do.
                        val isReorderable = !isPerformanceModeEnabled && setlistWithSongs.entries.size > 1
                        // The drag written as two steps, for whoever cannot drag: a screen reader, a keyboard. Each is the
                        // same single write a finished drag makes, and a row with nowhere to go in a direction is offered
                        // no step that way.
                        val songFileNames = rows.map { it.entry.songFileName }
                        val onMoveUp: (() -> Unit)? = songFileNames.movedOnePlace(entry.songFileName, by = -1)?.takeIf { isReorderable }?.let { order ->
                            { viewModel.reorderSetlist(setlistFileName = setlistWithSongs.setlist.fileName, songFileNames = order) }
                        }
                        val onMoveDown: (() -> Unit)? = songFileNames.movedOnePlace(entry.songFileName, by = 1)?.takeIf { isReorderable }?.let { order ->
                            { viewModel.reorderSetlist(setlistFileName = setlistWithSongs.setlist.fileName, songFileNames = order) }
                        }
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
                            // The long press that reorders a row is the gesture the drag handle is there to
                            // advertise, which leaves a touch platform no long press for the actions a song
                            // list usually hides behind one - so the overflow button is shown on every
                            // platform here rather than on the pointer driven ones alone.
                            val actions: (@Composable () -> Unit)? = if (isPerformanceModeEnabled) {
                                null
                            } else {
                                {
                                    Row(
                                        verticalAlignment = Alignment.CenterVertically,
                                    ) {
                                        // The grip goes in front of the overflow button rather than after it, so that
                                        // the button lands exactly where the songs screen has its own.
                                        if (isReorderable) {
                                            DragHandle(modifier = Modifier.draggableHandle(onDragStarted = onDragStarted, onDragStopped = onDragStopped))
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
                                    labelsOnEverySong = labelsOnEverySong,
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
                }
            }
        }
        // The visual copy can pass above the grid's clipped viewport, under the transparent app bar, as it fades away.
        pushedHeader?.let { pushed ->
            val setlist = setlistsWithSongs.firstOrNull { "setlist_${it.setlist.fileName}" == pushed.key }?.setlist
            if (setlist != null) {
                val density = LocalDensity.current
                SectionHeader(
                    modifier = Modifier.offset { pushed.offset }.width(with(density) { pushed.width.toDp() }).clearAndSetSemantics {},
                    text = setlist.title,
                    // Pinned for as long as it is being pushed away: it keeps the width it had in the bar's place rather than
                    // widening again as it leaves.
                    state = SectionHeaderState(visibleFraction = pushed.visibleFraction, pinnedFraction = 1f),
                    endPadding = headerEndPadding,
                    icon = if (setlist.isArchived) painterResource(Res.drawable.ic_archive) else null,
                    onClick = null,
                    actionIcon = if (isPerformanceModeEnabled) null else painterResource(Res.drawable.ic_more),
                    contentOpacity = pushed.visibleFraction,
                    pushedDistancePx = pushed.pushedDistance,
                    appBarOverlap = appBarOverlap,
                )
            }
        }
        FastScroller(
            modifier = Modifier.align(Alignment.TopEnd).padding(top = appBarOverlap.height).padding(contentPadding.only(top = true, end = true, bottom = true)),
            gridState = listState,
        )
    }
}

/**
 * One setlist's rows in the order they are drawn, which is the order a drag in flight has put them in rather than
 * the setlist's own. Each row takes the number of the slot it now sits in instead of the one it arrived with, so
 * that the rows a drag passes are renumbered as it passes them - and since the slots are the ones the visible rows
 * already occupied, the numbers do not change again when [CampfireViewModel.reorderSetlist] writes the same
 * dealing out to the file.
 */
private fun CampfireViewModel.SetlistWithSongs.rows(draggedSetlist: DraggedSetlist?): List<SetlistRow> {
    val songFileNames = draggedSetlist?.takeIf { it.setlistFileName == setlist.fileName }?.songFileNames
        ?: return entries.map { SetlistRow(entry = it, index = it.index) }
    val entriesBySongFileName = entries.associateBy { it.songFileName }
    return songFileNames.mapIndexedNotNull { position, songFileName ->
        entriesBySongFileName[songFileName]?.let { entry ->
            SetlistRow(entry = entry, index = entries.getOrNull(position)?.index ?: entry.index)
        }
    }
}

/** This order with [songFileName] one place further along it ([by] = 1) or back ([by] = -1), or null where it has no room to go. */
private fun List<String>.movedOnePlace(songFileName: String, by: Int): List<String>? {
    val from = indexOf(songFileName)
    val to = from + by
    return if (from < 0 || to !in indices) null else toMutableList().apply { add(to, removeAt(from)) }
}

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
 * song to act on, so it is offered the only thing that still applies to it - being taken out of the setlist -
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
) = when (entry) {
    // Nothing is locked: the sheet's box for this very setlist is what unticks the song out of it, which is the one
    // way a song is taken out of a setlist.
    is CampfireViewModel.SetlistWithSongs.Entry.Present -> SongActionsButton(
        viewModel = viewModel,
        song = entry.song,
        lockedSetlistFileName = null,
        leadingItems = { select -> MoveMenuItems(select, onMoveUp, onMoveDown) },
    )

    is CampfireViewModel.SetlistWithSongs.Entry.Missing -> ActionsMenu { select ->
        MoveMenuItems(select, onMoveUp, onMoveDown)
        ActionsMenuItem(
            title = stringResource(Res.string.setlists_remove_song),
            icon = painterResource(Res.drawable.ic_setlists_remove),
            onClick = {
                select {
                    viewModel.removeSongFromSetlist(songFileName = entry.songFileName, setlistFileName = setlistFileName)
                }
            },
        )
    }
}

/** The two steps a row of a setlist can be moved by without a drag, for as far as it can go each way. */
@Composable
private fun MoveMenuItems(
    select: (action: () -> Unit) -> Unit,
    onMoveUp: (() -> Unit)?,
    onMoveDown: (() -> Unit)?,
) {
    onMoveUp?.let { onClick ->
        ActionsMenuItem(
            title = stringResource(Res.string.setlists_move_up),
            icon = painterResource(Res.drawable.ic_move_up),
            onClick = { select(onClick) },
        )
    }
    onMoveDown?.let { onClick ->
        ActionsMenuItem(
            title = stringResource(Res.string.setlists_move_down),
            icon = painterResource(Res.drawable.ic_move_down),
            onClick = { select(onClick) },
        )
    }
}

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
