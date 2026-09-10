/*
 * This file is part of Campfire.
 * Copyright (c) Pandula Péter 2017-2026.
 * https://github.com/pandulapeter/campfire
 *
 * This Source Code Form is subject to the terms of the Mozilla Public License, v. 2.0.
 * If a copy of the MPL was not distributed with this file, You can obtain one at
 * https://mozilla.org/MPL/2.0/.
 */
package com.pandulapeter.campfire.presentation.ui.screens.songDetails

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.calculateEndPadding
import androidx.compose.foundation.layout.calculateStartPadding
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
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
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.pandulapeter.campfire.chordpro.model.ChordProSong
import com.pandulapeter.campfire.data.model.domain.Song
import com.pandulapeter.campfire.presentation.localization.stringResource
import com.pandulapeter.campfire.presentation.resources.Res
import com.pandulapeter.campfire.presentation.resources.back
import com.pandulapeter.campfire.presentation.resources.ic_back
import com.pandulapeter.campfire.presentation.resources.ic_error
import com.pandulapeter.campfire.presentation.resources.ic_songs
import com.pandulapeter.campfire.presentation.resources.ic_more
import com.pandulapeter.campfire.presentation.resources.ic_next
import com.pandulapeter.campfire.presentation.resources.ic_playlist_add
import com.pandulapeter.campfire.presentation.resources.ic_previous
import com.pandulapeter.campfire.presentation.resources.ic_tune
import com.pandulapeter.campfire.presentation.resources.retry
import com.pandulapeter.campfire.presentation.resources.song_details_add_to_setlist
import com.pandulapeter.campfire.presentation.resources.song_details_display_options
import com.pandulapeter.campfire.presentation.resources.song_details_next_song
import com.pandulapeter.campfire.presentation.resources.song_details_empty
import com.pandulapeter.campfire.presentation.resources.song_details_no_data
import com.pandulapeter.campfire.presentation.resources.song_details_no_data_hint
import com.pandulapeter.campfire.presentation.resources.song_details_previous_song
import com.pandulapeter.campfire.presentation.resources.song_details_song_position
import com.pandulapeter.campfire.presentation.resources.songs_actions
import com.pandulapeter.campfire.presentation.ui.CampfireViewModel
import com.pandulapeter.campfire.presentation.ui.components.CampfireTopAppBar
import com.pandulapeter.campfire.presentation.ui.components.EmptyState
import com.pandulapeter.campfire.presentation.ui.components.SongActionsMenu
import com.pandulapeter.campfire.presentation.ui.components.WindowSize
import com.pandulapeter.campfire.presentation.ui.platform.isDesktopPlatform
import com.pandulapeter.campfire.presentation.ui.navigation.CampfireDestination
import kotlinx.coroutines.launch
import org.jetbrains.compose.resources.painterResource

/**
 * The lyrics (and chords) of a song, or of a setlist's songs in a pager. The transposition and the text size can be
 * adjusted from the app bar: inline steppers when the window is wide enough, otherwise from a bottom sheet behind
 * a single "display options" action, so that the bar does not get crowded. The text size can also be changed with
 * a pinch or Ctrl / Cmd + scroll on the content itself, see [fontScaleGestures].
 *
 * When there is more than one song to page through, a [SongPagerControls] bar under the lyrics offers the same
 * paging as the swipe gesture, along with the name of the setlist and the position of the current song in it.
 *
 * @param settledWidth The width this screen has once the navigation chrome has finished animating. While a
 * navigation transition is running the screen is still as narrow as the rail next to it leaves it, and laying the
 * lyrics out for that would flow them into fewer columns for the duration of the transition, only to reflow them
 * once the rail is gone. Only the width needs this: the bar that could change the height instead of the width is
 * only used on windows narrow enough for a single column.
 */
@OptIn(ExperimentalMaterial3Api::class, ExperimentalMaterial3ExpressiveApi::class)
@Composable
internal fun SongDetailsScreen(
    modifier: Modifier = Modifier,
    viewModel: CampfireViewModel,
    destination: CampfireDestination.SongDetails,
    windowSize: WindowSize,
    settledWidth: Dp,
    contentPadding: PaddingValues,
    onBack: () -> Unit
) {
    val allSongs by viewModel.allSongs.collectAsStateWithLifecycle()
    val setlists by viewModel.setlists.collectAsStateWithLifecycle()
    val songTexts by viewModel.songTexts.collectAsStateWithLifecycle()
    val failedSongFileNames by viewModel.failedSongFileNames.collectAsStateWithLifecycle()
    val transpositions by viewModel.transpositions.collectAsStateWithLifecycle()
    val userPreferences by viewModel.userPreferences.collectAsStateWithLifecycle()
    val fontScale by viewModel.fontScale.collectAsStateWithLifecycle()
    val songs = remember(destination, allSongs) {
        val songsByFileName = allSongs.associateBy { it.fileName }
        destination.songFileNames.mapNotNull { songsByFileName[it] }
    }
    val pagerState = rememberPagerState(initialPage = destination.initialIndex.coerceIn(0, maxOf(0, songs.lastIndex))) { songs.size }
    val currentSong = songs.getOrNull(pagerState.currentPage)
    val canPage = songs.size > 1
    val setlistTitle = destination.setlistFileName?.let { fileName -> setlists.firstOrNull { it.fileName == fileName }?.title }
    val shouldShowChords = userPreferences?.isLyricsOnlyModeEnabled != true
    val isHorizontalFlow = userPreferences?.isHorizontalSectionFlowEnabled == true
    val currentTransposition = currentSong?.let { transpositions[it.fileName, destination.setlistFileName] } ?: 0
    val currentSongText = currentSong?.let { songTexts[it.fileName] }
    // Memoized on the text and the amount: the app bar only needs the resulting key, not the whole parsed song.
    val currentKey = remember(currentSongText, currentTransposition) {
        currentSongText?.let { viewModel.renderSong(it, currentTransposition).metadata.key }
    }

    val scrollBehavior = TopAppBarDefaults.pinnedScrollBehavior()
    val coroutineScope = rememberCoroutineScope()

    LaunchedEffect(currentSong?.fileName) { currentSong?.fileName?.let(viewModel::loadSongContent) }
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
                        painter = painterResource(Res.drawable.ic_back),
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
                if (windowSize.usesInlineSongControls) {
                    AnimatedVisibility(
                        visible = shouldShowChords && currentSong?.hasChords == true && currentSong.fileName in songTexts,
                        enter = fadeIn() + scaleIn(),
                        exit = fadeOut() + scaleOut()
                    ) {
                        TranspositionControls(
                            modifier = Modifier.padding(end = INLINE_CONTROL_SPACING),
                            isCompact = true,
                            transposition = currentTransposition,
                            key = currentKey,
                            onTranspositionChanged = { transposition ->
                                currentSong?.let { viewModel.setTransposition(it.fileName, destination.setlistFileName, transposition) }
                            }
                        )
                    }
                    FontScaleControls(
                        modifier = Modifier.padding(end = INLINE_CONTROL_SPACING),
                        isCompact = true,
                        fontScale = fontScale,
                        onFontScaleAdjusted = viewModel::adjustFontScale,
                        onFontScaleReset = { viewModel.setFontScale(CampfireViewModel.DEFAULT_FONT_SCALE) }
                    )
                }
                IconButton(
                    onClick = {
                        currentSong?.let {
                            viewModel.showDialog(CampfireViewModel.DialogType.SetlistPicker(songFileName = it.fileName, currentSetlistFileName = destination.setlistFileName))
                        }
                    }
                ) {
                    Icon(
                        painter = painterResource(Res.drawable.ic_playlist_add),
                        contentDescription = stringResource(Res.string.song_details_add_to_setlist)
                    )
                }
                if (!windowSize.usesInlineSongControls) {
                    IconButton(
                        onClick = {
                            currentSong?.let {
                                viewModel.showDialog(CampfireViewModel.DialogType.SongDisplayControls(songFileName = it.fileName, setlistFileName = destination.setlistFileName))
                            }
                        }
                    ) {
                        Icon(
                            painter = painterResource(Res.drawable.ic_tune),
                            contentDescription = stringResource(Res.string.song_details_display_options)
                        )
                    }
                }
                // The same actions as the song list's context menu, minus the one the bar next to it already offers.
                currentSong?.let { song ->
                    if (isDesktopPlatform) {
                        SongActionsMenu(
                            viewModel = viewModel,
                            song = song,
                            setlistFileName = destination.setlistFileName,
                            shouldIncludeAddToSetlist = false
                        )
                    } else {
                        IconButton(
                            onClick = {
                                viewModel.showDialog(
                                    CampfireViewModel.DialogType.SongActions(
                                        song = song,
                                        setlistFileName = destination.setlistFileName,
                                        shouldIncludeAddToSetlist = false
                                    )
                                )
                            }
                        ) {
                            Icon(
                                painter = painterResource(Res.drawable.ic_more),
                                contentDescription = stringResource(Res.string.songs_actions)
                            )
                        }
                    }
                }
            }
        )
        val currentFontScale by rememberUpdatedState(fontScale)
        // The paging bar sits below the pager and covers the bottom inset for it, so the pages only keep the
        // padding that is still theirs to apply.
        val layoutDirection = LocalLayoutDirection.current
        val pageContentPadding = if (canPage) {
            PaddingValues(
                start = contentPadding.calculateStartPadding(layoutDirection),
                end = contentPadding.calculateEndPadding(layoutDirection)
            )
        } else {
            contentPadding
        }
        if (songs.isEmpty()) {
            // The library has not been read yet, so there is nothing to page through; a pager with no pages would
            // leave the screen blank under an app bar with no title in it.
            Box(
                modifier = Modifier.weight(1f).fillMaxWidth().padding(contentPadding),
                contentAlignment = Alignment.Center
            ) {
                ContainedLoadingIndicator()
            }
        } else {
            HorizontalPager(
                modifier = Modifier
                    .weight(1f)
                    .fillMaxWidth()
                    .fontScaleGestures(
                        fontScale = { currentFontScale },
                        onFontScaleChanged = viewModel::setFontScale
                    ),
                state = pagerState,
                key = { songs[it].fileName },
                beyondViewportPageCount = 1
            ) { page ->
                val song = songs[page]
                SongDetailsPage(
                    song = song,
                    text = songTexts[song.fileName],
                    hasFailed = song.fileName in failedSongFileNames,
                    transposition = transpositions[song.fileName, destination.setlistFileName],
                    shouldShowChords = shouldShowChords,
                    fontScale = fontScale,
                    isHorizontalFlow = isHorizontalFlow,
                    settledWidth = settledWidth,
                    contentPadding = pageContentPadding,
                    renderSong = viewModel::renderSong,
                    onRetry = { viewModel.loadSongContent(song.fileName) }
                )
            }
        }
        if (canPage) {
            SongPagerControls(
                setlistTitle = setlistTitle,
                currentPage = pagerState.currentPage,
                pageCount = songs.size,
                contentPadding = contentPadding,
                onPageSelected = { page -> coroutineScope.launch { pagerState.animateScrollToPage(page) } }
            )
        }
    }
}

/**
 * The bar under the lyrics that steps through the songs of a setlist without swiping. Between the two buttons it
 * names the setlist being played and how far along it the current song is.
 */
@Composable
private fun SongPagerControls(
    setlistTitle: String?,
    currentPage: Int,
    pageCount: Int,
    contentPadding: PaddingValues,
    onPageSelected: (Int) -> Unit
) = Surface(
    color = MaterialTheme.colorScheme.surfaceContainer
) {
    val layoutDirection = LocalLayoutDirection.current
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(
                start = contentPadding.calculateStartPadding(layoutDirection) + 4.dp,
                end = contentPadding.calculateEndPadding(layoutDirection) + 4.dp,
                bottom = contentPadding.calculateBottomPadding()
            )
            .height(PAGER_CONTROLS_HEIGHT),
        verticalAlignment = Alignment.CenterVertically
    ) {
        IconButton(
            enabled = currentPage > 0,
            onClick = { onPageSelected(currentPage - 1) }
        ) {
            Icon(
                painter = painterResource(Res.drawable.ic_previous),
                contentDescription = stringResource(Res.string.song_details_previous_song)
            )
        }
        // The label takes whatever the two buttons leave. Only the setlist name gives way when that is not enough:
        // the position is short and always worth showing in full.
        Row(
            modifier = Modifier.weight(1f),
            horizontalArrangement = Arrangement.Center,
            verticalAlignment = Alignment.CenterVertically
        ) {
            if (setlistTitle != null) {
                Text(
                    modifier = Modifier.weight(1f, fill = false),
                    text = setlistTitle,
                    style = MaterialTheme.typography.labelLarge,
                    textAlign = TextAlign.Center,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                Text(
                    modifier = Modifier.padding(horizontal = 8.dp),
                    text = LABEL_SEPARATOR,
                    style = MaterialTheme.typography.labelLarge,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            AnimatedContent(
                targetState = currentPage,
                transitionSpec = { fadeIn() togetherWith fadeOut() }
            ) { page ->
                Text(
                    text = stringResource(Res.string.song_details_song_position, page + 1, pageCount),
                    style = MaterialTheme.typography.labelLarge,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    textAlign = TextAlign.Center
                )
            }
        }
        IconButton(
            enabled = currentPage < pageCount - 1,
            onClick = { onPageSelected(currentPage + 1) }
        ) {
            Icon(
                painter = painterResource(Res.drawable.ic_next),
                contentDescription = stringResource(Res.string.song_details_next_song)
            )
        }
    }
}

/**
 * @param text The ChordPro text of the song, null while it is still being read.
 * @param hasFailed Whether the file could not be read. The page then offers a retry rather than a loading indicator
 *   that has nothing left to wait for.
 */
@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
private fun SongDetailsPage(
    song: Song,
    text: String?,
    hasFailed: Boolean,
    transposition: Int,
    shouldShowChords: Boolean,
    fontScale: Float,
    isHorizontalFlow: Boolean,
    settledWidth: Dp,
    contentPadding: PaddingValues,
    renderSong: (text: String, transposition: Int) -> ChordProSong,
    onRetry: () -> Unit
) = AnimatedContent(
    modifier = Modifier.fillMaxSize(),
    targetState = text,
    transitionSpec = { fadeIn() togetherWith fadeOut() },
    contentKey = { it != null }
) { songText ->
    if (songText == null) {
        Box(
            modifier = Modifier.fillMaxSize().padding(contentPadding),
            contentAlignment = Alignment.Center
        ) {
            if (hasFailed) {
                EmptyState(
                    icon = painterResource(Res.drawable.ic_error),
                    title = stringResource(Res.string.song_details_no_data),
                    hint = stringResource(Res.string.song_details_no_data_hint),
                    actionText = stringResource(Res.string.retry),
                    onAction = onRetry
                )
            } else {
                ContainedLoadingIndicator()
            }
        }
    } else {
        val layoutDirection = LocalLayoutDirection.current
        // Keyed on the text and the transposition, so a long song is not parsed again on every recomposition.
        val renderedSong = remember(songText, transposition) { renderSong(songText, transposition) }
        if (renderedSong.blocks.isEmpty()) {
            // The file exists and could be read, it just has nothing in it yet - a newly created song, typically.
            Box(
                modifier = Modifier.fillMaxSize().padding(contentPadding),
                contentAlignment = Alignment.Center
            ) {
                EmptyState(
                    icon = painterResource(Res.drawable.ic_songs),
                    title = stringResource(Res.string.song_details_empty)
                )
            }
            return@AnimatedContent
        }
        val topPadding = 8.dp
        val bottomPadding = contentPadding.calculateBottomPadding() + 32.dp
        // Shared with the lyrics, whose section headers stick to the top of it while they are scrolled through.
        val scrollState = rememberScrollState()
        // The lyrics scroll, so they need to be told from the outside how much room there is for them without
        // scrolling: that is what decides how many columns they are flowed into.
        BoxWithConstraints(
            modifier = Modifier.fillMaxSize()
        ) {
            SongLyrics(
                modifier = Modifier
                    .fillMaxSize()
                    .verticalScroll(scrollState)
                    .padding(
                        start = contentPadding.calculateStartPadding(layoutDirection) + 16.dp,
                        end = contentPadding.calculateEndPadding(layoutDirection) + 16.dp,
                        top = topPadding,
                        bottom = bottomPadding
                    ),
                song = renderedSong,
                availableHeight = maxHeight - topPadding - bottomPadding,
                // The pages fill the screen, so whatever the screen is still missing this layout is missing too.
                extraWidth = (settledWidth - maxWidth).coerceAtLeast(0.dp),
                shouldShowChords = shouldShowChords,
                fontScale = fontScale,
                isHorizontalFlow = isHorizontalFlow,
                scrollState = scrollState,
                topInset = topPadding
            )
        }
    }
}

private const val LABEL_SEPARATOR = "·"
private val PAGER_CONTROLS_HEIGHT = 48.dp
private val INLINE_CONTROL_SPACING = 8.dp
