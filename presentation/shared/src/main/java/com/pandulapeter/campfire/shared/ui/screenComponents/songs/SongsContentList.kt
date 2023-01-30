package com.pandulapeter.campfire.shared.ui.screenComponents.songs

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.pandulapeter.campfire.data.model.domain.Song
import com.pandulapeter.campfire.data.model.domain.UserPreferences
import com.pandulapeter.campfire.shared.ui.catalogue.components.HeaderItem
import com.pandulapeter.campfire.shared.ui.catalogue.components.SongItem
import com.pandulapeter.campfire.shared.ui.catalogue.resources.CampfireStrings

@OptIn(ExperimentalFoundationApi::class)
@Composable
fun SongsContentList(
    modifier: Modifier = Modifier,
    uiStrings: CampfireStrings,
    state: LazyListState,
    sortingMode: UserPreferences.SortingMode = UserPreferences.SortingMode.BY_ARTIST,
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
                text = uiStrings.songsNoData
            )
        }
    }
    if (songs.isNotEmpty()) {
        songs.forEachIndexed { index, song ->
            val previousSong = if (index == 0) null else songs[index - 1]
            when (sortingMode) {
                UserPreferences.SortingMode.BY_TITLE -> {
                    if (song.title.firstOrNull() != previousSong?.title?.firstOrNull()) {
                        val text = song.title.firstOrNull()?.uppercase().orEmpty()
                        item(
                            key = "header_${text}"
                        ) {
                            HeaderItem(
                                modifier = Modifier.animateItemPlacement(),
                                text = text
                            )
                        }
                    }
                }
                UserPreferences.SortingMode.BY_ARTIST -> {
                    if (song.artist != previousSong?.artist) {
                        item(
                            key = "header_${song.artist}"
                        ) {
                            HeaderItem(
                                modifier = Modifier.animateItemPlacement(),
                                text = song.artist
                            )
                        }
                    }
                }
            }
            item(
                key = "song_${song.id}"
            ) {
                SongItem(
                    modifier = Modifier.animateItemPlacement(),
                    song = song,
                    onSongClicked = onSongClicked
                )
            }
        }
    }
    item(key = "spacer") {
        Spacer(
            modifier = Modifier.height(8.dp)
        )
    }
}