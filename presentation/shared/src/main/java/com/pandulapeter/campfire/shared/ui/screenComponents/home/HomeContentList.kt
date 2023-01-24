package com.pandulapeter.campfire.shared.ui.screenComponents.home

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.pandulapeter.campfire.data.model.domain.Song
import com.pandulapeter.campfire.shared.ui.catalogue.components.HeaderItem
import com.pandulapeter.campfire.shared.ui.catalogue.components.SongItem

@OptIn(ExperimentalFoundationApi::class)
@Composable
fun HomeContentList(
    modifier: Modifier = Modifier,
    state: LazyListState,
    songs: List<Song>,
    onSongClicked: (Song) -> Unit
) = LazyColumn(
    modifier = modifier,
    state = state
) {
    if (songs.isEmpty()) {
        item(key = "header_no_data") {
            HeaderItem(
                modifier = Modifier.animateItemPlacement(),
                text = "No data"
            )
        }
    }
    if (songs.isNotEmpty()) {
        item(key = "header_songs") {
            HeaderItem(
                modifier = Modifier.animateItemPlacement(),
                text = "Songs (${songs.size})"
            )
        }
        itemsIndexed(
            items = songs,
            key = { _, song -> "song_${song.id}" }
        ) { _, song ->
            SongItem(
                modifier = Modifier.animateItemPlacement(),
                song = song,
                onSongClicked = onSongClicked
            )
        }
    }
    item(key = "spacer") {
        Spacer(
            modifier = Modifier.height(8.dp)
        )
    }
}