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
import androidx.compose.animation.core.MutableTransitionState
import androidx.compose.foundation.ScrollState
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.calculateEndPadding
import androidx.compose.foundation.layout.calculateStartPadding
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
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
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshots.SnapshotStateMap
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
import com.pandulapeter.campfire.data.model.song.Song
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
import com.pandulapeter.campfire.presentation.ui.screens.rememberSongActionHandler
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
    val importPreview = rememberImportPreviewState(importReport = viewModel.importReportState.importReport, destination = destination)
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
    val pagerState = rememberPagerState(initialPage = destination.initialIndex.coerceIn(0, maxOf(0, songs.lastIndex))) { songs.size }
    val pageKey = rememberPageKeys(songs = songs, isImportPreview = isImportPreview)
    val isAddingToReview by viewModel.importController.isAddingToReview.collectAsStateWithLifecycle()
    // The stretch every composed page is headed for, by its song's file name, where the song changes its tempo or time
    // signature further down: what the click and the app bar's tempo follow, see SongDetailsPage's onTimingChanged.
    val songTimings = remember { mutableStateMapOf<String, SongTiming>() }
    val currentSong = songs.getOrNull(pagerState.currentPage)
    SongDetailsEffects(
        viewModel = viewModel,
        destination = destination,
        importPreview = importPreview,
        resolvedSongs = resolvedSongs,
        pagerState = pagerState,
        currentPage = pagerState.currentPage,
        songTimings = songTimings,
        songTexts = songTexts,
        userPreferences = userPreferences,
        onBack = onBack,
    )
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
    val isUncovered = isSongDetailsUncovered(visibleDialog, viewModel.navigator.backStack.lastOrNull())
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
                songActions = rememberSongActionHandler(viewModel.editorSession, viewModel.songFilesController, viewModel.dialogHost),
                librarySong = currentSong?.takeIf { !isPerformanceModeEnabled && !isImportPreview },
                setlistFileName = destination.setlistFileName,
                importPreview = importPreview,
                importController = viewModel.importController,
                playingKeys = SongPlayingKeys(overrides = viewModel.overrides, areChordsShown = shouldShowChords, isTempoShown = isMetronomeEnabled).takeUnless { isReadOnly },
            ),
    ) {
        SongDetailsAppBar(
            editorSession = viewModel.editorSession,
            songFilesController = viewModel.songFilesController,
            dialogHost = viewModel.dialogHost,
            playingOverridesFlow = viewModel.overrides.playingOverrides,
            songTextStore = viewModel.songTextStore,
            songRenderer = viewModel.songRenderer,
            libraryState = viewModel.libraryState,
            fontScaleController = viewModel.fontScaleController,
            metronomeController = viewModel.metronomeController,
            onTunerClicked = viewModel::openTunerSheet,
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
                SongDetailsPreviewBar(
                    importController = viewModel.importController,
                    importReportState = viewModel.importReportState,
                    importPreview = importPreview,
                    currentPage = pagerState.currentPage,
                    isAddingToReview = isAddingToReview,
                    contentPadding = contentPadding,
                    layoutDirection = layoutDirection,
                    baseBottom = baseBottom,
                )
            },
        ) { bottomPadding ->
            SongDetailsPager(
                modifier = Modifier.fillMaxSize(),
                contentPadding = contentPadding,
                songTextStore = viewModel.songTextStore,
                overrides = viewModel.overrides,
                preferencesController = viewModel.preferencesController,
                fontScaleController = viewModel.fontScaleController,
                songRenderer = viewModel.songRenderer,
                dialogHost = viewModel.dialogHost,
                metronomeController = viewModel.metronomeController,
                onTunerClicked = viewModel::openTunerSheet,
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
 * What the song details screen does rather than draws: it closes once there is nothing left to show, asks the questions
 * a song is the moment for, follows the pager, and reads the texts of the pages around the one being read.
 */
@Composable
private fun SongDetailsEffects(
    viewModel: CampfireViewModel,
    destination: CampfireDestination.SongDetails,
    importPreview: ImportPreviewState?,
    resolvedSongs: List<Song>?,
    pagerState: PagerState,
    currentPage: Int,
    songTimings: SnapshotStateMap<String, SongTiming>,
    songTexts: Map<String, String>,
    userPreferences: UserPreferences?,
    onBack: () -> Unit,
) {
    val isImportPreview = destination.importPreview != null
    val songs = resolvedSongs.orEmpty()
    LaunchedEffect(resolvedSongs?.isEmpty()) {
        if (shouldCloseSongDetails(isImportPreview, resolvedSongs, viewModel.navigator.backStack.lastOrNull(), destination)) onBack()
    }
    CloseImportPreviewWithoutReview(
        importReport = viewModel.importReportState.importReport,
        backStack = viewModel.navigator.backStack,
        destination = destination,
        importPreview = importPreview,
        onBack = onBack,
    )
    if (!isImportPreview) AskForSongLayoutOnce(showIfNoneIsShown = viewModel.dialogHost::showIfNoneIsShown, userPreferences = userPreferences)
    FollowPager(
        onPageSettled = viewModel.navigator::onSongDetailsPageSettled,
        onPageChanged = viewModel.metronomeController::onSongDetailsPageChanged,
        destination = destination,
        songs = songs,
        pagerState = pagerState,
        songTimings = songTimings,
    )
    if (!isImportPreview) {
        AskForChordNamesOnce(
            preferencesController = viewModel.preferencesController,
            playingOverrides = viewModel.overrides.playingOverrides,
            songTextStore = viewModel.songTextStore,
            dialogHost = viewModel.dialogHost,
            backStack = viewModel.navigator.backStack,
            songRenderer = viewModel.songRenderer,
            song = songs.getOrNull(currentPage),
            destination = destination,
        )
    }
    if (importPreview != null) {
        InferImportPreviewKeys(
            inferredKeys = viewModel.songTextStore.inferredKeys,
            importReport = viewModel.importReportState.importReport,
            importPreview = importPreview,
            currentPage = currentPage,
        )
    } else if (!isImportPreview) {
        LoadSongTexts(songTextStore = viewModel.songTextStore, songs = songs, currentPage = currentPage, songTexts = songTexts)
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
 * Whether the app bar is at rest: Material's own snap leaves it fully expanded or fully collapsed, and below 1% it does
 * not snap at all (`settleAppBar`).
 */
internal fun isAppBarSettled(collapsedFraction: Float) = collapsedFraction < 0.01f || collapsedFraction == 1f
