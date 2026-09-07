package com.pandulapeter.campfire.presentation.ui.screens.setlists

import androidx.compose.animation.core.animateDpAsState
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
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
import androidx.compose.foundation.lazy.grid.itemsIndexed
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
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.pandulapeter.campfire.presentation.resources.Res
import com.pandulapeter.campfire.presentation.resources.filters
import com.pandulapeter.campfire.presentation.resources.ic_delete
import com.pandulapeter.campfire.presentation.resources.ic_setlists
import com.pandulapeter.campfire.presentation.resources.ic_songs
import com.pandulapeter.campfire.presentation.resources.ic_tune
import com.pandulapeter.campfire.presentation.resources.setlists
import com.pandulapeter.campfire.presentation.resources.setlists_delete_setlist
import com.pandulapeter.campfire.presentation.resources.setlists_no_data
import com.pandulapeter.campfire.presentation.resources.setlists_no_data_hint
import com.pandulapeter.campfire.presentation.resources.setlists_remove_song
import com.pandulapeter.campfire.presentation.resources.setlists_reorder_hint
import com.pandulapeter.campfire.presentation.resources.songs_no_data
import com.pandulapeter.campfire.presentation.resources.songs_no_data_hint
import com.pandulapeter.campfire.presentation.ui.CampfireViewModel
import com.pandulapeter.campfire.presentation.ui.components.CampfireTopAppBar
import com.pandulapeter.campfire.presentation.ui.components.EmptyState
import com.pandulapeter.campfire.presentation.ui.components.ListColumns
import com.pandulapeter.campfire.presentation.ui.components.SectionHeader
import com.pandulapeter.campfire.presentation.ui.components.SectionHeaderAction
import com.pandulapeter.campfire.presentation.ui.components.SongListItem
import com.pandulapeter.campfire.presentation.ui.components.SongsControlsSidePanel
import com.pandulapeter.campfire.presentation.ui.components.besideSidePanel
import com.pandulapeter.campfire.presentation.ui.components.hasRoomForSidePanel
import com.pandulapeter.campfire.presentation.localization.stringResource
import org.jetbrains.compose.resources.painterResource
import sh.calvin.reorderable.ReorderableItem
import sh.calvin.reorderable.rememberReorderableLazyGridState

@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun SetlistsScreen(
    modifier: Modifier = Modifier,
    viewModel: CampfireViewModel,
    contentPadding: PaddingValues
) = BoxWithConstraints(
    modifier = modifier.fillMaxSize()
) {
    val scrollBehavior = TopAppBarDefaults.pinnedScrollBehavior()
    val isSidePanelVisible = hasRoomForSidePanel(maxWidth)
    Row(
        modifier = Modifier.fillMaxSize()
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
                                contentDescription = stringResource(Res.string.filters)
                            )
                        }
                    }
                }
            )
            SetlistList(
                modifier = Modifier.fillMaxSize(),
                viewModel = viewModel,
                contentPadding = contentPadding.besideSidePanel(isSidePanelVisible)
            )
        }
        SongsControlsSidePanel(
            isVisible = isSidePanelVisible,
            viewModel = viewModel,
            shouldIncludeSorting = false,
            contentPadding = contentPadding
        )
    }
}

@Composable
private fun SetlistList(
    modifier: Modifier = Modifier,
    viewModel: CampfireViewModel,
    contentPadding: PaddingValues
) {
    val setlistsWithSongs by viewModel.setlistsWithSongs.collectAsStateWithLifecycle()
    val allSongs by viewModel.allSongs.collectAsStateWithLifecycle()
    val rawSongDetails by viewModel.rawSongDetails.collectAsStateWithLifecycle()
    val listState = rememberLazyGridState()
    val reorderableState = rememberReorderableLazyGridState(listState) { from, to ->
        val fromKey = SetlistItemKey(from.key as? String)
        val toKey = SetlistItemKey(to.key as? String)
        // Songs can only be reordered within their own setlist.
        if (fromKey.setlistId != null && fromKey.setlistId == toKey.setlistId && fromKey.songId != null && toKey.songId != null) {
            viewModel.moveSongInSetlist(setlistId = fromKey.setlistId, fromSongId = fromKey.songId, toSongId = toKey.songId)
        }
    }
    val layoutDirection = LocalLayoutDirection.current
    LazyVerticalGrid(
        columns = ListColumns,
        modifier = modifier,
        state = listState,
        contentPadding = PaddingValues(
            start = contentPadding.calculateStartPadding(layoutDirection),
            end = contentPadding.calculateEndPadding(layoutDirection),
            bottom = contentPadding.calculateBottomPadding() + FAB_CLEARANCE
        )
    ) {
        when {
            setlistsWithSongs.isEmpty() -> item(
                key = "empty",
                span = { GridItemSpan(maxLineSpan) }
            ) {
                EmptyState(
                    modifier = Modifier.fillMaxWidth().animateItem(),
                    icon = painterResource(Res.drawable.ic_setlists),
                    title = stringResource(Res.string.setlists_no_data),
                    hint = stringResource(Res.string.setlists_no_data_hint)
                )
            }

            allSongs.isEmpty() -> item(
                key = "empty",
                span = { GridItemSpan(maxLineSpan) }
            ) {
                EmptyState(
                    modifier = Modifier.fillMaxWidth().animateItem(),
                    icon = painterResource(Res.drawable.ic_songs),
                    title = stringResource(Res.string.songs_no_data),
                    hint = stringResource(Res.string.songs_no_data_hint)
                )
            }

            else -> setlistsWithSongs.forEach { setlistWithSongs ->
                stickyHeader(key = "setlist_${setlistWithSongs.setlist.id}") {
                    SectionHeader(
                        modifier = Modifier.animateItem(),
                        text = setlistWithSongs.setlist.title,
                        action = {
                            SectionHeaderAction(
                                icon = painterResource(Res.drawable.ic_delete),
                                contentDescription = stringResource(Res.string.setlists_delete_setlist),
                                onClick = { viewModel.showDialog(CampfireViewModel.DialogType.DeleteSetlist(setlistWithSongs.setlist)) }
                            )
                        }
                    )
                }
                if (setlistWithSongs.songs.isEmpty()) {
                    item(
                        key = "hint_${setlistWithSongs.setlist.id}",
                        span = { GridItemSpan(maxLineSpan) }
                    ) {
                        Text(
                            modifier = Modifier.animateItem().padding(horizontal = 16.dp, vertical = 8.dp),
                            text = stringResource(Res.string.setlists_reorder_hint),
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
                itemsIndexed(
                    items = setlistWithSongs.songs,
                    key = { _, song -> SetlistItemKey(setlistId = setlistWithSongs.setlist.id, songId = song.id).string.orEmpty() }
                ) { index, song ->
                    val key = SetlistItemKey(setlistId = setlistWithSongs.setlist.id, songId = song.id)
                    ReorderableItem(
                        modifier = Modifier.animateItem(),
                        state = reorderableState,
                        key = key.string.orEmpty()
                    ) { isBeingDragged ->
                        DismissibleSongItem(
                            onDismissed = { viewModel.removeSongFromSetlist(songId = song.id, setlistId = setlistWithSongs.setlist.id) }
                        ) {
                            val elevation by animateDpAsState(if (isBeingDragged) 8.dp else 0.dp)
                            Surface(
                                shadowElevation = elevation
                            ) {
                                SongListItem(
                                    modifier = Modifier.longPressDraggableHandle(),
                                    song = song,
                                    isDownloaded = rawSongDetails[song.url] != null,
                                    isBeingDragged = isBeingDragged,
                                    onClick = { viewModel.openSongInSetlist(setlistWithSongs, index) }
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
private fun DismissibleSongItem(
    onDismissed: () -> Unit,
    content: @Composable () -> Unit
) {
    val dismissState = rememberSwipeToDismissBoxState()
    // The list keeps the saved state of removed keys, so a re-added song must not start out dismissed.
    LaunchedEffect(Unit) { dismissState.reset() }
    SwipeToDismissBox(
        state = dismissState,
        enableDismissFromEndToStart = false,
        onDismiss = { if (it == SwipeToDismissBoxValue.StartToEnd) onDismissed() },
        backgroundContent = {
            Box(
                modifier = Modifier.fillMaxSize().background(MaterialTheme.colorScheme.errorContainer).padding(horizontal = 24.dp),
                contentAlignment = Alignment.CenterStart
            ) {
                Icon(
                    painter = painterResource(Res.drawable.ic_delete),
                    contentDescription = stringResource(Res.string.setlists_remove_song),
                    tint = MaterialTheme.colorScheme.onErrorContainer
                )
            }
        }
    ) {
        content()
    }
}

/**
 * The lazy list key of a song inside a setlist, encoded as a string so that the list can save it.
 */
private class SetlistItemKey(val string: String?) {

    constructor(setlistId: String, songId: String) : this("$setlistId$TOKEN$songId")

    private val parts = string?.split(TOKEN)?.takeIf { it.size == 2 }

    val setlistId: String? = parts?.first()

    val songId: String? = parts?.last()

    companion object {
        private const val TOKEN = "#*#"
    }
}

private val FAB_CLEARANCE = 88.dp
