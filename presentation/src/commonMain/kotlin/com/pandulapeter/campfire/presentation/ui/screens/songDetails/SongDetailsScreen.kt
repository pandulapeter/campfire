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

import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.MutableTransitionState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.ScrollState
import androidx.compose.foundation.gestures.animateScrollBy
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.calculateEndPadding
import androidx.compose.foundation.layout.calculateStartPadding
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.pager.PagerState
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.runtime.snapshots.SnapshotStateMap
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.platform.LocalWindowInfo
import androidx.compose.ui.Modifier
import androidx.compose.ui.keepScreenOn
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.unit.Dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.pandulapeter.campfire.data.model.domain.Song
import com.pandulapeter.campfire.data.model.domain.UserPreferences
import com.pandulapeter.campfire.metronome.api.model.MetronomePlayback
import com.pandulapeter.campfire.presentation.ui.chords.toChordInstrument
import com.pandulapeter.campfire.presentation.ui.dialogs.DialogType
import com.pandulapeter.campfire.presentation.ui.metronome.SongTiming
import com.pandulapeter.campfire.presentation.ui.CampfireViewModel
import com.pandulapeter.campfire.presentation.ui.components.SHORT_WINDOW_HEIGHT
import com.pandulapeter.campfire.presentation.ui.navigation.CampfireDestination
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch

/**
 * The lyrics (and chords) of a song, or of a setlist's songs in a pager. **How the song is played is set in the song
 * itself**: the key, the capo, the tempo and the time signature are the first section of its own grid, each next to the
 * control that sets it (see [SongPlayingControls]), so the app bar is left with what is about the song rather than about
 * how it is played, and read only mode - performance mode, and a song read from an archived setlist - gets the line of
 * text those four used to be.
 *
 * The text size is the one control that is still the bar's, as a row at the end of its overflow menu
 * ([MenuStepperRow]) - and in performance mode, where it is all that is left, in the bar itself
 * ([showsFontScaleInPerformanceBar]). It can
 * also be changed with a pinch or Ctrl / Cmd + scroll on the content itself, see [fontScaleGestures], and on the
 * desktop and the web with Ctrl / Cmd + plus, minus and zero, which the window answers ([CampfireViewModel.zoomSongText]).
 *
 * When there is more than one song to page through, or the song is read from a setlist of any length, a
 * [SongPagerControls] bar under the lyrics offers the same paging as the swipe gesture, along with the name of the
 * setlist and the position of the current song in it.
 *
 * The arrow keys do both without either gesture, see [songKeyboardShortcuts].
 *
 * @param settledWidth The width this screen has once the navigation chrome has finished animating. While a
 * navigation transition is running the screen is still as narrow as the rail next to it leaves it, and laying the
 * lyrics out for that would flow them into fewer columns for the duration of the transition, only to reflow them
 * once the rail is gone. Only the width needs this: the bar that could change the height instead of the width is
 * only used on windows narrow enough for a single column.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun SongDetailsScreen(
    modifier: Modifier = Modifier,
    viewModel: CampfireViewModel,
    destination: CampfireDestination.SongDetails,
    settledWidth: Dp,
    contentPadding: PaddingValues,
    onBack: () -> Unit,
) {
    val songsByFileName by viewModel.songsByFileName.collectAsStateWithLifecycle()
    val setlists by viewModel.setlists.collectAsStateWithLifecycle()
    val songTexts by viewModel.songTexts.collectAsStateWithLifecycle()
    val transpositions by viewModel.transpositions.collectAsStateWithLifecycle()
    val userPreferences by viewModel.userPreferences.collectAsStateWithLifecycle()
    // Only what is drawn over the screen, which the keys are then meant for instead.
    val visibleDialog by viewModel.visibleDialog.collectAsStateWithLifecycle()
    val isPerformanceModeEnabled by viewModel.isPerformanceModeEnabled.collectAsStateWithLifecycle()
    val isReadOnly = isPerformanceModeEnabled || setlists.any { it.fileName == destination.setlistFileName && it.isArchived }
    val songsBeingRenamed by viewModel.songsBeingRenamed.collectAsStateWithLifecycle()
    val songs = remember(destination, songsByFileName, songsBeingRenamed) {
        // A song whose file is being renamed is still this screen's song. The library drops the old name as the file
        // moves and the back stack is rewritten a few writes later, and in between the destination names a song the
        // library does not hold: resolving it to the song as it was keeps the pages - and their count - exactly
        // where they are, instead of this screen closing itself or a setlist settling on the next song. The pager's key
        // is the file name, so a name the destination repeats - whatever built it, a saved back stack included - would
        // be a repeated key and a crash: each song is one page.
        destination.songFileNames.mapNotNull { songsByFileName[it] ?: songsBeingRenamed[it] }.distinctBy { it.fileName }
    }
    val isLoading by viewModel.isLoading.collectAsStateWithLifecycle()
    LaunchedEffect(songs.isEmpty(), isLoading) {
        // Every song this screen was opened on is gone from the library, and the library has been read: there is
        // nothing left to show, so the screen goes the way it would have if the song had been deleted from here. Only
        // while it is still the screen on top, since one that has just been popped goes on being composed for as long
        // as its exit transition runs, and going back from there would close the screen underneath it too.
        if (songs.isEmpty() && !isLoading && viewModel.backStack.lastOrNull() == destination) onBack()
    }
    val pagerState = rememberPagerState(initialPage = destination.initialIndex.coerceIn(0, maxOf(0, songs.lastIndex))) { songs.size }
    // The stretch every composed page is headed for, by its song's file name, where the song changes its tempo or time
    // signature further down: what the click and the app bar's tempo follow, see SongDetailsPage's onTimingChanged.
    val songTimings = remember { mutableStateMapOf<String, SongTiming>() }
    FollowPager(
        viewModel = viewModel,
        destination = destination,
        songs = songs,
        pagerState = pagerState,
        songTimings = songTimings,
    )
    val currentSong = songs.getOrNull(pagerState.currentPage)
    val canPage = songs.size > 1
    // A setlist of one song is still a setlist being played, so it keeps the bar that names it; only a song opened
    // from the library, with nothing before or after it, goes without one.
    val hasPagerControls = canPage || (destination.setlistFileName != null && songs.isNotEmpty())
    val setlist = destination.setlistFileName?.let { fileName -> setlists.firstOrNull { it.fileName == fileName } }
    val setlistTitle = setlist?.title
    // Each page's place in the setlist, the missing files included, which is what the setlist's rows are numbered by:
    // a setlist is read by its own order, and "2 / 3" over the song its screen calls number 3 would read as a mistake.
    // The pages only count themselves where one of them cannot be placed - the setlist is gone, or the library has not
    // caught up with a change to it yet - so that two numberings are never mixed in one bar.
    val setlistSlots = remember(setlist, songs) { setlist?.let { buildSetlistSlots(it.entries, songs.map { song -> song.fileName }) } }
    val shouldShowChords = userPreferences?.areChordsEnabled != false
    val chordDiagrams = rememberChordDiagrams(viewModel = viewModel, userPreferences = userPreferences, shouldShowChords = shouldShowChords)
    val isMetronomeEnabled = userPreferences?.isMetronomeEnabled != false
    val areSetlistsEnabled = userPreferences?.areSetlistsEnabled != false
    val isCoverArtEnabled = userPreferences?.isCoverArtEnabled == true
    val currentSongText = currentSong?.let { songTexts[it.fileName] }
    // The sheet of what the song is, opened from the app bar's title. Performance mode edits nothing, so there it is only
    // offered where it has something in it, and it is read from the song's text, so nowhere before that is at hand.
    // Outside performance mode it is offered wherever the text is, so nothing of the text needs to be read for it.
    val hasSongInfo = remember(currentSongText, isReadOnly) {
        isReadOnly && currentSongText?.let(viewModel::hasSongInfo) == true
    }
    val openCurrentSongInfo = currentSong?.let { song ->
        if (currentSongText == null || (isReadOnly && !hasSongInfo)) null else { { viewModel.showDialog(DialogType.SongInfo(song)) } }
    }
    val chordSpelling = userPreferences?.chordSpelling ?: UserPreferences.ChordSpelling.Default
    val layoutDirection = LocalLayoutDirection.current
    val metronomePlayback by viewModel.metronomePlayback.collectAsStateWithLifecycle()
    val metronomeSettings by viewModel.metronomeSettings.collectAsStateWithLifecycle()
    val isMetronomePlaying = metronomePlayback is MetronomePlayback.Playing
    // A click can only play here while the panel is up, which is what starts and stops it; the preference is read
    // beside it anyway, since a click stopped from the panel leaves it where it was, see
    // CampfireViewModel.toggleMetronomePanel.
    val isMetronomePanelShown = metronomeSettings.isSongPanelShown || isMetronomePlaying

    val coroutineScope = rememberCoroutineScope()
    val pageStepper = remember(pagerState, coroutineScope) { PageStepper(pagerState, coroutineScope) }
    // The arrow keys scroll the song the reader is looking at, and the pages each scroll on their own, so the one
    // that is current hands its state up here for them to drive.
    var currentPageScrollState by remember { mutableStateOf<ScrollState?>(null) }
    // The step buttons, and the keys that press them, step between the sections or the rows of the song being read, for
    // the same reason.
    var currentPageStepper by remember { mutableStateOf<SongStepper?>(null) }
    // The scroll of every page that is composed, the ones beside the current page included, so that a step back from
    // the top of a song can put the one before it at its end before the pager gets there.
    val pageScrollStates = remember { mutableStateMapOf<Int, ScrollState>() }
    // The steps go on to the song beside this one at either end of it, worked out from the page being headed for like the
    // pager bar's buttons. Both are decided again at the time of the press.
    val hasPreviousSongToStepTo = canPage && pagerState.targetPage > 0
    val hasNextSongToStepTo = canPage && pagerState.targetPage < songs.lastIndex
    val canStepBackInSong = currentPageStepper?.canStepBack == true
    val canStepForwardInSong = currentPageStepper?.canStepForward == true
    // A tap the song was dragged under steps from where the drag began (from), the rest from where the song is.
    fun stepBack(from: Int? = null) {
        val stepper = currentPageStepper
        if (stepper?.canStep(-1, from) == true) {
            coroutineScope.launch { stepper.step(-1, from) }
        } else if (pagerState.targetPage > 0) {
            // Going back from the top of a song is going back to the last lines of the one before it, and landing on its
            // top instead would have the next press skip that song whole. A page not laid out yet has nowhere to go but
            // its top.
            pageScrollStates[pagerState.targetPage - 1]?.let { state -> coroutineScope.launch { state.scrollTo(state.maxValue) } }
            pageStepper.step(-1)
        }
    }
    fun stepForward(from: Int? = null) {
        val stepper = currentPageStepper
        if (stepper?.canStep(1, from) == true) coroutineScope.launch { stepper.step(1, from) } else if (pagerState.targetPage < songs.lastIndex) pageStepper.step(1)
    }
    val stepBackAction: (from: Int?) -> Unit = ::stepBack
    val stepForwardAction: (from: Int?) -> Unit = ::stepForward

    LoadSongTexts(viewModel = viewModel, songs = songs, currentPage = pagerState.currentPage, songTexts = songTexts)

    val isCompactHeight = LocalWindowInfo.current.containerDpSize.height < SHORT_WINDOW_HEIGHT
    val appBarScrollBehavior = TopAppBarDefaults.enterAlwaysScrollBehavior(canScroll = { isCompactHeight })
    val isMetronomePanelVisible = isMetronomePanelShown && isMetronomeEnabled
    val metronomePanelState = remember { MutableTransitionState(isMetronomePanelVisible) }
    // The room above the pages changes on every frame the bar collapses or comes back and the panel opens or closes, so
    // the pages decide their grid only once both are at rest, see SongDetailsPage's isViewportSettled.
    val isViewportSettled = { (!isCompactHeight || isAppBarSettled(appBarScrollBehavior.state.collapsedFraction)) && metronomePanelState.isIdle }
    Column(
        modifier = modifier
            .fillMaxSize()
            .nestedScroll(appBarScrollBehavior.nestedScrollConnection)
            .keepScreenOn()
            .songDetailsKeyboardShortcuts(
                currentPageScrollState = { currentPageScrollState },
                coroutineScope = coroutineScope,
                pageStepper = pageStepper,
                canStepBack = canStepBackInSong || hasPreviousSongToStepTo,
                canStepForward = canStepForwardInSong || hasNextSongToStepTo,
                hasPreviousSong = canPage && pagerState.targetPage > 0,
                hasNextSong = canPage && pagerState.targetPage < songs.lastIndex,
                isUncovered = visibleDialog == null && viewModel.backStack.lastOrNull() is CampfireDestination.SongDetails,
                stepBack = stepBackAction,
                stepForward = stepForwardAction,
            ),
    ) {
        SongDetailsAppBar(
            viewModel = viewModel,
            destination = destination,
            currentSong = currentSong,
            isReadOnly = isReadOnly,
            isPerformanceModeEnabled = isPerformanceModeEnabled,
            shouldShowChords = shouldShowChords,
            isMetronomeEnabled = isMetronomeEnabled,
            areSetlistsEnabled = areSetlistsEnabled,
            isCoverArtEnabled = isCoverArtEnabled,
            chordSpelling = chordSpelling,
            songTimings = songTimings,
            currentPageScrollState = { currentPageScrollState },
            openCurrentSongInfo = openCurrentSongInfo,
            songs = songs,
            settledWidth = settledWidth,
            isMetronomePanelShown = isMetronomePanelShown,
            isBeatFlashEnabled = metronomeSettings.isVisualBeatEnabled,
            isMetronomePanelVisible = isMetronomePanelVisible,
            metronomePanelState = metronomePanelState,
            scrollBehavior = if (isCompactHeight) appBarScrollBehavior else null,
            contentPadding = contentPadding,
            coroutineScope = coroutineScope,
            onBack = onBack,
        )
        // The paging bar sits below the pager and covers the bottom inset for it, so the pages only keep the
        // padding that is still theirs to apply.
        val pageContentPadding = if (hasPagerControls) {
            PaddingValues(
                start = contentPadding.calculateStartPadding(layoutDirection),
                end = contentPadding.calculateEndPadding(layoutDirection),
            )
        } else {
            contentPadding
        }
        SongDetailsPager(
            modifier = Modifier.weight(1f).fillMaxWidth(),
            contentPadding = contentPadding,
            viewModel = viewModel,
            destination = destination,
            songs = songs,
            pagerState = pagerState,
            pageStepper = pageStepper,
            pageScrollStates = pageScrollStates,
            songTimings = songTimings,
            isReadOnly = isReadOnly,
            chordSpelling = chordSpelling,
            shouldShowChords = shouldShowChords,
            isMetronomeEnabled = isMetronomeEnabled,
            canPage = canPage,
            settledWidth = settledWidth,
            pageContentPadding = pageContentPadding,
            chordDiagrams = chordDiagrams,
            isViewportSettled = isViewportSettled,
            currentPageScrollState = { currentPageScrollState },
            currentPageStepper = { currentPageStepper },
            canStepBackInSong = canStepBackInSong,
            canStepForwardInSong = canStepForwardInSong,
            hasPreviousSongToStepTo = hasPreviousSongToStepTo,
            hasNextSongToStepTo = hasNextSongToStepTo,
            stepBack = stepBackAction,
            stepForward = stepForwardAction,
            onCurrentPage = { scrollState, stepper ->
                currentPageScrollState = scrollState
                currentPageStepper = stepper
            },
        )
        if (hasPagerControls) {
            SongPagerControls(
                setlistTitle = setlistTitle,
                setlistSlots = setlistSlots,
                currentPage = pagerState.currentPage,
                targetPage = pagerState.targetPage,
                pageCount = songs.size,
                contentPadding = contentPadding,
                onStep = pageStepper::step,
            )
        }
    }
}

/**
 * Reads the text of the song being read, and of the songs beside it: the pages next to the current one are composed
 * ahead of time (beyondViewportPageCount), so their text is read ahead of time too, and a swipe then lands on lyrics
 * rather than on a loading indicator.
 */
@Composable
private fun LoadSongTexts(
    viewModel: CampfireViewModel,
    songs: List<Song>,
    currentPage: Int,
    songTexts: Map<String, String>,
) {
    val failedSongFileNames by viewModel.failedSongFileNames.collectAsStateWithLifecycle()
    val currentFileName = songs.getOrNull(currentPage)?.fileName
    LaunchedEffect(currentFileName) { currentFileName?.let(viewModel::loadSongContent) }
    LaunchedEffect(currentPage, songs) {
        listOfNotNull(songs.getOrNull(currentPage - 1), songs.getOrNull(currentPage + 1))
            .filter { it.fileName !in songTexts && it.fileName !in failedSongFileNames }
            .forEach { viewModel.loadSongContent(it.fileName) }
    }
}

/**
 * What the Chords section of every page is drawn with, null where the diagrams are off. One for the whole pager, since
 * nothing in it belongs to one song: the instrument, the player's shapes and whether the section is folded are the same
 * wherever a song is read.
 */
@Composable
private fun rememberChordDiagrams(
    viewModel: CampfireViewModel,
    userPreferences: UserPreferences?,
    shouldShowChords: Boolean,
): ChordDiagrams? {
    val chordInstrument = userPreferences?.takeIf { shouldShowChords && it.areChordDiagramsEnabled }?.chordInstrument?.toChordInstrument()
    val storedChordShapes = chordInstrument?.let { userPreferences.chordVoicings[it.id] }.orEmpty()
    val isChordSectionFolded = userPreferences?.isChordSectionFolded == true
    return remember(chordInstrument, storedChordShapes, isChordSectionFolded) {
        chordInstrument?.let {
            ChordDiagrams(
                instrument = it,
                storedShapes = storedChordShapes,
                isFolded = isChordSectionFolded,
                onFoldToggled = viewModel::toggleChordSectionFold,
            )
        }
    }
}

/**
 * [songKeyboardShortcuts] as the song details screen sets them up: the arrows scroll the song being read, and Up and
 * Down do whatever the step buttons would do where they are there, so that a pedal pressing them reads the song the way
 * the buttons do; where they are not, the keys scroll. Not part of [SongDetailsScreen] itself, which has to stay small
 * enough for HotSpot to compile.
 *
 * @param hasPreviousSong Whether there is a song before the one being headed for. The target page only decides whether
 *   the key does anything; the step itself is decided at the time of the press.
 */
@Composable
private fun Modifier.songDetailsKeyboardShortcuts(
    currentPageScrollState: () -> ScrollState?,
    coroutineScope: CoroutineScope,
    pageStepper: PageStepper,
    canStepBack: Boolean,
    canStepForward: Boolean,
    hasPreviousSong: Boolean,
    hasNextSong: Boolean,
    isUncovered: Boolean,
    stepBack: (from: Int?) -> Unit,
    stepForward: (from: Int?) -> Unit,
) = songKeyboardShortcuts(
    onScrollUp = { currentPageScrollState()?.let { coroutineScope.launch { it.scrollByKeyStep(-1f) } } },
    onScrollDown = { currentPageScrollState()?.let { coroutineScope.launch { it.scrollByKeyStep(1f) } } },
    onStepBack = if (canStepBack) {
        { stepBack(null) }
    } else {
        null
    },
    onStepForward = if (canStepForward) {
        { stepForward(null) }
    } else {
        null
    },
    isUncovered = isUncovered,
    onPreviousSong = if (hasPreviousSong) {
        { pageStepper.step(-1) }
    } else {
        null
    },
    onNextSong = if (hasNextSong) {
        { pageStepper.step(1) }
    } else {
        null
    },
)

/**
 * Scrolls a pager created before the library was read to the song that was tapped, and tells the view model where the
 * pager is: the page it has come to rest on and the page it is headed for. Not part of [SongDetailsScreen] itself,
 * which has to stay small enough for HotSpot to compile.
 */
@Composable
private fun FollowPager(
    viewModel: CampfireViewModel,
    destination: CampfireDestination.SongDetails,
    songs: List<Song>,
    pagerState: PagerState,
    songTimings: SnapshotStateMap<String, SongTiming>,
) {
    // The initial page is only read when the pager is created. If the library had not been read by then, the pager
    // was created with no pages and its first page is page zero: the song that was tapped is scrolled to once the
    // songs are there, exactly once.
    var isInitialPageSettled by rememberSaveable { mutableStateOf(songs.isNotEmpty()) }
    LaunchedEffect(songs.size) {
        if (!isInitialPageSettled && songs.isNotEmpty()) {
            isInitialPageSettled = true
            pagerState.scrollToPage(destination.initialIndex.coerceIn(0, songs.lastIndex))
        }
    }
    // Where the pager has come to rest is where the user is, which the web build's address names. Not before the
    // initial page has been scrolled to, or the first page of a pager created before the library was read would be
    // reported on its way to the song that was tapped.
    val latestSongs by rememberUpdatedState(songs)
    val latestDestination by rememberUpdatedState(destination)
    LaunchedEffect(pagerState, isInitialPageSettled) {
        if (isInitialPageSettled) {
            snapshotFlow { latestSongs.getOrNull(pagerState.settledPage)?.fileName }.collect { fileName ->
                if (fileName != null) viewModel.onSongDetailsPageSettled(latestDestination, fileName)
            }
        }
    }
    // The page being headed for rather than the one settled on, so that a click paged on to the next song has its tempo
    // while the page is still sliding in, and the stretch of it that page is headed for. A song paged back to is put at
    // its end first (stepBack), so the click lands on its last stretch rather than on its opening.
    LaunchedEffect(pagerState, isInitialPageSettled) {
        if (isInitialPageSettled) {
            snapshotFlow { latestSongs.getOrNull(pagerState.targetPage)?.fileName?.let { it to songTimings[it] } }.collect { target ->
                if (target != null) viewModel.onSongDetailsPageChanged(latestDestination, target.first, target.second)
            }
        }
    }
}

/**
 * Whether the app bar is at rest: Material's own snap leaves it fully expanded or fully collapsed, and below 1% it does
 * not snap at all (`settleAppBar`).
 */
internal fun isAppBarSettled(collapsedFraction: Float) = collapsedFraction < 0.01f || collapsedFraction == 1f

/**
 * One press of an arrow key, in the direction it was pressed (-1 for up, 1 for down). The step is a fraction of what
 * is on screen rather than a fixed distance, so it means the same thing on a phone and on a full screen window, and
 * it is animated over roughly the interval a held key repeats at: a single press then reads as one smooth nudge,
 * while a held one keeps restarting an animation that is still moving and scrolls at a steady pace instead of
 * stuttering between steps.
 */
private suspend fun ScrollState.scrollByKeyStep(direction: Float) = animateScrollBy(
    value = viewportSize * KEY_SCROLL_STEP_FRACTION * direction,
    animationSpec = tween(durationMillis = KEY_SCROLL_STEP_DURATION, easing = LinearEasing),
)

private const val KEY_SCROLL_STEP_FRACTION = 0.1f // Of the height of the scrolling viewport.
private const val KEY_SCROLL_STEP_DURATION = 120
