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

import com.pandulapeter.campfire.presentation.ui.screens.songDetails.stepping.ReadingWindow
import com.pandulapeter.campfire.presentation.ui.screens.songDetails.stepping.RowSnapFlingBehavior
import com.pandulapeter.campfire.presentation.ui.screens.songDetails.stepping.SongStepper
import com.pandulapeter.campfire.presentation.ui.screens.songDetails.stepping.timingIndexAt
import com.pandulapeter.campfire.presentation.ui.songView.SectionMotion
import com.pandulapeter.campfire.presentation.ui.songView.SongLyrics
import com.pandulapeter.campfire.presentation.ui.songView.rememberSongLyricsModel
import com.pandulapeter.campfire.presentation.ui.songView.firstSection.SongPlayingControls
import com.pandulapeter.campfire.presentation.ui.songView.firstSection.SongPlayingTools
import com.pandulapeter.campfire.presentation.ui.songView.model.ChordDiagrams
import com.pandulapeter.campfire.presentation.ui.songView.model.ScaleSuggestions
import com.pandulapeter.campfire.presentation.ui.songView.model.RenderSection
import com.pandulapeter.campfire.presentation.ui.songView.model.SongLyricsInputs
import com.pandulapeter.campfire.presentation.ui.songView.model.buildsModelInPlace
import com.pandulapeter.campfire.presentation.ui.songView.model.prepareSongLyrics
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.ScrollState
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.calculateEndPadding
import androidx.compose.foundation.layout.calculateStartPadding
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.offset
import com.pandulapeter.campfire.chordpro.model.ChordProSong
import com.pandulapeter.campfire.data.model.song.Song
import com.pandulapeter.campfire.data.model.preferences.UserPreferences
import com.pandulapeter.campfire.metronome.api.model.TimeSignature
import com.pandulapeter.campfire.presentation.localization.stringResource
import com.pandulapeter.campfire.presentation.resources.Res
import com.pandulapeter.campfire.presentation.resources.ic_error
import com.pandulapeter.campfire.presentation.resources.ic_songs
import com.pandulapeter.campfire.presentation.resources.retry
import com.pandulapeter.campfire.presentation.resources.song_details_empty
import com.pandulapeter.campfire.presentation.resources.song_details_no_data
import com.pandulapeter.campfire.presentation.resources.song_details_no_data_hint
import com.pandulapeter.campfire.presentation.mapper.toChordNotation
import com.pandulapeter.campfire.presentation.ui.components.DelayedLoadingIndicator
import com.pandulapeter.campfire.presentation.ui.components.EDGE_FADE_SIZE
import com.pandulapeter.campfire.presentation.ui.components.EmptyState
import com.pandulapeter.campfire.presentation.ui.components.EmptyStateAction
import com.pandulapeter.campfire.presentation.ui.components.fadingTopEdge
import com.pandulapeter.campfire.presentation.ui.metronome.SongTiming
import com.pandulapeter.campfire.presentation.ui.playing.withCapo
import com.pandulapeter.campfire.presentation.ui.playing.withTempo
import com.pandulapeter.campfire.presentation.ui.components.bounceVerticalScroll
import com.pandulapeter.campfire.presentation.ui.songLayout.rememberDefaultSectionLabels
import com.pandulapeter.campfire.presentation.ui.tabs.TabReading
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.filterNotNull
import org.jetbrains.compose.resources.painterResource

/**
 * @param text The ChordPro text of the song, null while it is still being read.
 * @param buildsModelInPlace Whether the first rendering of the text is built while composing rather than in the
 *   background, see [buildsModelInPlace] and [rememberSongLyricsModel].
 * @param isViewportSettled Whether the app bar and the metronome panel above the pager are at rest, read while
 *   composing: the grid is decided against the height the page has then, see [settledHeight].
 * @param followsViewport Whether the page follows the viewport's height frame by frame: the page being read and the one
 *   a swipe is heading to; the others keep the height it last rested at, see [pageViewportHeight].
 * @param hasFailed Whether the file could not be read. The page then offers a retry rather than a loading indicator
 *   that has nothing left to wait for.
 * @param scrollState Owned by the pager rather than by the page, so that the arrow keys can reach the scroll of the
 *   song being read, and so that a page keeps where it was left while its text is loaded again.
 * @param tempoOverride The tempo the song plays at here where that is not its own, which the tempo line then shows,
 *   the way the key line shows the transposed key, so that the page never contradicts the metronome.
 * @param transposition The semitones the file's chords are moved by to the ones that sound, the file's own capo counted
 *   in (`SongPlayback.soundingTransposition`): the page names what the band hears on every instrument.
 * @param tabCapo How many of [transposition]'s semitones a tab's frets are not moved by, see `SongPlayback.tabCapoFor`.
 * @param capoOverride The fret this setlist (or this device) capos the song at where that is not its own, shown the
 *   same way as [tempoOverride].
 * @param shapeCapo The fret the Chords section's shapes are fingered above (`SongPlayback.shapeCapoFor`), none on the
 *   keyboard.
 * @param inferredKey The sounding key the song's chords were found in (`InferredSongKeys`), named by the read only line of
 *   a file that names none; null where the playing controls are drawn, which name it themselves.
 * @param playingControls What sets the key, the capo, the tempo and the time signature from the song's own first
 *   section, null in read only mode, see [SongPlayingControls].
 * @param playingTools The tuner and metronome buttons of that section, in read only mode too, see [SongPlayingTools].
 * @param shouldShowCapo False on the keyboard, which takes no capo (`playsWithoutCapo`): neither the page nor its read
 *   only line names a capo then, the file's own included.
 * @param shouldShowTempo False with the metronome switched off, which takes the tempo and the time signature off the page,
 *   the changes of them further down included.
 * @param fontScale Read where the lyrics are built rather than passed as a value: a pinch changes it on every frame,
 *   and read here it invalidates only this page's content rather than the screen and the pager around it.
 * @param headedOffset Where the reader is headed in the song, see [SongStepper.headedOffset].
 * @param onTimingChanged Told the stretch of the song [headedOffset] is in whenever it changes, null for its opening:
 *   the page being headed for, rather than the one a finger is still dragging past, is what the click follows.
 * @param chordDiagrams What the song's Chords section is drawn with, null where it has none.
 * @param tabs Who the song's tabs are chosen for and moved to, null until the preferences are read.
 * @param showsDegrees Whether the Chords section labels each diagram with the step of the key its chord stands on.
 */
@Composable
internal fun SongDetailsPage(
    modifier: Modifier = Modifier,
    song: Song,
    scrollState: ScrollState,
    flingBehavior: RowSnapFlingBehavior,
    text: String?,
    buildsModelInPlace: Boolean,
    isViewportSettled: () -> Boolean,
    followsViewport: Boolean,
    hasFailed: Boolean,
    transposition: Int,
    tabCapo: Int,
    tempoOverride: Int?,
    capoOverride: Int?,
    shapeCapo: Int,
    inferredKey: String?,
    playingControls: SongPlayingControls?,
    playingTools: SongPlayingTools?,
    shouldShowChords: Boolean,
    shouldShowCapo: Boolean,
    shouldShowTempo: Boolean,
    shouldNumberSections: Boolean,
    fontScale: () -> Float,
    keepsStepButtonInset: Boolean,
    foldedSections: Set<String>,
    onFoldToggled: (key: String) -> Unit,
    chordSpelling: UserPreferences.ChordSpelling,
    settledWidth: Dp,
    contentPadding: PaddingValues,
    transposeSong: (text: String, transposition: Int, capo: Int, spelling: UserPreferences.ChordSpelling, tabs: TabReading?) -> ChordProSong,
    notateSong: (song: ChordProSong, spelling: UserPreferences.ChordSpelling) -> ChordProSong,
    onRetry: () -> Unit,
    headedOffset: () -> Int?,
    onTimingChanged: (SongTiming?) -> Unit,
    chordDiagrams: ChordDiagrams?,
    scaleSuggestions: ScaleSuggestions?,
    tabs: TabReading?,
    showsDegrees: Boolean,
) = AnimatedContent(
    modifier = modifier.fillMaxSize(),
    targetState = text,
    transitionSpec = { fadeIn() togetherWith fadeOut() },
    contentKey = { it != null },
) { songText ->
    if (songText == null) {
        Box(
            modifier = Modifier.fillMaxSize().padding(contentPadding),
            contentAlignment = Alignment.Center,
        ) {
            if (hasFailed) {
                EmptyState(
                    icon = painterResource(Res.drawable.ic_error),
                    title = stringResource(Res.string.song_details_no_data),
                    hint = stringResource(Res.string.song_details_no_data_hint),
                    actions = listOf(EmptyStateAction(text = stringResource(Res.string.retry), onClick = onRetry)),
                )
            } else {
                DelayedLoadingIndicator()
            }
        }
    } else {
        val layoutDirection = LocalLayoutDirection.current
        val labels = rememberDefaultSectionLabels(shouldNumberSections)
        val model = rememberSongLyricsModel(
            inputs = SongLyricsInputs(
                text = songText,
                transposition = transposition,
                tabCapo = tabCapo,
                spelling = chordSpelling,
                shouldShowChords = shouldShowChords,
                labels = labels,
                tempoOverride = tempoOverride,
                capoOverride = capoOverride,
                shapeCapo = shapeCapo,
                inferredKey = inferredKey,
                showsTiming = shouldShowTempo,
                chordTuning = chordDiagrams?.tuning,
                tabs = tabs,
                suggestsScales = scaleSuggestions != null,
                showsDegrees = showsDegrees,
            ),
            buildsInPlace = buildsModelInPlace,
        ) { inputs, searchesShapes ->
            // The scales are read from the song as its file capos it, the capo saying only which shapes the file is written
            // in; the page names the capo it is played at.
            val transposed = transposeSong(inputs.text, inputs.transposition, inputs.tabCapo, inputs.spelling, inputs.tabs)
            prepareSongLyrics(
                song = notateSong(transposed.withTempo(inputs.tempoOverride).withCapo(inputs.capoOverride), inputs.spelling).let { song ->
                    inputs.inferredKey?.takeIf { song.metadata.key.isNullOrBlank() }?.let { song.copy(metadata = song.metadata.copy(key = it)) } ?: song
                },
                standardSong = transposed,
                shapeCapo = inputs.shapeCapo,
                shouldShowChords = inputs.shouldShowChords,
                labels = inputs.labels,
                showsTiming = inputs.showsTiming,
                chordTuning = inputs.chordTuning,
                notation = inputs.spelling.notation.toChordNotation(),
                searchesShapes = searchesShapes,
                suggestsScales = inputs.suggestsScales,
                showsDegrees = inputs.showsDegrees,
            )
        }
        if (model == null) {
            // A page beside the one being read whose first rendering is still being built in the background. Nobody is
            // looking at it, so it appears in one frame once it is there, the way data arriving does.
            Box(
                modifier = Modifier.fillMaxSize().padding(contentPadding),
                contentAlignment = Alignment.Center,
            ) {
                DelayedLoadingIndicator()
            }
            return@AnimatedContent
        }
        if (model.song.blocks.isEmpty()) {
            // The file exists and could be read, it just has nothing in it yet - a newly created song, typically.
            Box(
                modifier = Modifier.fillMaxSize().padding(contentPadding),
                contentAlignment = Alignment.Center,
            ) {
                EmptyState(
                    icon = painterResource(Res.drawable.ic_songs),
                    title = stringResource(Res.string.song_details_empty),
                )
            }
            return@AnimatedContent
        }
        val topPadding = PAGE_TOP_PADDING
        val topPaddingPx = with(LocalDensity.current) { topPadding.roundToPx() }
        LaunchedEffect(flingBehavior) { flingBehavior.keepReaderInPlace() }
        val timings = remember(model.sections) { model.sections.filterIsInstance<RenderSection.Timing>() }
        val latestHeadedOffset by rememberUpdatedState(headedOffset)
        val latestOnTimingChanged by rememberUpdatedState(onTimingChanged)
        LaunchedEffect(timings, flingBehavior, scrollState) {
            snapshotFlow { latestHeadedOffset()?.let { offset -> timingIndexAt(offset, flingBehavior.rows, scrollState.maxValue) } }
                .filterNotNull()
                .distinctUntilChanged()
                .collect { index -> latestOnTimingChanged(timings.getOrNull(index)?.toSongTiming(index)) }
        }
        val bottomPadding = contentPadding.calculateBottomPadding() + PAGE_BOTTOM_PADDING
        // The lyrics scroll, so they need to be told from the outside how much room there is for them without
        // scrolling: that is what decides how many columns they are flowed into.
        BoxWithConstraints(
            modifier = Modifier.fillMaxSize()
        ) {
            val currentFontScale = fontScale()
            // The grid is decided against the height the page has with the app bar and the metronome panel at rest,
            // since every pixel either moves would otherwise search it again, on all three composed pages; the rows
            // are still padded to the live height, so what is on screen follows the bar frame by frame. Reading whether
            // they are at rest is what recomposes this once they come to rest.
            val settled = remember { SettledHeight(maxHeight) }
            settled.height = settledHeight(previous = settled.height, live = maxHeight, isSettled = isViewportSettled())
            // The pages beside the one being read are off screen, so they keep the height the viewport last rested at
            // rather than laying themselves out again on every frame of it moving.
            val viewportHeight = pageViewportHeight(live = maxHeight, settled = settled.height, followsViewport = followsViewport)
            // A single change - a window maximised, a stepper tapped - springs the sections to their new place; a burst
            // of them, from a pinch or a window edge being dragged, is followed, gliding only over the grid's jumps. The
            // viewport's height is one of them, since every row is padded to it: a bottom edge dragged, the short window's
            // title row collapsing over the frames of a scroll and the metronome panel opening or closing above the
            // pager are bursts of height changes alike, and the grid they settle into glides too.
            val isChangingContinuously = rememberContinuousChange(maxWidth, viewportHeight, currentFontScale)
            // Of those bursts only a pinch resizes the playing controls on every frame; the others leave them their sizes,
            // so they still travel where one of them rearranges them.
            val isTextScaling = rememberContinuousChange(currentFontScale)
            val density = LocalDensity.current
            // Computed here rather than in onRowsPlaced, which would otherwise read the constraints of this scope and be a
            // new lambda on every frame of the height, recomposing the lyrics of a page that does not follow it.
            val viewport = with(density) { IntSize(maxWidth.roundToPx(), viewportHeight.roundToPx()) }
            // Both are read through a state rather than captured, since a lambda capturing the value itself is a new one
            // on every frame of the height changing: only then does the song skip being composed again on those frames,
            // the layout reading the live height where it measures and the rows reading the viewport as they are placed.
            val latestViewport by rememberUpdatedState(viewport)
            val liveViewportHeight by rememberUpdatedState(viewportHeight)
            val lyricsLineHeight = MaterialTheme.typography.bodyLarge.lineHeight
            val readingWindow = with(density) {
                ReadingWindow(
                    top = EDGE_FADE_SIZE.roundToPx(),
                    bottom = contentPadding.calculateBottomPadding().roundToPx(),
                    end = (bottomPadding - contentPadding.calculateBottomPadding()).roundToPx(),
                    // A chorded line is a line of chords over a line of lyrics, and it is the last of those that the eye
                    // looks for at the top of the next page.
                    overlap = (lyricsLineHeight * currentFontScale).roundToPx() * 2,
                )
            }
            SideEffect { flingBehavior.readingWindow = readingWindow }
            SongLyrics(
                modifier = Modifier
                    .fillMaxSize()
                    .fadingTopEdge(
                        scrolled = { flingBehavior.rows.scrolledIntoRow(scrollState.value) },
                        backgroundColor = MaterialTheme.colorScheme.background,
                    )
                    .bounceVerticalScroll(state = scrollState, flingBehavior = flingBehavior)
                    .padding(
                        start = contentPadding.calculateStartPadding(layoutDirection) + PAGE_HORIZONTAL_PADDING,
                        end = contentPadding.calculateEndPadding(layoutDirection) + PAGE_HORIZONTAL_PADDING,
                        top = topPadding,
                        bottom = bottomPadding,
                    ),
                model = model,
                availableHeight = settled.height - topPadding - bottomPadding,
                // The pages fill the screen, so whatever the screen is still missing this layout is missing too.
                extraWidth = (settledWidth - maxWidth).coerceAtLeast(0.dp),
                sectionMotion = if (isChangingContinuously) SectionMotion.GLIDE else SectionMotion.SPRING,
                isTextScaling = isTextScaling,
                fontScale = currentFontScale,
                foldedSections = foldedSections,
                onFoldToggled = onFoldToggled,
                playingControls = playingControls,
                playingTools = playingTools,
                // Read only, the page is the one place left that says how the song is played, so a capo of none and the
                // tempo and the time the click starts at are said rather than left to be guessed from nothing.
                readsDefaults = playingControls == null,
                shouldShowCapo = shouldShowCapo,
                shouldShowTempo = shouldShowTempo,
                // The padding is inside the scroll, so a row is at the top of the viewport once the song is scrolled by
                // its position plus the padding above it - all but the first, which is read at the top of the song.
                onRowsPlaced = { rows ->
                    flingBehavior.onRowsPlaced(
                        placedRows = rows.belowPadding(topPaddingPx),
                        viewport = latestViewport,
                    )
                },
                rowViewportHeight = { liveViewportHeight },
                rowViewportTopPadding = topPadding,
                rowViewportBottomPadding = bottomPadding,
                // The buttons are as far from the end of the screen as the text is, so the text only has to leave
                // them their own width and a gap.
                stepButtonInset = STEP_BUTTON_SIZE + STEP_BUTTON_GAP,
                keepsStepButtonInset = keepsStepButtonInset,
                canCutSections = true,
                chordDiagrams = chordDiagrams,
                scaleSuggestions = scaleSuggestions,
            )
        }
    }
}

/**
 * True while [values] keep changing: from their second change within [CONTINUOUS_CHANGE_MILLIS] of the one before it
 * until they have held still for that long. The first change of a burst is indistinguishable from a single one, so it
 * still counts as discrete.
 */
@Composable
private fun rememberContinuousChange(vararg values: Any): Boolean {
    var isContinuous by remember { mutableStateOf(false) }
    val tracker = remember { ContinuousChangeTracker() }
    LaunchedEffect(*values) {
        if (tracker.isFirst) {
            tracker.isFirst = false
            return@LaunchedEffect
        }
        // A change that arrives while the delay of the one before it was still running, which this restart cancelled,
        // is the second of a burst.
        if (tracker.isRecent) isContinuous = true
        tracker.isRecent = true
        delay(CONTINUOUS_CHANGE_MILLIS)
        tracker.isRecent = false
        isContinuous = false
    }
    return isContinuous
}

/** The height a page's grid is decided against: the [live] one while the viewport is settled, the [previous] one otherwise. */
internal fun settledHeight(previous: Dp, live: Dp, isSettled: Boolean) = if (isSettled) live else previous

/** The height a page pads its rows to: the [live] one where it [followsViewport], the [settled] one otherwise. */
internal fun pageViewportHeight(live: Dp, settled: Dp, followsViewport: Boolean) = if (followsViewport) live else settled

/**
 * The page's height when the viewport was last at rest. Not state: it is written while composing, and that composition
 * is the one that reads it.
 */
private class SettledHeight(var height: Dp)

/** What [rememberContinuousChange] remembers between changes. Not state, since only its effect reads it. */
private class ContinuousChangeTracker {
    var isFirst = true
    var isRecent = false
}

private val PAGE_BOTTOM_PADDING = 16.dp

private const val CONTINUOUS_CHANGE_MILLIS = 200L

/** The stretch after the [index]-th change of a song, as its line on the page reads it and as the click plays it. */
private fun RenderSection.Timing.toSongTiming(index: Int) = SongTiming(
    index = index,
    bpm = tempo?.toIntOrNull(),
    timeSignature = TimeSignature.parse(time) ?: TimeSignature.COMMON_TIME,
)
