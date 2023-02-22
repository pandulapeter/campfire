package com.pandulapeter.campfire.shared.ui.screenComponents.setlists

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.Surface
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.pandulapeter.campfire.data.model.domain.RawSongDetails
import com.pandulapeter.campfire.data.model.domain.Setlist
import com.pandulapeter.campfire.data.model.domain.Song
import com.pandulapeter.campfire.shared.ui.catalogue.components.HeaderItem
import com.pandulapeter.campfire.shared.ui.catalogue.components.SongItem
import com.pandulapeter.campfire.shared.ui.catalogue.resources.CampfireStrings


@OptIn(ExperimentalFoundationApi::class)
@Composable
fun SetlistsContentList(
    modifier: Modifier = Modifier,
    uiStrings: CampfireStrings,
    state: LazyListState,
    songs: List<Song>,
    setlists: List<Setlist>,
    rawSongDetails: Map<String, RawSongDetails>,
    onSongClicked: (Song) -> Unit
) = LazyColumn(
    modifier = modifier.fillMaxWidth(),
    state = state,
    contentPadding = PaddingValues(vertical = 8.dp)
) {
    if (setlists.isEmpty()) {
        item(key = "header_no_setlists") {
            HeaderItem(
                modifier = Modifier.animateItemPlacement().padding(horizontal = 8.dp),
                text = uiStrings.setlistsNoData,
                shouldUseLargePadding = false
            )
        }
    } else {
        if (songs.isEmpty()) {
            item(key = "header_no_songs") {
                HeaderItem(
                    modifier = Modifier.animateItemPlacement().padding(horizontal = 8.dp),
                    text = uiStrings.songsNoData,
                    shouldUseLargePadding = false
                )
            }
        } else {
            setlists.sortedByDescending { it.priority }.forEach { setlist ->
                stickyHeader { StickyHeaderItem(setlist.title) }
                setlist.songIds.forEach { songId ->
                    songs.firstOrNull { it.id == songId }?.let { song ->
                        item(
                            key = "song_${setlist.id}_${song.id}"
                        ) {
                            SongItem(
                                modifier = Modifier.animateItemPlacement(),
                                uiStrings = uiStrings,
                                song = song,
                                isDownloaded = rawSongDetails[song.url] != null,
                                onSongClicked = onSongClicked
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun StickyHeaderItem(text: String) = Surface(
    modifier = Modifier.padding(horizontal = 8.dp).padding(bottom = 8.dp),
    elevation = 6.dp,
    shape = RoundedCornerShape(8.dp),
) {
    HeaderItem(
        text = text,
        shouldUseLargePadding = false
    )
}