package com.pandulapeter.campfire.shared.ui.screenComponents.setlists

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
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
import org.burnoutcrew.reorderable.ReorderableItem
import org.burnoutcrew.reorderable.ReorderableLazyListState
import org.burnoutcrew.reorderable.detectReorderAfterLongPress
import org.burnoutcrew.reorderable.reorderable


@OptIn(ExperimentalFoundationApi::class)
@Composable
fun SetlistsContentList(
    modifier: Modifier = Modifier,
    uiStrings: CampfireStrings,
    state: ReorderableLazyListState,
    songs: List<Song>,
    setlists: List<Setlist>,
    rawSongDetails: Map<String, RawSongDetails>,
    onSongClicked: (Song) -> Unit
) = LazyColumn(
    modifier = modifier.fillMaxWidth()
        .reorderable(state)
        .detectReorderAfterLongPress(state),
    state = state.listState,
    contentPadding = PaddingValues(top = 8.dp, bottom = 80.dp)
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
            setlists.forEach { setlist ->
                stickyHeader { StickyHeaderItem(setlist.title) }
                setlist.songIds.forEach { songId ->
                    songs.firstOrNull { it.id == songId }?.let { song ->
                        val key = SetlistItemKey(
                            setlistId = setlist.id,
                            songId = song.id
                        ).string
                        item(
                            key = key
                        ) {
                            ReorderableItem(state, key = key) { isBeingDragged ->
                                SongItem(
                                    modifier = Modifier.animateItemPlacement(),
                                    uiStrings = uiStrings,
                                    isBeingDragged = isBeingDragged,
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

@JvmInline
value class SetlistItemKey(
    val string: String?
) {
    constructor(
        setlistId: String,
        songId: String
    ) : this(
        "${setlistId}$TOKEN${songId}"
    )

    val setlistId get() = string?.split(TOKEN)?.firstOrNull()

    val songId get() = string?.split(TOKEN)?.lastOrNull()

    companion object {
        private const val TOKEN = "#*#"
    }
}