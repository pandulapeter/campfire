package com.pandulapeter.campfire.shared.ui.screenComponents.songs

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.Surface
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.pandulapeter.campfire.data.model.domain.RawSongDetails
import com.pandulapeter.campfire.data.model.domain.Song
import com.pandulapeter.campfire.data.model.domain.UserPreferences
import com.pandulapeter.campfire.domain.api.useCases.NormalizeTextUseCase
import com.pandulapeter.campfire.shared.ui.catalogue.components.HeaderItem
import com.pandulapeter.campfire.shared.ui.catalogue.components.SongItem
import com.pandulapeter.campfire.shared.ui.catalogue.resources.CampfireStrings
import org.koin.java.KoinJavaComponent.get

@OptIn(ExperimentalFoundationApi::class)
@Composable
fun SongsContentList(
    normalizeText: NormalizeTextUseCase = get(NormalizeTextUseCase::class.java),
    modifier: Modifier = Modifier,
    uiStrings: CampfireStrings,
    state: LazyListState,
    sortingMode: UserPreferences.SortingMode?,
    shouldUseHeaders: Boolean,
    songs: List<Song>,
    rawSongDetails: Map<String, RawSongDetails>,
    onSongClicked: (Song) -> Unit
) = LazyColumn(
    modifier = modifier,
    state = state,
    contentPadding = PaddingValues(vertical = 8.dp)
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
            if (shouldUseHeaders) {
                val previousSong = if (index == 0) null else songs[index - 1]
                when (sortingMode ?: UserPreferences.SortingMode.BY_ARTIST) {
                    UserPreferences.SortingMode.BY_TITLE -> {
                        val firstCharacter = normalizeText(song.title.take(1))
                        val previousFirstCharacter = normalizeText(previousSong?.title?.take(1).orEmpty())
                        if (firstCharacter != previousFirstCharacter) {
                            if (!firstCharacter.first().isLetter() && previousFirstCharacter.firstOrNull()?.isLetter() != true) {
                                if (previousSong == null) {
                                    stickyHeader { StickyHeaderItem(uiStrings.songsUnsortedLabel) }
                                }
                            } else {
                                stickyHeader { StickyHeaderItem(normalizeText(song.title.take(1)).uppercase()) }
                            }
                        }
                    }
                    UserPreferences.SortingMode.BY_ARTIST -> {
                        if (normalizeText(song.artist) != normalizeText(previousSong?.artist.orEmpty())) {
                            stickyHeader { StickyHeaderItem(song.artist) }
                        }
                    }
                }
            }
            item(
                key = "song_${song.id}"
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