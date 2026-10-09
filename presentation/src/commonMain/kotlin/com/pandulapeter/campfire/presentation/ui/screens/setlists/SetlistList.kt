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
import androidx.compose.animation.core.MutableTransitionState
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
import androidx.compose.runtime.snapshotFlow
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.platform.LocalWindowInfo
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.semantics.CustomAccessibilityAction
import androidx.compose.ui.semantics.customActions
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.pandulapeter.campfire.data.model.domain.UserPreferences
import com.pandulapeter.campfire.presentation.resources.Res
import com.pandulapeter.campfire.presentation.resources.ic_add
import com.pandulapeter.campfire.presentation.resources.ic_reorder_songs_done
import com.pandulapeter.campfire.presentation.resources.ic_archive
import com.pandulapeter.campfire.presentation.resources.setlists_archived
import com.pandulapeter.campfire.presentation.resources.setlists_choose_songs
import com.pandulapeter.campfire.presentation.resources.setlists_done_reordering
import com.pandulapeter.campfire.presentation.resources.setlists_move_down
import com.pandulapeter.campfire.presentation.resources.setlists_move_up
import com.pandulapeter.campfire.presentation.resources.setlists_show_archived
import com.pandulapeter.campfire.presentation.ui.CampfireViewModel
import com.pandulapeter.campfire.presentation.ui.components.ActionListItem
import com.pandulapeter.campfire.presentation.ui.components.AppBarOverlap
import com.pandulapeter.campfire.presentation.ui.components.CheckboxListItem
import com.pandulapeter.campfire.presentation.ui.components.DragHandle
import com.pandulapeter.campfire.presentation.ui.components.FastScroller
import com.pandulapeter.campfire.presentation.ui.components.FAST_SCROLLER_WIDTH
import com.pandulapeter.campfire.presentation.ui.components.ListColumns
import com.pandulapeter.campfire.presentation.ui.components.ListPlaceholder
import com.pandulapeter.campfire.presentation.ui.components.SHORT_WINDOW_HEIGHT
import com.pandulapeter.campfire.presentation.ui.components.SONG_CARD_VERTICAL_PADDING
import com.pandulapeter.campfire.presentation.ui.components.ScrollToTopWhenChanged
import com.pandulapeter.campfire.presentation.ui.components.SectionHeader
import com.pandulapeter.campfire.presentation.ui.components.SetlistActions
import com.pandulapeter.campfire.presentation.ui.components.MissingSongListItem
import com.pandulapeter.campfire.presentation.ui.components.SongListItem
import com.pandulapeter.campfire.presentation.ui.components.belowAppBarOverlap
import com.pandulapeter.campfire.presentation.ui.components.draggedListItemContainerColor
import com.pandulapeter.campfire.presentation.ui.components.songCardTextKeyline
import com.pandulapeter.campfire.presentation.ui.components.listItemAnimation
import com.pandulapeter.campfire.presentation.ui.components.rememberListTopFade
import com.pandulapeter.campfire.presentation.ui.components.rememberOverflowMenuState
import com.pandulapeter.campfire.presentation.ui.components.listTopFadeViewport
import com.pandulapeter.campfire.presentation.ui.components.fadingUnderListTop
import com.pandulapeter.campfire.presentation.ui.components.only
import com.pandulapeter.campfire.presentation.ui.components.rememberToday
import com.pandulapeter.campfire.presentation.ui.components.rememberHasLoadedLibrary
import com.pandulapeter.campfire.presentation.ui.components.rememberSectionHeaderState
import com.pandulapeter.campfire.presentation.ui.components.songCardPadding
import com.pandulapeter.campfire.presentation.ui.components.sectionHeaderBottomGap
import com.pandulapeter.campfire.presentation.ui.dialogs.DialogType
import com.pandulapeter.campfire.presentation.ui.platform.LocalFilePicker
import com.pandulapeter.campfire.presentation.ui.platform.isDesktopPlatform
import com.pandulapeter.campfire.presentation.localization.stringResource
import com.pandulapeter.campfire.presentation.ui.playing.songPlaybackOf
import com.pandulapeter.campfire.presentation.ui.screens.rememberSetlistActionHandler
import com.pandulapeter.campfire.presentation.ui.screens.rememberSongActionHandler
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import org.jetbrains.compose.resources.painterResource
import sh.calvin.reorderable.ReorderableCollectionItemScope
import sh.calvin.reorderable.ReorderableItem
import sh.calvin.reorderable.rememberReorderableLazyGridState
import com.pandulapeter.campfire.presentation.ui.platform.bounceScrollableContent

@Composable
internal fun SetlistList(
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
    val songActions = rememberSongActionHandler(viewModel)
    val setlistActions = rememberSetlistActionHandler(viewModel)
    val setlistsWithSongs by viewModel.setlistsWithSongs.collectAsStateWithLifecycle()
    // Keep validation and writes tied to the full library; only the rendered list is narrowed by this mode.
    var narrowedSetlistFileName by remember { mutableStateOf<String?>(null) }
    val shownSetlists = remember(setlistsWithSongs, narrowedSetlistFileName) {
        if (narrowedSetlistFileName != null) setlistsWithSongs.filter { it.setlist.fileName == narrowedSetlistFileName }
        else setlistsWithSongs
    }
    // Room after the end of the list for the setlist being brought to the top, held only until the other setlists
    // have left: a setlist near the end has too little of the list after it to be scrolled up there otherwise.
    var bringToTopPadding by remember { mutableStateOf(0.dp) }
    val density = LocalDensity.current
    LaunchedEffect(isReordering, reorderingSetlistFileName) {
        if (!isReordering) {
            narrowedSetlistFileName = null
            bringToTopPadding = 0.dp
            return@LaunchedEffect
        }
        // The other setlists only leave once the setlist is the one the list starts at, already or after scrolling it
        // there: the grid keeps its first visible item by key as they go, so nothing on screen moves. Narrowed
        // anywhere else, the grid would lose that item and fall back to its index somewhere inside the setlist.
        fun isSetlistAtTop(): Boolean {
            val key = listState.layoutInfo.visibleItemsInfo.firstOrNull { it.index == listState.firstVisibleItemIndex }?.key
            return key == "setlist_$reorderingSetlistFileName" ||
                key == "description_$reorderingSetlistFileName" ||
                key == "choose_songs_$reorderingSetlistFileName" ||
                SetlistItemKey(key as? String).setlistFileName == reorderingSetlistFileName
        }
        if (!isSetlistAtTop()) {
            val headerIndex = setlistsWithSongs.headerIndexOf(reorderingSetlistFileName)
            if (headerIndex != null) {
                val viewportHeight = listState.layoutInfo.viewportSize.height
                bringToTopPadding = with(density) { viewportHeight.toDp() }
                snapshotFlow { listState.layoutInfo.afterContentPadding }.first { it >= viewportHeight }
                try {
                    listState.animateScrollToItem(headerIndex)
                } catch (exception: CancellationException) {
                    // A scroll of the user's own interrupts the animation, and the mode still narrows the list after
                    // it; only the mode itself ending cancels the rest.
                    currentCoroutineContext().ensureActive()
                }
            }
        }
        val isSetlistAtTop = isSetlistAtTop()
        narrowedSetlistFileName = reorderingSetlistFileName
        bringToTopPadding = 0.dp
        if (!isSetlistAtTop) listState.requestScrollToItem(0)
    }
    val setlists by viewModel.setlists.collectAsStateWithLifecycle()
    val userPreferences by viewModel.userPreferences.collectAsStateWithLifecycle()
    val playingOverrides by viewModel.playingOverrides.collectAsStateWithLifecycle()
    val labelsOnEverySong by viewModel.labelsOnEverySong.collectAsStateWithLifecycle()
    val isLoading by viewModel.isLoading.collectAsStateWithLifecycle()
    val hasLoadedLibrary = rememberHasLoadedLibrary(isLoading)
    val topFade = rememberListTopFade(listState, sectionHeaderContentType = SETLIST_HEADER_CONTENT_TYPE)
    // Remembered, since a new modifier every time the list recomposes would recompose the grid with it.
    val gridModifier = remember(topFade) { Modifier.fillMaxSize().listTopFadeViewport(topFade) }
    val isPerformanceModeEnabled by viewModel.isPerformanceModeEnabled.collectAsStateWithLifecycle()
    // Read by the headers alone, so midnight recomposes the ones counting down and not the list around them.
    val today by rememberToday()
    // Read once each, so that the branches below and the placeholders they render can never disagree about them.
    val setlistsPlaceholder = viewModel.setlistsPlaceholder.collectAsStateWithLifecycle().value
    LaunchedEffect(isScreenActive, isPerformanceModeEnabled, setlistsWithSongs) {
        if (!isScreenActive || isPerformanceModeEnabled ||
            setlistsWithSongs.none { it.setlist.fileName == reorderingSetlistFileName && !it.setlist.isArchived && it.setlist.entries.size > 1 }
        ) {
            onReorderingSetlistChanged(null)
        }
    }
    // The chords switched off take them out of the viewer, and the key is the shortest way of writing them down.
    val shouldShowChords = userPreferences?.areChordsEnabled != false
    // The tempo is what the click plays, so it goes with the metronome.
    val isMetronomeEnabled = userPreferences?.isMetronomeEnabled != false
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
    // A row lifted, every place it moves to and the row set down are felt: the row is under the finger moving it, which
    // covers the slot number that would otherwise say where it has got to.
    val hapticFeedback = LocalHapticFeedback.current
    val keyboardController = LocalSoftwareKeyboardController.current
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
                hapticFeedback.performHapticFeedback(HapticFeedbackType.SegmentFrequentTick)
            }
        }
    }
    // Written once the finger lifts rather than on every move, so that one drag is one write.
    val onDragStopped = {
        draggingSetlistFileName = null
        hapticFeedback.performHapticFeedback(HapticFeedbackType.GestureEnd)
        draggedSetlist?.let { dragged ->
            viewModel.reorderSetlist(
                setlistFileName = dragged.setlistFileName,
                songFileNames = dragged.songFileNames,
                // Compared by identity: a later drag of the same setlist that ended on the same order is another drag,
                // whose own write is still on its way.
                onNotWritten = { if (draggedSetlist === dragged) draggedSetlist = null },
            )
        }
        Unit
    }
    // The drawn order is given up only once the library agrees with it, not the moment the finger lifts: the write
    // has to reach the disk and come back, and the rows would sit in their old order until it did. It is given up
    // just as readily when the setlist turns out to hold different songs than the drag was working from, which is
    // what a removal or a sync landing mid drag looks like, and when it has been archived, since an archived setlist
    // is never reordered and the drag will not be written. A write that fails is told by reorderSetlist instead.
    LaunchedEffect(setlistsWithSongs, draggedSetlist) {
        draggedSetlist?.let { dragged ->
            val setlist = setlistsWithSongs.firstOrNull { it.setlist.fileName == dragged.setlistFileName }
            val songFileNames = setlist?.entries?.map { it.songFileName }
            if (setlist?.setlist?.isArchived == true || songFileNames == dragged.songFileNames || songFileNames?.toSet() != dragged.songFileNames.toSet()) {
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
    val searchedQuery = remember(query) { viewModel.songRenderer.normalizeForSearch(query) }
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
            modifier = gridModifier.bounceScrollableContent(listState, pull = topFade.overscrollPull),
            state = listState,
            contentPadding = contentPadding.only(start = true, end = true, bottom = true, extraEnd = FAST_SCROLLER_WIDTH, extraBottom = (if (isReordering) 88.dp else 8.dp) + bringToTopPadding),
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
                            { viewModel.showDialog(DialogType.NewSetlist) }
                        },
                        onImport = if (isPerformanceModeEnabled) null else {
                            { viewModel.importFiles(filePicker) }
                        },
                    )
                }

                else -> shownSetlists.forEach { setlistWithSongs ->
                    stickyHeader(
                        key = "setlist_${setlistWithSongs.setlist.fileName}",
                        contentType = SETLIST_HEADER_CONTENT_TYPE,
                    ) { headerIndex ->
                        val headerState = rememberSectionHeaderState(listState, headerIndex)
                        SectionHeader(
                            // Sticky placement belongs to the grid. Animating it as a regular item can briefly
                            // pull an already-pinned header away when entering reorder mode changes the list.
                            // Keep only its appearance fades.
                            modifier = listItemAnimation(listState, hasLoadedLibrary, placementSpec = null),
                            state = { headerState.value },
                            // The card fade and animated placement can settle on different frames near the top.
                            // Keep the pinned row opaque throughout reordering.
                            backgroundColor = if (isReordering) MaterialTheme.colorScheme.background else Color.Transparent,
                            endPadding = headerEndPadding,
                            text = setlistWithSongs.setlist.title,
                            subtitle = setlistWithSongs.headerSubtitle(today),
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
                                        actions = setlistActions,
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
                            // Derived, so that the keyboard sliding recomposes the item only as it crosses the threshold.
                            val windowInfo = LocalWindowInfo.current
                            val isCompactHeight by remember(windowInfo, contentPadding) {
                                derivedStateOf { windowInfo.containerDpSize.height - contentPadding.calculateBottomPadding() < SHORT_WINDOW_HEIGHT }
                            }
                            // The description stands as far from the first card as from the header's text above it,
                            // which is centered in a row of a fixed height, so the room above depends on whether the
                            // header has a subtitle and on the text size. The card's own padding counts towards it.
                            val gap = sectionHeaderBottomGap(hasSubtitle = setlistWithSongs.headerSubtitle(today) != null) + DESCRIPTION_TOP_PADDING
                            Text(
                                modifier = listItemAnimation(listState, hasLoadedLibrary).fadingUnderListTop(topFade)
                                    .animateContentSize().clickable(enabled = isCompactHeight) { isExpanded = !isExpanded }
                                    .padding(
                                        start = songCardTextKeyline,
                                        end = songCardTextKeyline,
                                        top = DESCRIPTION_TOP_PADDING,
                                        bottom = (gap - SONG_CARD_VERTICAL_PADDING).coerceAtLeast(0.dp),
                                    ),
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
                        val canMove = !isPerformanceModeEnabled && !setlistWithSongs.setlist.isArchived && setlistWithSongs.entries.size > 1
                        val isReorderable = canMove && isReordering && reorderingSetlistFileName == setlistWithSongs.setlist.fileName
                        // Above the branch below, which composes the row anew whenever reorder mode starts or ends: a
                        // visibility composed anew starts where its target is, so the grip would appear and vanish in one
                        // frame. A row that scrolls in while the mode is on starts with its grip already there, which is
                        // the list being shown rather than changed.
                        val handleVisibility = remember { MutableTransitionState(isReorderable) }
                        handleVisibility.targetState = isReorderable
                        // Above the branch too, so that a menu opened by a long press is the one the row's button still
                        // holds after the row is composed anew.
                        val actionsMenuState = rememberOverflowMenuState()
                        val hasActions = !isPerformanceModeEnabled && (!setlistWithSongs.setlist.isArchived || entry is SetlistWithSongs.Entry.Present)
                        // The songs screen's shortcut to the row's own overflow menu. In reorder mode a long press lifts
                        // the row instead, which is what a long press on a row of the setlist being reordered has to do.
                        val onLongClick: (() -> Unit)? = if (isDesktopPlatform || !hasActions || isReorderable) {
                            null
                        } else {
                            {
                                keyboardController?.hide()
                                actionsMenuState.open()
                            }
                        }
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
                        val onDragStarted: (Offset) -> Unit = {
                            draggingSetlistFileName = setlistWithSongs.setlist.fileName
                            hapticFeedback.performHapticFeedback(HapticFeedbackType.GestureThresholdActivate)
                        }
                        // The setlist's own transposition and capo for this song, which is why the same song can be
                        // listed in one key here and in another one two setlists down. Remembered as on the songs
                        // screen, and above the branch below so that reorder mode starting or ending does not work it
                        // out again: the row is composed again as every scroll starts and ends, and it is a whole
                        // transposition to work out.
                        val renderedKey = (entry as? SetlistWithSongs.Entry.Present)?.let { present ->
                            val playback = songPlaybackOf(song = present.song, setlistFileName = setlistWithSongs.setlist.fileName, overrides = playingOverrides)
                            val transposition = playback.transposition
                            val capo = playback.capo.fret
                            remember(present.song.key, present.song.transpose, transposition, capo, chordSpelling) {
                                viewModel.songRenderer.renderKey(song = present.song, transposition = transposition, capo = capo, spelling = chordSpelling)
                            }
                        }
                        // ReorderableItem's drag tracking, and the elevation and color it animates while a row is
                        // lifted, are only paid for while reorder mode is on. Every row of every setlist is wrapped
                        // until the grid has been narrowed to the one being reordered, which is what keeps the other
                        // setlists' rows out of the drag's drop targets during the scroll that brings it to the top.
                        // Outside the mode every row would otherwise carry an Animatable that never leaves its rest
                        // value for as long as the row is on screen, which is the very per-row coroutine
                        // draggedListItemContainerColor's own documentation says a list of this size cannot afford.
                        // scope is the ReorderableItem content's own receiver, which draggableHandle and
                        // longPressDraggableHandle are members of; it is null outside reorder mode, where isReorderable
                        // is always false and neither is ever called.
                        val rowContent: @Composable (scope: ReorderableCollectionItemScope?, isBeingDragged: Boolean, itemModifier: Modifier) -> Unit = { scope, isBeingDragged, itemModifier ->
                            val elevation = if (isReordering) {
                                val animated by animateDpAsState(if (isBeingDragged) 8.dp else 0.dp)
                                animated
                            } else {
                                0.dp
                            }
                            val containerColor = if (isReordering) {
                                draggedListItemContainerColor(isBeingDragged)
                            } else {
                                MaterialTheme.colorScheme.surfaceContainerLow
                            }
                            val cardModifier = itemModifier.then(moveActions).fadingUnderListTop(topFade).let { base ->
                                if (scope == null) base else with(scope) { base.longPressDraggableHandle(enabled = isReorderable, onDragStarted = onDragStarted, onDragStopped = onDragStopped) }
                            }
                            // Keep the overflow button available on touch platforms too, including while a
                            // long press belongs to reordering rather than the song's actions.
                            val actions: (@Composable () -> Unit)? = if (isPerformanceModeEnabled) {
                                null
                            } else {
                                {
                                    AnimatedVisibility(
                                        visible = hasActions,
                                        enter = fadeIn() + expandHorizontally(),
                                        exit = fadeOut() + shrinkHorizontally(),
                                    ) {
                                        Row(
                                            verticalAlignment = Alignment.CenterVertically,
                                        ) {
                                            // The grip goes in front of the overflow button rather than after it, so that
                                            // the button lands exactly where the songs screen has its own.
                                            AnimatedVisibility(
                                                visibleState = handleVisibility,
                                                enter = fadeIn() + expandHorizontally(),
                                                exit = fadeOut() + shrinkHorizontally(),
                                            ) {
                                                DragHandle(
                                                    modifier = if (isReorderable && scope != null) {
                                                        with(scope) { Modifier.draggableHandle(onDragStarted = onDragStarted, onDragStopped = onDragStopped) }
                                                    } else {
                                                        Modifier
                                                    },
                                                )
                                            }
                                            SetlistEntryActions(
                                                state = actionsMenuState,
                                                viewModel = viewModel,
                                                songActions = songActions,
                                                entry = entry,
                                                setlistFileName = setlistWithSongs.setlist.fileName,
                                                isArchived = setlistWithSongs.setlist.isArchived,
                                                onMoveUp = onMoveUp,
                                                onMoveDown = onMoveDown,
                                            )
                                        }
                                    }
                                }
                            }
                            when (entry) {
                                is SetlistWithSongs.Entry.Present -> {
                                    val setlistFileName = setlistWithSongs.setlist.fileName
                                    SongListItem(
                                        modifier = cardModifier,
                                        song = entry.song,
                                        index = row.index,
                                        cardPadding = songCardPadding(rowIndex, columnCount),
                                        key = renderedKey,
                                        // And the setlist's own tempo, for the same reason.
                                        tempo = songPlaybackOf(
                                            song = entry.song,
                                            setlistFileName = setlistFileName,
                                            overrides = playingOverrides,
                                        ).tempo.displayedBpm.takeIf { isMetronomeEnabled },
                                        shouldShowChords = shouldShowChords,
                                        duration = entry.song.duration,
                                        coverArtUrl = entry.song.coverArtUrl?.takeIf { isCoverArtEnabled },
                                        labelsOnEverySong = labelsOnEverySong,
                                        shouldShowLabels = false,
                                        containerColor = containerColor,
                                        shadowElevation = elevation,
                                        onClick = { viewModel.openSongInSetlist(setlistWithSongs, entry.song) },
                                        onLongClick = onLongClick,
                                        actions = actions,
                                    )
                                }

                                // Nothing to open, but it still takes its place in the order and can be removed.
                                is SetlistWithSongs.Entry.Missing -> MissingSongListItem(
                                    modifier = cardModifier,
                                    index = row.index,
                                    songFileName = entry.songFileName,
                                    cardPadding = songCardPadding(rowIndex, columnCount),
                                    containerColor = containerColor,
                                    shadowElevation = elevation,
                                    onLongClick = onLongClick,
                                    actions = actions,
                                )
                            }
                        }
                        if (isReordering) {
                            ReorderableItem(
                                state = reorderableState,
                                key = key.string.orEmpty(),
                                enabled = draggingSetlistFileName.let { it == null || it == setlistWithSongs.setlist.fileName },
                                animateItemModifier = listItemAnimation(
                                    listState = listState,
                                    isEnabled = hasLoadedLibrary,
                                    isRearranging = draggedSetlist != null,
                                ),
                            ) { isBeingDragged -> rowContent(this, isBeingDragged, Modifier) }
                        } else {
                            rowContent(null, false, listItemAnimation(listState = listState, isEnabled = hasLoadedLibrary))
                        }
                    }
                    // Every setlist ends in the way to more songs, which for an empty one is also all there is under its
                    // header, so the way to them is where the songs will be rather than behind the header's menu.
                    if (!isPerformanceModeEnabled) {
                        item(
                            key = "choose_songs_${setlistWithSongs.setlist.fileName}",
                            span = { GridItemSpan(maxLineSpan) },
                            contentType = "setlist_action",
                        ) {
                            // The item animation goes on the item's root layout, the only one the grid animates, which
                            // is the visibility wrapper here rather than the row inside it.
                            AnimatedVisibility(
                                modifier = listItemAnimation(listState, hasLoadedLibrary),
                                visible = !setlistWithSongs.setlist.isArchived,
                            ) {
                                ActionListItem(
                                    modifier = Modifier.fadingUnderListTop(topFade),
                                    title = stringResource(Res.string.setlists_choose_songs),
                                    icon = painterResource(Res.drawable.ic_add),
                                    onClick = { viewModel.showDialog(DialogType.SongPicker(setlistWithSongs.setlist)) },
                                )
                            }
                        }
                    }
                }
            }
            if (narrowedSetlistFileName == null && setlists.any { it.isArchived }) {
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
            setlistActions = setlistActions,
            listState = listState,
            setlistsWithSongs = shownSetlists,
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

/** What a setlist's header is to the grid, and to the cards' fade, which is measured from the setlist being read. */
private const val SETLIST_HEADER_CONTENT_TYPE = "setlist_header"

/** The room between a setlist's description and the text of the header above it, on top of what the header leaves. */
private val DESCRIPTION_TOP_PADDING = 8.dp

/**
 * The index the grid holds a setlist's header at, counted the way [SetlistList] emits the items of the setlists before
 * it while performance mode is off, which it is whenever a setlist is being reordered.
 */
internal fun List<SetlistWithSongs>.headerIndexOf(setlistFileName: String?): Int? {
    var index = 0
    forEach { setlistWithSongs ->
        if (setlistWithSongs.setlist.fileName == setlistFileName) return index
        index += 2 + setlistWithSongs.entries.size + if (setlistWithSongs.setlist.description.isNotBlank()) 1 else 0
    }
    return null
}

/** Swap the row at [from] with its neighbor in the already checked direction. */
internal fun List<String>.movedOnePlace(from: Int, by: Int): List<String> {
    val result = toMutableList()
    val to = from + by
    val neighbor = result[to]
    result[to] = result[from]
    result[from] = neighbor
    return result
}
