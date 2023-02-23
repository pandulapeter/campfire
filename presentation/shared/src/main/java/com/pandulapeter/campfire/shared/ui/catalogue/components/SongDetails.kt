package com.pandulapeter.campfire.shared.ui.catalogue.components

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
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
import androidx.compose.ui.unit.dp
import com.pandulapeter.campfire.data.model.domain.RawSongDetails
import com.pandulapeter.campfire.data.model.domain.Setlist
import com.pandulapeter.campfire.data.model.domain.Song
import com.pandulapeter.campfire.shared.ui.CampfireViewModelStateHolder
import com.pandulapeter.campfire.shared.ui.catalogue.resources.CampfireIcons
import com.pandulapeter.campfire.shared.ui.catalogue.resources.CampfireStrings

@Composable
internal fun SongDetailsScreen(
    modifier: Modifier = Modifier,
    uiStrings: CampfireStrings,
    stateHolder: CampfireViewModelStateHolder,
    songDetailsScreenData: SongDetailsScreenData?,
    rawSongDetailsMap: Map<String, RawSongDetails>?,
    setlists: List<Setlist>,
    onSongClosed: () -> Unit
) = Column(
    modifier = modifier.fillMaxSize()
) {
    // TODO: Add pager component
    val currentSong = when (songDetailsScreenData) {
        is SongDetailsScreenData.SetlistData -> songDetailsScreenData.songs[songDetailsScreenData.initiallySelectedSongIndex]
        is SongDetailsScreenData.SongData -> songDetailsScreenData.song
        null -> null
    }
    val rawSongDetails = currentSong?.url?.let { songUrl -> rawSongDetailsMap?.get(songUrl) }
    TopAppBar(
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
                                currentSetlistId = (songDetailsScreenData as? SongDetailsScreenData.SetlistData)?.setlistId
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
            text = rawSongDetails.rawData
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