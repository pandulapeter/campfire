package com.pandulapeter.campfire.shared.ui.catalogue.components

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.snapping.rememberSnapFlingBehavior
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.CircularProgressIndicator
import androidx.compose.material.Icon
import androidx.compose.material.IconButton
import androidx.compose.material.MaterialTheme
import androidx.compose.material.Text
import androidx.compose.material.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.pandulapeter.campfire.data.model.domain.RawSongDetails
import com.pandulapeter.campfire.data.model.domain.Setlist
import com.pandulapeter.campfire.data.model.domain.Song
import com.pandulapeter.campfire.data.model.domain.TranspositionKey
import com.pandulapeter.campfire.shared.ui.CampfireViewModel
import com.pandulapeter.campfire.shared.ui.CampfireViewModelStateHolder
import com.pandulapeter.campfire.shared.ui.catalogue.resources.CampfireIcons
import com.pandulapeter.campfire.shared.ui.catalogue.resources.CampfireStrings
import com.pandulapeter.campfire.shared.ui.catalogue.theme.CampfireColors

@OptIn(ExperimentalFoundationApi::class)
@Composable
internal fun SongDetailsScreen(
    modifier: Modifier = Modifier,
    uiStrings: CampfireStrings,
    lazyListState: LazyListState,
    stateHolder: CampfireViewModelStateHolder,
    songDetailsScreenData: SongDetailsScreenData?,
    rawSongDetailsMap: Map<String, RawSongDetails>?,
    transpositions: Map<TranspositionKey, Int>,
    setlists: List<Setlist>,
    onSongClosed: () -> Unit
) {
    LazyRow(
        modifier = modifier.fillMaxSize().background(MaterialTheme.colors.background),
        state = lazyListState,
        flingBehavior = rememberSnapFlingBehavior(lazyListState = lazyListState)
    ) {
        when (songDetailsScreenData) {
            is SongDetailsScreenData.SetlistData -> {
                items(
                    items = songDetailsScreenData.songs,
                    key = { it.id }
                ) { song ->
                    SongDetailsPage(
                        modifier = Modifier.fillParentMaxSize(),
                        uiStrings = uiStrings,
                        stateHolder = stateHolder,
                        currentSong = song,
                        rawSongDetails = rawSongDetailsMap?.get(song.url),
                        transposition = transpositions[TranspositionKey(song.id, songDetailsScreenData.setlistId)] ?: 0,
                        setlistId = songDetailsScreenData.setlistId,
                        setlists = setlists,
                        onSongClosed = onSongClosed
                    )
                }
            }
            is SongDetailsScreenData.SongData -> {
                item(key = songDetailsScreenData.song.id) {
                    SongDetailsPage(
                        modifier = Modifier.fillParentMaxSize(),
                        uiStrings = uiStrings,
                        stateHolder = stateHolder,
                        currentSong = songDetailsScreenData.song,
                        rawSongDetails = rawSongDetailsMap?.get(songDetailsScreenData.song.url),
                        transposition = transpositions[TranspositionKey(songDetailsScreenData.song.id, null)] ?: 0,
                        setlistId = null,
                        setlists = setlists,
                        onSongClosed = onSongClosed
                    )
                }
            }
            null -> Unit
        }
    }
}

@Composable
private fun SongDetailsPage(
    modifier: Modifier = Modifier,
    uiStrings: CampfireStrings,
    stateHolder: CampfireViewModelStateHolder,
    currentSong: Song?,
    rawSongDetails: RawSongDetails?,
    transposition: Int,
    setlistId: String?,
    setlists: List<Setlist>,
    onSongClosed: () -> Unit
) = Column(
    modifier = modifier
) {
    val shouldShowChords = stateHolder.userPreferences.value?.isLyricsOnlyModeEnabled != true
    TopAppBar(
        modifier = Modifier.fillMaxWidth(),
        navigationIcon = {
            IconButton(
                onClick = onSongClosed
            ) {
                Icon(
                    imageVector = CampfireIcons.close,
                    contentDescription = uiStrings.songsClose
                )
            }
        },
        actions = {
            if (shouldShowChords && currentSong?.hasChords == true && rawSongDetails != null) {
                TranspositionControls(
                    uiStrings = uiStrings,
                    transposition = transposition,
                    onTranspositionChanged = { stateHolder.onTranspositionChanged(currentSong.id, setlistId, it) }
                )
            }
            if (setlists.isNotEmpty()) {
                IconButton(
                    onClick = {
                        currentSong?.id?.let { songId ->
                            stateHolder.onSetlistPickerClicked(
                                songId = songId,
                                currentSetlistId = setlistId
                            )
                        }
                    }
                ) {
                    Icon(
                        imageVector = CampfireIcons.setlists,
                        contentDescription = uiStrings.setlists
                    )
                }
            }
        },
        backgroundColor = MaterialTheme.colors.background,
        title = { Text(text = currentSong?.title.orEmpty()) }
    )
    if (rawSongDetails == null) {
        CircularProgressIndicator(
            modifier = Modifier.padding(16.dp).align(Alignment.CenterHorizontally)
        )
    } else {
        val transposedRawData = remember(rawSongDetails.rawData, transposition, shouldShowChords) {
            if (shouldShowChords) stateHolder.getTransposedRawData(rawSongDetails.rawData, transposition) else rawSongDetails.rawData
        }
        SongLyrics(
            modifier = Modifier.fillMaxWidth().verticalScroll(rememberScrollState()).padding(16.dp),
            uiStrings = uiStrings,
            rawData = transposedRawData,
            shouldShowChords = shouldShowChords
        )
    }
}

@Composable
private fun RowScope.TranspositionControls(
    uiStrings: CampfireStrings,
    transposition: Int,
    onTranspositionChanged: (Int) -> Unit
) = Row(
    modifier = Modifier.align(Alignment.CenterVertically)
) {
    IconButton(
        enabled = transposition > CampfireViewModel.MIN_TRANSPOSITION,
        onClick = { onTranspositionChanged(transposition - 1) }
    ) {
        Icon(
            imageVector = CampfireIcons.subtract,
            contentDescription = uiStrings.songDetailsTransposeDown
        )
    }
    Text(
        modifier = Modifier
            .align(Alignment.CenterVertically)
            .defaultMinSize(minWidth = 32.dp)
            .clickable(enabled = transposition != 0) { onTranspositionChanged(0) }
            .padding(vertical = 8.dp),
        text = if (transposition > 0) "+$transposition" else transposition.toString(),
        textAlign = TextAlign.Center,
        fontWeight = FontWeight.Bold,
        color = if (transposition == 0) MaterialTheme.colors.onSurface else CampfireColors.colorCampfireOrange
    )
    IconButton(
        enabled = transposition < CampfireViewModel.MAX_TRANSPOSITION,
        onClick = { onTranspositionChanged(transposition + 1) }
    ) {
        Icon(
            imageVector = CampfireIcons.add,
            contentDescription = uiStrings.songDetailsTransposeUp
        )
    }
}

sealed class SongDetailsScreenData {

    abstract val songUrl: String

    data class SongData(val song: Song) : SongDetailsScreenData() {
        override val songUrl = song.url
    }

    data class SetlistData(
        val setlistId: String,
        val songs: List<Song>,
        val initiallySelectedSongIndex: Int
    ) : SongDetailsScreenData() {

        override val songUrl = songs[initiallySelectedSongIndex].url
    }
}