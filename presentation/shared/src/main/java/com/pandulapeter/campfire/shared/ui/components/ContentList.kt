package com.pandulapeter.campfire.shared.ui.components

import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.pandulapeter.campfire.data.model.domain.Collection
import com.pandulapeter.campfire.data.model.domain.Song

@Composable
internal fun ContentList(
    modifier: Modifier = Modifier,
    collections: List<Collection>,
    songs: List<Song>
) = LazyColumn(
    modifier = modifier.fillMaxHeight()
) {
    if (collections.isEmpty() && songs.isEmpty()) {
        item {
            Header(text = "No data")
        }
    } else {
        if (collections.isNotEmpty()) {
            item {
                Header(text = "Collections (${collections.size})")
            }
            items(collections.size) {
                CollectionItem(collection = collections[it])
            }
        }
        if (songs.isNotEmpty()) {
            item {
                Header(text = "Songs (${songs.size})")
            }
            items(songs.size) {
                SongItem(song = songs[it])
            }
        }
    }
}

@Composable
private fun CollectionItem(
    modifier: Modifier = Modifier,
    collection: Collection
) = Text(
    modifier = modifier.padding(8.dp).fillMaxWidth(),
    text = collection.title
)

@Composable
private fun SongItem(
    modifier: Modifier = Modifier,
    song: Song
) = Text(
    modifier = modifier.padding(8.dp).fillMaxWidth(),
    text = "${song.artist} - ${song.title}"
)