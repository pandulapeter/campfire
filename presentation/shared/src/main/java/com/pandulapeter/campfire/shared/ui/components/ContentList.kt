package com.pandulapeter.campfire.shared.ui.components

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.material.MaterialTheme
import androidx.compose.material.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.pandulapeter.campfire.data.model.domain.Collection
import com.pandulapeter.campfire.data.model.domain.Song

@OptIn(ExperimentalFoundationApi::class)
@Composable
internal fun ContentList(
    modifier: Modifier = Modifier,
    collections: List<Collection>,
    songs: List<Song>,
    lazyColumnWrapper: @Composable BoxScope.(LazyListScope.() -> Unit) -> Unit
) = Box(
    modifier = modifier.fillMaxHeight()
) {
    lazyColumnWrapper {
        if (collections.isEmpty() && songs.isEmpty()) {
            item(key = "header_no_data") {
                Header(
                    modifier = Modifier.animateItemPlacement(),
                    text = "No data"
                )
            }
        } else {
            if (collections.isNotEmpty()) {
                item(key = "header_collections") {
                    Header(
                        modifier = Modifier.animateItemPlacement(),
                        text = "Collections (${collections.size})"
                    )
                }
                itemsIndexed(
                    items = collections,
                    key = { _, collection -> "collection_${collection.id}" }
                ) { _, collection ->
                    CollectionItem(
                        modifier = Modifier.animateItemPlacement(),
                        collection = collection
                    )
                }
            }
            if (songs.isNotEmpty()) {
                item(key = "header_songs") {
                    Header(
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
                        song = song
                    )
                }
            }
        }
    }
}

@Composable
private fun CollectionItem(
    modifier: Modifier = Modifier,
    collection: Collection
) = RoundedCard(
    modifier = modifier
) {
    Text(
        modifier = Modifier.background(MaterialTheme.colors.surface).padding(8.dp).fillMaxWidth(),
        text = collection.title
    )
}

@Composable
private fun SongItem(
    modifier: Modifier = Modifier,
    song: Song
) = RoundedCard(
    modifier = modifier
) {
    Text(
        modifier = Modifier.background(MaterialTheme.colors.surface).padding(8.dp).fillMaxWidth(),
        text = "${song.artist} - ${song.title}"
    )
}