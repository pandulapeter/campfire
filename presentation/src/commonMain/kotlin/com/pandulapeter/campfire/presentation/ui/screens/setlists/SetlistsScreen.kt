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
import androidx.compose.foundation.background
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
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.grid.rememberLazyGridState
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.SwipeToDismissBox
import androidx.compose.material3.SwipeToDismissBoxValue
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.material3.rememberSwipeToDismissBoxState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.pandulapeter.campfire.presentation.resources.Res
import com.pandulapeter.campfire.presentation.resources.ic_add
import com.pandulapeter.campfire.presentation.resources.filters
import com.pandulapeter.campfire.presentation.resources.ic_delete
import com.pandulapeter.campfire.presentation.resources.ic_export
import com.pandulapeter.campfire.presentation.resources.ic_edit
import com.pandulapeter.campfire.presentation.resources.ic_tune
import com.pandulapeter.campfire.presentation.resources.setlists
import com.pandulapeter.campfire.presentation.resources.setlists_new_setlist
import com.pandulapeter.campfire.presentation.resources.setlists_delete_setlist
import com.pandulapeter.campfire.presentation.resources.setlists_remove_song
import com.pandulapeter.campfire.presentation.resources.setlists_export
import com.pandulapeter.campfire.presentation.resources.setlists_rename
import com.pandulapeter.campfire.presentation.resources.setlists_reorder_hint
import com.pandulapeter.campfire.presentation.ui.CampfireViewModel
import com.pandulapeter.campfire.presentation.ui.components.CampfireFloatingActionButton
import com.pandulapeter.campfire.presentation.ui.components.CampfireTopAppBar
import com.pandulapeter.campfire.presentation.ui.components.FAB_CLEARANCE
import com.pandulapeter.campfire.presentation.ui.components.ListColumns
import com.pandulapeter.campfire.presentation.ui.components.ListPlaceholder
import com.pandulapeter.campfire.presentation.ui.components.SECTION_HEADER_GAP
import com.pandulapeter.campfire.presentation.ui.components.SectionHeader
import com.pandulapeter.campfire.presentation.ui.components.MissingSongListItem
import com.pandulapeter.campfire.presentation.ui.components.SectionHeaderAction
import com.pandulapeter.campfire.presentation.ui.components.SongListItem
import com.pandulapeter.campfire.presentation.ui.components.SongsControlsSidePanel
import com.pandulapeter.campfire.presentation.ui.components.animateScrollToKey
import com.pandulapeter.campfire.presentation.ui.components.besideSidePanel
import com.pandulapeter.campfire.presentation.ui.components.hasRoomForSidePanel
import com.pandulapeter.campfire.presentation.ui.components.listItemAnimation
import com.pandulapeter.campfire.presentation.ui.components.rememberHasLoadedLibrary
import com.pandulapeter.campfire.presentation.ui.components.songListColumnCount
import com.pandulapeter.campfire.presentation.ui.platform.LocalFilePicker
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
    val isPerformanceModeEnabled by viewModel.isPerformanceModeEnabled.collectAsStateWithLifecycle()
    val isSidePanelVisible = hasRoomForSidePanel(settledWidth)
    val columnCount = songListColumnCount(
        settledWidth = settledWidth,
        contentPadding = contentPadding,
        isSidePanelVisible = isSidePanelVisible,
    )
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
                                contentDescription = stringResource(Res.string.filters),
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
        SongsControlsSidePanel(
            isVisible = isSidePanelVisible,
            viewModel = viewModel,
            shouldIncludeSorting = false,
            contentPadding = contentPadding,
        )
    }
}

@Composable
private fun SetlistList(
    modifier: Modifier = Modifier,
    viewModel: CampfireViewModel,
    columnCount: Int,
    contentPadding: PaddingValues,
) {
    val setlistsWithSongs by viewModel.setlistsWithSongs.collectAsStateWithLifecycle()
    val isLoading by viewModel.isLoading.collectAsStateWithLifecycle()
    val hasLoadedLibrary = rememberHasLoadedLibrary(isLoading)
    val isPerformanceModeEnabled by viewModel.isPerformanceModeEnabled.collectAsStateWithLifecycle()
    // Read once each, so that the branches below and the placeholders they render can never disagree about them.
    val setlistsPlaceholder = viewModel.setlistsPlaceholder.collectAsStateWithLifecycle().value
    val libraryPlaceholder = viewModel.libraryPlaceholder.collectAsStateWithLifecycle().value
    val listState = rememberLazyGridState()
    val reorderableState = rememberReorderableLazyGridState(listState) { from, to ->
        val fromKey = SetlistItemKey(from.key as? String)
        val toKey = SetlistItemKey(to.key as? String)
        // Songs can only be reordered within their own setlist.
        if (fromKey.setlistFileName != null && fromKey.setlistFileName == toKey.setlistFileName && fromKey.songFileName != null && toKey.songFileName != null) {
            viewModel.moveSongInSetlist(
                setlistFileName = fromKey.setlistFileName,
                fromSongFileName = fromKey.songFileName,
                toSongFileName = toKey.songFileName,
            )
        }
    }
    val layoutDirection = LocalLayoutDirection.current
    val filePicker = LocalFilePicker.current
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
                    modifier = listItemAnimation(hasLoadedLibrary).fillMaxWidth(),
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
                        modifier = listItemAnimation(hasLoadedLibrary),
                        text = setlistWithSongs.setlist.title,
                        onClick = { coroutineScope.launch { listState.animateScrollToKey(headerKey) } },
                        action = if (isPerformanceModeEnabled) null else {
                            {
                                SectionHeaderAction(
                                    icon = painterResource(Res.drawable.ic_edit),
                                    contentDescription = stringResource(Res.string.setlists_rename),
                                    onClick = { viewModel.showDialog(CampfireViewModel.DialogType.RenameSetlist(setlistWithSongs.setlist)) },
                                )
                                SectionHeaderAction(
                                    icon = painterResource(Res.drawable.ic_export),
                                    contentDescription = stringResource(Res.string.setlists_export),
                                    onClick = { viewModel.exportSetlist(filePicker, setlistWithSongs.setlist.fileName) },
                                )
                                SectionHeaderAction(
                                    icon = painterResource(Res.drawable.ic_delete),
                                    contentDescription = stringResource(Res.string.setlists_delete_setlist),
                                    onClick = { viewModel.showDialog(CampfireViewModel.DialogType.DeleteSetlist(setlistWithSongs.setlist)) },
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
                            modifier = listItemAnimation(hasLoadedLibrary).padding(horizontal = 16.dp, vertical = 8.dp),
                            text = stringResource(Res.string.setlists_reorder_hint),
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }
                items(
                    items = setlistWithSongs.entries,
                    key = { entry -> SetlistItemKey(setlistFileName = setlistWithSongs.setlist.fileName, songFileName = entry.songFileName).string.orEmpty() },
                ) { entry ->
                    val key = SetlistItemKey(setlistFileName = setlistWithSongs.setlist.fileName, songFileName = entry.songFileName)
                    ReorderableItem(
                        modifier = listItemAnimation(hasLoadedLibrary),
                        state = reorderableState,
                        key = key.string.orEmpty(),
                    ) { isBeingDragged ->
                        DismissibleSongItem(
                            isEnabled = !isPerformanceModeEnabled,
                            onDismissed = { viewModel.removeSongFromSetlist(songFileName = entry.songFileName, setlistFileName = setlistWithSongs.setlist.fileName) },
                        ) {
                            val elevation by animateDpAsState(if (isBeingDragged) 8.dp else 0.dp)
                            Surface(
                                shadowElevation = elevation
                            ) {
                                when (entry) {
                                    is CampfireViewModel.SetlistWithSongs.Entry.Present -> SongListItem(
                                        modifier = Modifier.longPressDraggableHandle(enabled = !isPerformanceModeEnabled),
                                        song = entry.song,
                                        isBeingDragged = isBeingDragged,
                                        onClick = { viewModel.openSongInSetlist(setlistWithSongs, entry.song) },
                                    )

                                    // Nothing to open, but it still takes part in the reordering and the swipe.
                                    is CampfireViewModel.SetlistWithSongs.Entry.Missing -> MissingSongListItem(
                                        modifier = Modifier.longPressDraggableHandle(enabled = !isPerformanceModeEnabled),
                                        songFileName = entry.songFileName,
                                    )
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}

/**
 * @param isEnabled False in performance mode, where the swipe is taken away rather than the row: a setlist still
 *   reads and scrolls exactly as it did, it simply cannot lose a song to a gesture meant for the page.
 */
@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
private fun DismissibleSongItem(
    isEnabled: Boolean,
    onDismissed: () -> Unit,
    content: @Composable () -> Unit,
) {
    val dismissState = rememberSwipeToDismissBoxState()
    // The list keeps the saved state of removed keys, so a re-added song must not start out dismissed.
    LaunchedEffect(Unit) { dismissState.reset() }
    SwipeToDismissBox(
        state = dismissState,
        enableDismissFromStartToEnd = isEnabled,
        enableDismissFromEndToStart = false,
        onDismiss = { if (it == SwipeToDismissBoxValue.StartToEnd) onDismissed() },
        backgroundContent = {
            Box(
                modifier = Modifier.fillMaxSize().background(MaterialTheme.colorScheme.errorContainer).padding(horizontal = 24.dp),
                contentAlignment = Alignment.CenterStart,
            ) {
                Icon(
                    painter = painterResource(Res.drawable.ic_delete),
                    contentDescription = stringResource(Res.string.setlists_remove_song),
                    tint = MaterialTheme.colorScheme.onErrorContainer,
                )
            }
        },
    ) {
        content()
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
        // Contains a character sanitizeFileName() rejects, so it can never occur inside a file name.
        private const val TOKEN = "#*#"
    }
}
