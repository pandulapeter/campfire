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

import com.pandulapeter.campfire.presentation.ui.screens.songDetails.autoScroll.rememberSongAutoScrolling
import com.pandulapeter.campfire.presentation.ui.screens.songDetails.stepping.PageStepper
import com.pandulapeter.campfire.presentation.ui.screens.songDetails.stepping.SongStepper
import com.pandulapeter.campfire.presentation.ui.songView.firstSection.SongPlayingControls
import com.pandulapeter.campfire.presentation.ui.songView.model.ChordDiagrams
import com.pandulapeter.campfire.presentation.ui.songView.grid.pageCount
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
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.layout.size
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
import androidx.compose.runtime.setValue
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.platform.LocalWindowInfo
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.compose.ui.keepScreenOn
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.unit.Dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.pandulapeter.campfire.data.model.preferences.SongLayout
import com.pandulapeter.campfire.data.model.preferences.UserPreferences
import com.pandulapeter.campfire.metronome.api.model.MetronomePlayback
import com.pandulapeter.campfire.presentation.mapper.toChordNotation
import com.pandulapeter.campfire.presentation.ui.dialogs.DialogType
import com.pandulapeter.campfire.presentation.ui.instrument.chordTuning
import com.pandulapeter.campfire.presentation.ui.instrument.instrumentTuning
import com.pandulapeter.campfire.presentation.ui.dialogs.SongEditTarget
import com.pandulapeter.campfire.presentation.ui.instrument.storedChordShapes
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
 * ([MenuStepperRow]) - and in performance mode, where it is the one control beside that menu, in the bar itself
 * wherever it fits ([showsFontScaleInPerformanceBar]). It can
 * also be changed with a pinch or Ctrl / Cmd + scroll on the content itself, see [fontScaleGestures], and on the
 * desktop and the web with Ctrl / Cmd + plus, minus and zero, which the window answers (`FontScaleController.zoomSongText`).
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
    val setlists by viewModel.libraryState.setlists.collectAsStateWithLifecycle()
    val songTexts by viewModel.songTextStore.songTexts.collectAsStateWithLifecycle()
    val transpositions by viewModel.overrides.transpositions.collectAsStateWithLifecycle()
    val userPreferences by viewModel.userPreferences.collectAsStateWithLifecycle()
    // Only what is drawn over the screen, which the keys are then meant for instead.
    val visibleDialog by viewModel.dialogHost.visibleDialog.collectAsStateWithLifecycle()
    val isPerformanceModeEnabled by viewModel.preferencesController.isPerformanceModeEnabled.collectAsStateWithLifecycle()
    // A preview of songs an import has not written yet is read only like performance mode, since nothing of it is the
    // library's to change: its pages are the plan's, see ImportPreviewState.
    val importPreview = rememberImportPreviewState(viewModel = viewModel, destination = destination)
    val isImportPreview = destination.importPreview != null
    val isReadOnly = isImportPreview || isPerformanceModeEnabled || setlists.any { it.fileName == destination.setlistFileName && it.isArchived }
    val songsBeingRenamed by viewModel.songTextStore.songsBeingRenamed.collectAsStateWithLifecycle()
    val songLookup by viewModel.libraryState.songLookup.collectAsStateWithLifecycle()
    val librarySongs = remember(destination, songLookup, songsBeingRenamed) {
        // A song whose file is being renamed is still this screen's song. The library drops the old name as the file
        // moves and the back stack is rewritten a few writes later, and in between the destination names a song the
        // library does not hold: resolving it to the song as it was keeps the pages - and their count - exactly
        // where they are, instead of this screen closing itself or a setlist settling on the next song. The pager's key
        // is the file name, so a name the destination repeats - whatever built it, a saved back stack included - would
        // be a repeated key and a crash: each song is one page.
        songDetailsSongsOf(destination.songFileNames, songLookup, songsBeingRenamed)
    }
    val resolvedSongs = if (isImportPreview) importPreview?.songs else librarySongs
    val songs = resolvedSongs.orEmpty()
    LaunchedEffect(resolvedSongs?.isEmpty()) {
        // Every song this screen was opened on is gone from a library that has been read, and indexed: there is
        // nothing left to show, so the screen goes the way it would have if the song had been deleted from here. Only
        // while it is still the screen on top, since one that has just been popped goes on being composed for as long
        // as its exit transition runs, and going back from there would close the screen underneath it too.
        if (!isImportPreview && resolvedSongs?.isEmpty() == true && viewModel.navigator.backStack.lastOrNull() == destination) onBack()
    }
    CloseImportPreviewWithoutReview(viewModel = viewModel, destination = destination, importPreview = importPreview, onBack = onBack)
    if (!isImportPreview) AskForSongLayoutOnce(viewModel = viewModel, userPreferences = userPreferences)
    val pagerState = rememberPagerState(initialPage = destination.initialIndex.coerceIn(0, maxOf(0, songs.lastIndex))) { songs.size }
    val pageKey = rememberPageKeys(songs = songs, isImportPreview = isImportPreview)
    val isAddingToReview by viewModel.importController.isAddingToReview.collectAsStateWithLifecycle()
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
    val isTunerEnabled = userPreferences?.isTunerEnabled != false
    val areSetlistsEnabled = userPreferences?.areSetlistsEnabled != false
    val isCoverArtEnabled = userPreferences?.isCoverArtEnabled == true
    val currentSongText = currentSong?.let { songTexts[it.fileName] }
    // The sheet of what the song is, opened from the app bar's title and its menu. Performance mode edits nothing, so
    // there it is only offered where it has something in it, and it is read from the song's text, so nowhere before that
    // is at hand. Outside performance mode it is offered wherever the text is, so nothing of the text needs to be read
    // for it.
    val hasSongInfo = remember(currentSongText, isReadOnly) {
        isReadOnly && currentSongText?.let(viewModel.songMetadataEditing::hasSongInfo) == true
    }
    // A preview offers none: the sheet reads and writes the library's file, which the preview's song is not yet.
    val openCurrentSongInfo = currentSong?.takeIf { !isImportPreview }?.let { song ->
        if (currentSongText == null || (isReadOnly && !hasSongInfo)) null else { { viewModel.dialogHost.showDialog(DialogType.SongInfo(song = song, target = SongEditTarget.File(song.fileName))) } }
    }
    val chordSpelling = userPreferences?.chordSpelling ?: UserPreferences.ChordSpelling.Default
    val layoutDirection = LocalLayoutDirection.current
    val metronomePlayback by viewModel.metronomeController.metronomePlayback.collectAsStateWithLifecycle()
    val metronomeSettings by viewModel.metronomeController.metronomeSettings.collectAsStateWithLifecycle()
    val isMetronomePlaying = metronomePlayback is MetronomePlayback.Playing
    // A click can only play here while the panel is up, which is what starts and stops it; the preference is read
    // beside it anyway, since a click stopped from the panel leaves it where it was, see
    // MetronomeController.toggleMetronomePanel.
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
    // A song in one column scrolls by itself, and the step buttons, their keys, and a tap only nudge it there.
    val isContinuous = userPreferences?.songLayout == SongLayout.CONTINUOUS
    val isUncovered = visibleDialog == null && viewModel.navigator.backStack.lastOrNull() is CampfireDestination.SongDetails
    val autoScrolling = rememberSongAutoScrolling(
        userPreferences = userPreferences,
        isUncovered = isUncovered,
        currentPage = pagerState.currentPage,
        songFileName = currentSong?.fileName,
        writeSpeed = viewModel.preferencesController::setAutoScrollSpeed,
    )
    val canStepBackInSong = if (isContinuous) autoScrolling.scroller?.canNudge(-1) == true else currentPageStepper?.canStepBack == true
    val canStepForwardInSong = if (isContinuous) autoScrolling.scroller?.canNudge(1) == true else currentPageStepper?.canStepForward == true
    // A tap the song was dragged under steps from where the drag began (from), the rest from where the song is.
    fun stepBack(from: Int? = null) {
        val stepper = currentPageStepper
        if (isContinuous && autoScrolling.nudge(-1)) return
        if (!isContinuous && stepper?.canStep(-1, from) == true) {
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
        if (isContinuous && autoScrolling.nudge(1)) return
        if (!isContinuous && stepper?.canStep(1, from) == true) {
            coroutineScope.launch { stepper.step(1, from) }
        } else if (pagerState.targetPage < songs.lastIndex) {
            pageStepper.step(1)
        }
    }
    val stepBackAction: (from: Int?) -> Unit = ::stepBack
    val stepForwardAction: (from: Int?) -> Unit = ::stepForward

    if (importPreview != null) {
        InferImportPreviewKeys(viewModel = viewModel, importPreview = importPreview, currentPage = pagerState.currentPage)
    } else if (!isImportPreview) {
        LoadSongTexts(viewModel = viewModel, songs = songs, currentPage = pagerState.currentPage, songTexts = songTexts)
    }

    val isCompactHeight = LocalWindowInfo.current.containerDpSize.height < SHORT_WINDOW_HEIGHT
    val appBarScrollBehavior = TopAppBarDefaults.enterAlwaysScrollBehavior(canScroll = { isCompactHeight })
    val isMetronomePanelVisible = isMetronomePanelShown && isMetronomeEnabled && !isImportPreview
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
                isUncovered = isUncovered,
                stepBack = stepBackAction,
                stepForward = stepForwardAction,
                onToggleAutoScroll = autoScrolling::togglePlaying.takeIf { isContinuous },
                onEdit = currentSong?.takeIf { !isPerformanceModeEnabled && !isImportPreview }?.let { song -> { viewModel.editorSession.openEditor(song.fileName) } },
                onSave = importPreview?.let { preview ->
                    // The batch being added is waited for by the controller itself, which ignores the press meanwhile.
                    preview.actions?.takeIf { preview.canImport(primary = it.primary, isAddingToReview = false) }?.saveShortcutAction
                }?.let { action -> { viewModel.importController.importFromPreview(action) } },
            ),
    ) {
        SongDetailsAppBar(
            viewModel = viewModel,
            destination = destination,
            currentSong = currentSong,
            isReadOnly = isReadOnly,
            // A preview has no song's actions: what it is about is the import, which its buttons answer.
            isPerformanceModeEnabled = isPerformanceModeEnabled || isImportPreview,
            shouldShowChords = shouldShowChords,
            isMetronomeEnabled = isMetronomeEnabled,
            isTunerEnabled = isTunerEnabled,
            areSetlistsEnabled = areSetlistsEnabled,
            // Nothing is fetched for a song that is not in the library.
            isCoverArtEnabled = isCoverArtEnabled && !isImportPreview,
            chordSpelling = chordSpelling,
            currentPageScrollState = { currentPageScrollState },
            openCurrentSongInfo = openCurrentSongInfo,
            songs = songs,
            settledWidth = settledWidth,
            isMetronomePanelVisible = isMetronomePanelVisible,
            metronomePanelState = metronomePanelState,
            scrollBehavior = if (isCompactHeight) appBarScrollBehavior else null,
            contentPadding = contentPadding,
            coroutineScope = coroutineScope,
            onBack = onBack,
        )
        // The paging bar sits below the pager and covers the bottom inset for it, so the pages only keep the
        // padding that is still theirs to apply.
        val baseBottom = if (hasPagerControls) 0.dp else contentPadding.calculateBottomPadding()
        PagesAbovePreviewBar(
            modifier = Modifier.weight(1f).fillMaxWidth(),
            baseBottom = baseBottom,
            bar = {
                ImportPreviewBar(
                    modifier = Modifier
                        .padding(
                            start = contentPadding.calculateStartPadding(layoutDirection) + PREVIEW_BAR_MARGIN,
                            end = contentPadding.calculateEndPadding(layoutDirection) + PREVIEW_BAR_MARGIN,
                            bottom = baseBottom + PREVIEW_BAR_MARGIN,
                        )
                        .widthIn(max = PREVIEW_BAR_MAX_WIDTH),
                    importPreview = importPreview,
                    currentPage = pagerState.currentPage,
                    isAddingToReview = isAddingToReview,
                    onAction = viewModel.importController::importFromPreview,
                    onChosenChange = { key, isChosen -> viewModel.importReportState.setImportEntriesChosen(listOf(key), isChosen) },
                    onResolutionSelected = viewModel.importReportState::setImportConflictResolution,
                )
            },
        ) { bottomPadding ->
            SongDetailsPager(
                modifier = Modifier.fillMaxSize(),
                contentPadding = contentPadding,
                viewModel = viewModel,
                destination = destination,
                importPreview = importPreview,
                pageKey = pageKey,
                songs = songs,
                pagerState = pagerState,
                pageStepper = pageStepper,
                pageScrollStates = pageScrollStates,
                songTimings = songTimings,
                isReadOnly = isReadOnly,
                chordSpelling = chordSpelling,
                shouldShowChords = shouldShowChords,
                isMetronomeEnabled = isMetronomeEnabled,
                isTunerEnabled = isTunerEnabled,
                isTunerSheetShown = visibleDialog is DialogType.Tuner,
                isMetronomePanelShown = isMetronomePanelShown,
                isBeatFlashEnabled = metronomeSettings.isVisualBeatEnabled,
                canPage = canPage,
                settledWidth = settledWidth,
                pageContentPadding = PaddingValues(
                    start = contentPadding.calculateStartPadding(layoutDirection),
                    end = contentPadding.calculateEndPadding(layoutDirection),
                    bottom = bottomPadding,
                ),
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
                isContinuous = isContinuous,
                autoScrolling = autoScrolling,
                onCurrentPage = { scrollState, stepper, autoScroller ->
                    currentPageScrollState = scrollState
                    currentPageStepper = stepper
                    autoScrolling.scroller = autoScroller
                },
            )
        }
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
 * What the Chords section of every page is drawn with, null where the diagrams are off. One for the whole pager, since
 * nothing else in it belongs to one song: the instrument, the tuning, and the player's shapes are the same wherever a
 * song is read. Whether the section is folded is each song's own, given by its page. An instrument without shapes still gets one, with no tuning to draw in, so that
 * the first section keeps the chips that change it.
 */
@Composable
private fun rememberChordDiagrams(
    viewModel: CampfireViewModel,
    userPreferences: UserPreferences?,
    shouldShowChords: Boolean,
): ChordDiagrams? {
    val preferences = userPreferences?.takeIf { shouldShowChords && it.areChordDiagramsEnabled } ?: return null
    val chordTuning = preferences.chordTuning()
    val storedChordShapes = chordTuning?.let { preferences.storedChordShapes(it) }.orEmpty()
    val instrument = preferences.instrument
    val instrumentTuning = preferences.instrumentTuning
    val notation = preferences.chordSpelling.notation.toChordNotation()
    return remember(chordTuning, storedChordShapes, instrument, instrumentTuning, notation) {
        ChordDiagrams(
            tuning = chordTuning,
            instrument = instrument,
            instrumentTuning = instrumentTuning,
            storedShapes = storedChordShapes,
            notation = notation,
        )
    }
}

/**
 * [songKeyboardShortcuts] as the song details screen sets them up: the arrows scroll the song being read, and Up and
 * Down do whatever the step buttons would do where they are there, so that a pedal pressing them reads the song the way
 * the buttons do; where they are not, the keys scroll. Not part of [SongDetailsScreen] itself, so that a change of what
 * it reads recomposes only this.
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
    onToggleAutoScroll: (() -> Unit)?,
    onEdit: (() -> Unit)?,
    onSave: (() -> Unit)?,
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
    onToggleAutoScroll = onToggleAutoScroll,
    onEdit = onEdit,
    onSave = onSave,
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

/** Between a preview's card and the edges around it, and between it and the last line of the song above it. */
private val PREVIEW_BAR_MARGIN = 16.dp

/** A preview's card stays a card, centred, rather than a strip across a wide window, whose message would sit far from its buttons. */
private val PREVIEW_BAR_MAX_WIDTH = 720.dp
