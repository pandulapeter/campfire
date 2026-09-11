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

import androidx.compose.animation.core.animateDpAsState
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.calculateEndPadding
import androidx.compose.foundation.layout.calculateStartPadding
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.grid.GridItemSpan
import androidx.compose.foundation.lazy.grid.LazyGridState
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.pandulapeter.campfire.data.model.domain.UserPreferences
import com.pandulapeter.campfire.presentation.resources.Res
import com.pandulapeter.campfire.presentation.resources.ic_add
import com.pandulapeter.campfire.presentation.resources.ic_archive
import com.pandulapeter.campfire.presentation.resources.ic_setlists_remove
import com.pandulapeter.campfire.presentation.resources.ic_tune
import com.pandulapeter.campfire.presentation.resources.setlists
import com.pandulapeter.campfire.presentation.resources.setlists_archived
import com.pandulapeter.campfire.presentation.resources.setlists_new_setlist
import com.pandulapeter.campfire.presentation.resources.setlists_sort_and_filter
import com.pandulapeter.campfire.presentation.resources.setlists_remove_song
import com.pandulapeter.campfire.presentation.resources.setlists_reorder_hint
import com.pandulapeter.campfire.presentation.ui.CampfireViewModel
import com.pandulapeter.campfire.presentation.ui.components.ActionsMenu
import com.pandulapeter.campfire.presentation.ui.components.ActionsMenuItem
import com.pandulapeter.campfire.presentation.ui.components.CampfireFloatingActionButton
import com.pandulapeter.campfire.presentation.ui.components.CampfireTopAppBar
import com.pandulapeter.campfire.presentation.ui.components.ControlsSidePanel
import com.pandulapeter.campfire.presentation.ui.components.DragHandle
import com.pandulapeter.campfire.presentation.ui.components.FAB_CLEARANCE
import com.pandulapeter.campfire.presentation.ui.components.ListColumns
import com.pandulapeter.campfire.presentation.ui.components.ListPlaceholder
import com.pandulapeter.campfire.presentation.ui.components.KeepTopAppBarInSync
import com.pandulapeter.campfire.presentation.ui.components.SECTION_HEADER_GAP
import com.pandulapeter.campfire.presentation.ui.components.SectionHeader
import com.pandulapeter.campfire.presentation.ui.components.SetlistActionsMenu
import com.pandulapeter.campfire.presentation.ui.components.SetlistsControls
import com.pandulapeter.campfire.presentation.ui.components.MissingSongListItem
import com.pandulapeter.campfire.presentation.ui.components.SongListItem
import com.pandulapeter.campfire.presentation.ui.components.SongActionsMenu
import com.pandulapeter.campfire.presentation.ui.components.animateScrollToKey
import com.pandulapeter.campfire.presentation.ui.components.besideSidePanel
import com.pandulapeter.campfire.presentation.ui.components.hasRoomForSidePanel
import com.pandulapeter.campfire.presentation.ui.components.listItemAnimation
import com.pandulapeter.campfire.presentation.ui.components.rememberHasLoadedLibrary
import com.pandulapeter.campfire.presentation.ui.components.rememberRetainedLazyGridState
import com.pandulapeter.campfire.presentation.ui.components.songListColumnCount
import com.pandulapeter.campfire.presentation.localization.stringResource
import kotlinx.coroutines.launch
import org.jetbrains.compose.resources.painterResource
import sh.calvin.reorderable.ReorderableItem
import sh.calvin.reorderable.rememberReorderableLazyGridState

@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun SetlistsScreen(
    modifier: Modifier = Modifier,
    viewModel: CampfireViewModel,
    settledWidth: Dp,
    contentPadding: PaddingValues,
) {
    val scrollBehavior = TopAppBarDefaults.pinnedScrollBehavior()
    val listState = rememberRetainedLazyGridState(viewModel.setlistsScrollPosition)
    val isPerformanceModeEnabled by viewModel.isPerformanceModeEnabled.collectAsStateWithLifecycle()
    val isSidePanelVisible = hasRoomForSidePanel(settledWidth)
    val columnCount = songListColumnCount(
        settledWidth = settledWidth,
        contentPadding = contentPadding,
        isSidePanelVisible = isSidePanelVisible,
    )
    KeepTopAppBarInSync(scrollBehavior, listState)
    Row(
        modifier = modifier.fillMaxSize()
    ) {
        Column(
            modifier = Modifier.weight(1f).fillMaxSize().nestedScroll(scrollBehavior.nestedScrollConnection)
        ) {
            CampfireTopAppBar(
                scrollBehavior = scrollBehavior,
                title = { Text(stringResource(Res.string.setlists)) },
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
            val listContentPadding = contentPadding.besideSidePanel(isSidePanelVisible)
            Box(
                modifier = Modifier.fillMaxSize()
            ) {
                SetlistList(
                    modifier = Modifier.fillMaxSize(),
                    viewModel = viewModel,
                    listState = listState,
                    columnCount = columnCount,
                    contentPadding = listContentPadding,
                )
                CampfireFloatingActionButton(
                    modifier = Modifier.align(Alignment.BottomEnd),
                    isVisible = !isPerformanceModeEnabled,
                    settledWidth = settledWidth,
                    contentPadding = listContentPadding,
                    icon = painterResource(Res.drawable.ic_add),
                    label = stringResource(Res.string.setlists_new_setlist),
                    onClick = { viewModel.showDialog(CampfireViewModel.DialogType.NewSetlist) },
                )
            }
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

@Composable
private fun SetlistList(
    modifier: Modifier = Modifier,
    viewModel: CampfireViewModel,
    listState: LazyGridState,
    columnCount: Int,
    contentPadding: PaddingValues,
) {
    val setlistsWithSongs by viewModel.setlistsWithSongs.collectAsStateWithLifecycle()
    val userPreferences by viewModel.userPreferences.collectAsStateWithLifecycle()
    val transpositions by viewModel.transpositions.collectAsStateWithLifecycle()
    val isLoading by viewModel.isLoading.collectAsStateWithLifecycle()
    val hasLoadedLibrary = rememberHasLoadedLibrary(isLoading)
    val isPerformanceModeEnabled by viewModel.isPerformanceModeEnabled.collectAsStateWithLifecycle()
    // Read once each, so that the branches below and the placeholders they render can never disagree about them.
    val setlistsPlaceholder = viewModel.setlistsPlaceholder.collectAsStateWithLifecycle().value
    // Lyrics only mode takes the chords out of the viewer, and the key is the shortest way of writing them down.
    val shouldShowChords = userPreferences?.isLyricsOnlyModeEnabled != true
    val chordSpelling = userPreferences?.chordSpelling ?: UserPreferences.ChordSpelling.Default
    val libraryPlaceholder = viewModel.libraryPlaceholder.collectAsStateWithLifecycle().value
    // The order the rows are drawn in while a drag is in flight, before any of it has been written down. The
    // reorderable state has to see every move answered in the frame it reports it, and the library is several frames
    // away: a move that had to go to disk and come back through the repository left the row under the finger
    // snapping between where it was and where it had been put.
    var draggedSetlist by remember { mutableStateOf<DraggedSetlist?>(null) }
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
    val layoutDirection = LocalLayoutDirection.current
    val coroutineScope = rememberCoroutineScope()
    LazyVerticalGrid(
        columns = ListColumns(columnCount),
        modifier = modifier,
        state = listState,
        contentPadding = PaddingValues(
            start = contentPadding.calculateStartPadding(layoutDirection),
            top = SECTION_HEADER_GAP,
            end = contentPadding.calculateEndPadding(layoutDirection),
            bottom = contentPadding.calculateBottomPadding() + FAB_CLEARANCE,
        ),
    ) {
        // The setlists come first: they are what this screen is about. The library only speaks up once there are
        // setlists to fill, since without it the rows of every setlist would be missing rather than the setlists
        // themselves. Both go through the same slot, so that "still loading" turning out to be "you have no
        // setlists" cross fades instead of being swapped in a single frame.
        val placeholder = setlistsPlaceholder ?: libraryPlaceholder
        when {
            placeholder != null -> item(
                key = "placeholder",
                span = { GridItemSpan(maxLineSpan) },
            ) {
                ListPlaceholder(
                    modifier = listItemAnimation(listState, hasLoadedLibrary).fillMaxWidth(),
                    placeholder = placeholder,
                    onRetry = viewModel::refresh,
                )
            }

            else -> setlistsWithSongs.forEach { setlistWithSongs ->
                val headerKey = "setlist_${setlistWithSongs.setlist.fileName}"
                item(
                    key = headerKey,
                    span = { GridItemSpan(maxLineSpan) },
                ) {
                    SectionHeader(
                        modifier = listItemAnimation(listState, hasLoadedLibrary),
                        text = setlistWithSongs.setlist.title,
                        // A setlist is only ever on this screen archived because the filter was asked to show them,
                        // so the mark is what tells it from the ones still in use.
                        icon = if (setlistWithSongs.setlist.isArchived) painterResource(Res.drawable.ic_archive) else null,
                        iconContentDescription = stringResource(Res.string.setlists_archived),
                        onClick = { coroutineScope.launch { listState.animateScrollToKey(headerKey) } },
                        action = if (isPerformanceModeEnabled) null else {
                            {
                                SetlistActionsMenu(
                                    viewModel = viewModel,
                                    setlist = setlistWithSongs.setlist,
                                )
                            }
                        },
                    )
                }
                if (setlistWithSongs.entries.isEmpty() && !isPerformanceModeEnabled) {
                    item(
                        key = "hint_${setlistWithSongs.setlist.fileName}",
                        span = { GridItemSpan(maxLineSpan) },
                    ) {
                        Text(
                            modifier = listItemAnimation(listState, hasLoadedLibrary).padding(horizontal = 16.dp, vertical = 8.dp),
                            text = stringResource(Res.string.setlists_reorder_hint),
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }
                items(
                    items = setlistWithSongs.rows(draggedSetlist),
                    key = { row -> SetlistItemKey(setlistFileName = setlistWithSongs.setlist.fileName, songFileName = row.entry.songFileName).string.orEmpty() },
                ) { row ->
                    val entry = row.entry
                    val key = SetlistItemKey(setlistFileName = setlistWithSongs.setlist.fileName, songFileName = entry.songFileName)
                    // The placement animation goes to ReorderableItem rather than onto the item itself, because it
                    // is what decides which rows may have one: the row under the finger is placed by the drag's own
                    // translation, and a placement animation on top of that animates it back towards the slot it is
                    // being dragged out of, which is the jumping. Every other row still slides into place.
                    ReorderableItem(
                        state = reorderableState,
                        key = key.string.orEmpty(),
                        animateItemModifier = listItemAnimation(listState, hasLoadedLibrary),
                    ) { isBeingDragged ->
                        val elevation by animateDpAsState(if (isBeingDragged) 8.dp else 0.dp)
                        Surface(
                            shadowElevation = elevation
                        ) {
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
                                        DragHandle(modifier = Modifier.draggableHandle(onDragStopped = onDragStopped))
                                        SetlistEntryActionsMenu(
                                            viewModel = viewModel,
                                            entry = entry,
                                            setlistFileName = setlistWithSongs.setlist.fileName,
                                        )
                                    }
                                }
                            }
                            when (entry) {
                                is CampfireViewModel.SetlistWithSongs.Entry.Present -> SongListItem(
                                    modifier = Modifier.longPressDraggableHandle(enabled = !isPerformanceModeEnabled, onDragStopped = onDragStopped),
                                    song = entry.song,
                                    index = row.index,
                                    // The setlist's own transposition of this song, which is why the same song
                                    // can be listed in one key here and in another one two setlists down.
                                    key = viewModel.renderKey(
                                        song = entry.song,
                                        transposition = transpositions[entry.song.fileName, setlistWithSongs.setlist.fileName],
                                        spelling = chordSpelling,
                                    ),
                                    shouldShowChords = shouldShowChords,
                                    isBeingDragged = isBeingDragged,
                                    onClick = { viewModel.openSongInSetlist(setlistWithSongs, entry.song) },
                                    actions = actions,
                                )

                                // Nothing to open, but it still takes its place in the order and can be removed.
                                is CampfireViewModel.SetlistWithSongs.Entry.Missing -> MissingSongListItem(
                                    modifier = Modifier.longPressDraggableHandle(enabled = !isPerformanceModeEnabled, onDragStopped = onDragStopped),
                                    index = row.index,
                                    songFileName = entry.songFileName,
                                    actions = actions,
                                )
                            }
                        }
                    }
                }
            }
        }
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
 * The overflow menu of one row of a setlist. A row whose file has gone missing has no song to act on, so it is
 * offered the only thing that still applies to it - being taken out of the setlist - rather than a menu full of
 * entries that would all fail.
 */
@Composable
private fun SetlistEntryActionsMenu(
    viewModel: CampfireViewModel,
    entry: CampfireViewModel.SetlistWithSongs.Entry,
    setlistFileName: String,
) = when (entry) {
    // Nothing is locked: the sheet's box for this very setlist is what unticks the song out of it, which is the
    // swipe written as a list rather than as a gesture.
    is CampfireViewModel.SetlistWithSongs.Entry.Present -> SongActionsMenu(
        viewModel = viewModel,
        song = entry.song,
        lockedSetlistFileName = null,
    )

    is CampfireViewModel.SetlistWithSongs.Entry.Missing -> ActionsMenu { dismiss ->
        ActionsMenuItem(
            title = stringResource(Res.string.setlists_remove_song),
            icon = painterResource(Res.drawable.ic_setlists_remove),
            onClick = {
                dismiss()
                viewModel.removeSongFromSetlist(songFileName = entry.songFileName, setlistFileName = setlistFileName)
            },
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
