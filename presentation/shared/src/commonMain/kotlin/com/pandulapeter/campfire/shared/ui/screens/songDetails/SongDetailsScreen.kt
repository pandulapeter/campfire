package com.pandulapeter.campfire.shared.ui.screens.songDetails

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.calculateEndPadding
import androidx.compose.foundation.layout.calculateStartPadding
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.ContainedLoadingIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.pandulapeter.campfire.data.model.domain.RawSongDetails
import com.pandulapeter.campfire.data.model.domain.Song
import com.pandulapeter.campfire.data.model.domain.TranspositionKey
import com.pandulapeter.campfire.shared.resources.Res
import com.pandulapeter.campfire.shared.resources.back
import com.pandulapeter.campfire.shared.resources.song_details_add_to_setlist
import com.pandulapeter.campfire.shared.resources.song_details_transpose_down
import com.pandulapeter.campfire.shared.resources.song_details_transpose_reset
import com.pandulapeter.campfire.shared.resources.song_details_transpose_up
import com.pandulapeter.campfire.shared.ui.CampfireViewModel
import com.pandulapeter.campfire.shared.ui.components.CampfireTopAppBar
import com.pandulapeter.campfire.shared.ui.navigation.CampfireDestination
import com.pandulapeter.campfire.shared.ui.theme.CampfireIcons
import com.pandulapeter.campfire.shared.localization.stringResource

@OptIn(ExperimentalMaterial3Api::class, ExperimentalMaterial3ExpressiveApi::class)
@Composable
internal fun SongDetailsScreen(
    modifier: Modifier = Modifier,
    viewModel: CampfireViewModel,
    destination: CampfireDestination.SongDetails,
    contentPadding: PaddingValues,
    onBack: () -> Unit
) {
    val allSongs by viewModel.allSongs.collectAsStateWithLifecycle()
    val rawSongDetails by viewModel.rawSongDetails.collectAsStateWithLifecycle()
    val transpositions by viewModel.transpositions.collectAsStateWithLifecycle()
    val userPreferences by viewModel.userPreferences.collectAsStateWithLifecycle()
    val songs = remember(destination, allSongs) {
        val songsById = allSongs.associateBy { it.id }
        destination.songIds.mapNotNull { songsById[it] }
    }
    val pagerState = rememberPagerState(initialPage = destination.initialIndex.coerceIn(0, maxOf(0, songs.lastIndex))) { songs.size }
    val currentSong = songs.getOrNull(pagerState.currentPage)
    val shouldShowChords = userPreferences?.isLyricsOnlyModeEnabled != true
    val currentTransposition = currentSong?.let { transpositions[TranspositionKey(it.id, destination.setlistId)] } ?: 0

    val scrollBehavior = TopAppBarDefaults.pinnedScrollBehavior()

    LaunchedEffect(currentSong?.url) { currentSong?.let(viewModel::loadSongDetails) }
    // Every page scrolls on its own, so the app bar's notion of "content scrolled underneath" restarts per page.
    LaunchedEffect(pagerState.currentPage) { scrollBehavior.state.contentOffset = 0f }

    Column(
        modifier = modifier.fillMaxSize().nestedScroll(scrollBehavior.nestedScrollConnection)
    ) {
        CampfireTopAppBar(
            scrollBehavior = scrollBehavior,
            navigationIcon = {
                IconButton(onClick = onBack) {
                    Icon(
                        imageVector = CampfireIcons.back,
                        contentDescription = stringResource(Res.string.back)
                    )
                }
            },
            title = {
                AnimatedContent(
                    targetState = currentSong,
                    transitionSpec = { fadeIn() togetherWith fadeOut() }
                ) { song ->
                    Column {
                        Text(
                            text = song?.title.orEmpty(),
                            style = MaterialTheme.typography.titleMedium,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                        Text(
                            text = song?.artist.orEmpty(),
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                    }
                }
            },
            actions = {
                AnimatedVisibility(
                    visible = shouldShowChords && currentSong?.hasChords == true && currentSong.url in rawSongDetails,
                    enter = fadeIn() + scaleIn(),
                    exit = fadeOut() + scaleOut()
                ) {
                    TranspositionControls(
                        transposition = currentTransposition,
                        onTranspositionChanged = { transposition ->
                            currentSong?.let { viewModel.setTransposition(it.id, destination.setlistId, transposition) }
                        }
                    )
                }
                IconButton(
                    onClick = {
                        currentSong?.let {
                            viewModel.showDialog(CampfireViewModel.DialogType.SetlistPicker(songId = it.id, currentSetlistId = destination.setlistId))
                        }
                    }
                ) {
                    Icon(
                        imageVector = CampfireIcons.playlistAdd,
                        contentDescription = stringResource(Res.string.song_details_add_to_setlist)
                    )
                }
            }
        )
        HorizontalPager(
            modifier = Modifier.fillMaxSize(),
            state = pagerState,
            key = { songs[it].id },
            beyondViewportPageCount = 1
        ) { page ->
            val song = songs[page]
            SongDetailsPage(
                song = song,
                rawSongDetails = rawSongDetails[song.url],
                transposition = transpositions[TranspositionKey(song.id, destination.setlistId)] ?: 0,
                shouldShowChords = shouldShowChords,
                contentPadding = contentPadding,
                transpose = viewModel::transpose
            )
        }
    }
}

@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
private fun SongDetailsPage(
    song: Song,
    rawSongDetails: RawSongDetails?,
    transposition: Int,
    shouldShowChords: Boolean,
    contentPadding: PaddingValues,
    transpose: (rawData: String, transposition: Int) -> String
) = AnimatedContent(
    modifier = Modifier.fillMaxSize(),
    targetState = rawSongDetails,
    transitionSpec = { fadeIn() togetherWith fadeOut() },
    contentKey = { it != null }
) { details ->
    if (details == null) {
        Box(
            modifier = Modifier.fillMaxSize().padding(contentPadding),
            contentAlignment = Alignment.Center
        ) {
            ContainedLoadingIndicator()
        }
    } else {
        val layoutDirection = LocalLayoutDirection.current
        val transposedRawData = remember(details.rawData, transposition, shouldShowChords) {
            if (shouldShowChords && song.hasChords) transpose(details.rawData, transposition) else details.rawData
        }
        SongLyrics(
            modifier = Modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(
                    start = contentPadding.calculateStartPadding(layoutDirection) + 16.dp,
                    end = contentPadding.calculateEndPadding(layoutDirection) + 16.dp,
                    top = 8.dp,
                    bottom = contentPadding.calculateBottomPadding() + 32.dp
                ),
            rawData = transposedRawData,
            shouldShowChords = shouldShowChords
        )
    }
}

@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
private fun TranspositionControls(
    transposition: Int,
    onTranspositionChanged: (Int) -> Unit
) = Row(
    verticalAlignment = Alignment.CenterVertically
) {
    IconButton(
        enabled = transposition > CampfireViewModel.MIN_TRANSPOSITION,
        onClick = { onTranspositionChanged(transposition - 1) }
    ) {
        Icon(
            imageVector = CampfireIcons.subtract,
            contentDescription = stringResource(Res.string.song_details_transpose_down)
        )
    }
    val color by animateColorAsState(
        if (transposition == 0) MaterialTheme.colorScheme.onSurface else MaterialTheme.colorScheme.primary,
        MaterialTheme.motionScheme.defaultEffectsSpec()
    )
    AnimatedContent(
        targetState = transposition,
        transitionSpec = { fadeIn() togetherWith fadeOut() }
    ) { value ->
        Text(
            modifier = Modifier
                .defaultMinSize(minWidth = 32.dp)
                .clickable(enabled = value != 0, onClickLabel = stringResource(Res.string.song_details_transpose_reset)) { onTranspositionChanged(0) }
                .padding(vertical = 8.dp),
            text = if (value > 0) "+$value" else value.toString(),
            textAlign = TextAlign.Center,
            fontWeight = FontWeight.Bold,
            color = color
        )
    }
    IconButton(
        enabled = transposition < CampfireViewModel.MAX_TRANSPOSITION,
        onClick = { onTranspositionChanged(transposition + 1) }
    ) {
        Icon(
            imageVector = CampfireIcons.add,
            contentDescription = stringResource(Res.string.song_details_transpose_up)
        )
    }
}
