package com.pandulapeter.campfire.shared.ui.catalogue.components

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.gestures.snapping.rememberSnapFlingBehavior
import androidx.compose.foundation.layout.Column
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
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.unit.dp
import com.pandulapeter.campfire.data.model.domain.RawSongDetails
import com.pandulapeter.campfire.data.model.domain.Setlist
import com.pandulapeter.campfire.data.model.domain.Song
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
    setlists: List<Setlist>,
    onSongClosed: () -> Unit
) {
    LazyRow(
        modifier = modifier.fillMaxSize(),
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

private val chordRegex = Regex("\\[(.*?)[]]")

@Composable
private fun SongDetailsPage(
    modifier: Modifier = Modifier,
    uiStrings: CampfireStrings,
    stateHolder: CampfireViewModelStateHolder,
    currentSong: Song?,
    rawSongDetails: RawSongDetails?,
    setlistId: String?,
    setlists: List<Setlist>,
    onSongClosed: () -> Unit
) = Column(
    modifier = modifier
) {
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
        Text(
            modifier = Modifier.fillMaxWidth().verticalScroll(rememberScrollState()).padding(16.dp),
            text = buildAnnotatedString {
                append(rawSongDetails.rawData)
                chordRegex.findAll(rawSongDetails.rawData).forEach { result ->
                    addStyle(SpanStyle(CampfireColors.colorCampfireOrange), result.range.first, result.range.last + 1)
                }
            }
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