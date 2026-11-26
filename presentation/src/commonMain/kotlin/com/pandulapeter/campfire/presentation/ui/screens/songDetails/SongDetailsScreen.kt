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

import com.pandulapeter.campfire.presentation.ui.screens.songDetails.autoScroll.autoScrollCoverOf
import com.pandulapeter.campfire.presentation.ui.screens.songDetails.dock.readAfterDock
import com.pandulapeter.campfire.presentation.ui.screens.songDetails.dock.DOCK_CORNER
import com.pandulapeter.campfire.presentation.ui.screens.songDetails.dock.DOCK_TRAVERSAL_INDEX
import androidx.compose.ui.semantics.isTraversalGroup
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.traversalIndex
import com.pandulapeter.campfire.presentation.ui.screens.songDetails.dock.DockSidePanel
import com.pandulapeter.campfire.presentation.ui.screens.songDetails.dock.PaddingBesidePanel
import com.pandulapeter.campfire.presentation.ui.screens.songDetails.dock.SongDock
import com.pandulapeter.campfire.presentation.ui.screens.songDetails.dock.SongDockState
import com.pandulapeter.campfire.presentation.ui.screens.songDetails.dock.rememberDockMetronome
import com.pandulapeter.campfire.presentation.ui.screens.songDetails.dock.rememberSongDock
import com.pandulapeter.campfire.presentation.ui.songView.firstSection.SongPlayingControls
import com.pandulapeter.campfire.presentation.ui.songView.model.ChordDiagrams
import com.pandulapeter.campfire.presentation.ui.songView.grid.pageCount
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
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
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.ui.draw.clip
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.snapshots.SnapshotStateMap
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.platform.LocalWindowInfo
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.compose.ui.keepScreenOn
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.unit.Dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.pandulapeter.campfire.data.model.preferences.UserPreferences
import com.pandulapeter.campfire.data.model.song.Song
import com.pandulapeter.campfire.presentation.ui.dialogs.DialogType
import com.pandulapeter.campfire.presentation.ui.dialogs.SongEditTarget
import com.pandulapeter.campfire.presentation.ui.metronome.SongTiming
import com.pandulapeter.campfire.presentation.ui.CampfireViewModel
import com.pandulapeter.campfire.presentation.ui.components.SHORT_WINDOW_HEIGHT
import com.pandulapeter.campfire.presentation.ui.navigation.CampfireDestination
import com.pandulapeter.campfire.presentation.ui.pictures.LocalSongImageLoader
import com.pandulapeter.campfire.presentation.ui.screens.rememberSongActionHandler

/**
 * The lyrics (and chords) of a song, or of a setlist's songs in a pager. **How the song is played is set from its key
 * chip** in the app bar (the key, the transposition, and the capo, see [SongPlayingControls]) and the dock under the song
 * ([SongDock]: the song's chords, its scale suggestions, and the tempo chip), so the song starts at its first line.
 *
 * The text size is a row at the end of the bar's overflow menu ([MenuStepperRow]). It can also be changed with a pinch
 * or Ctrl / Cmd + scroll on the content itself, see [fontScaleGestures], and on the desktop and the web with Ctrl / Cmd
 * + plus, minus and zero, which the window answers (`FontScaleController.zoomSongText`).
 *
 * When there is more than one song to page through, or the song is read from a setlist of any length, a
 * [SongPagerControls] row under the dock and the side panel alike offers the same paging as the swipe gesture, along
 * with the name of the setlist and the position of the current song in it.
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
    val importPreview = rememberImportPreviewState(importReport = viewModel.importReportState.importReport, destination = destination, libraryImages = viewModel.songImages)
    val isImportPreview = destination.importPreview != null
    val isReadOnly = isImportPreview || isPerformanceModeEnabled || setlists.any { it.fileName == destination.setlistFileName && it.isArchived }
    val songsBeingRenamed by viewModel.songTextStore.songsBeingRenamed.collectAsStateWithLifecycle()
    val songLookup by viewModel.libraryState.songLookup.collectAsStateWithLifecycle()
    val librarySongs = rememberLibrarySongs(destination, songLookup, songsBeingRenamed)
    val resolvedSongs = if (isImportPreview) importPreview?.songs else librarySongs
    val songs = resolvedSongs.orEmpty()
    val pagerState = rememberPagerState(initialPage = destination.initialIndex.coerceIn(0, maxOf(0, songs.lastIndex))) { songs.size }
    val pageKey = rememberPageKeys(songs = songs, isImportPreview = isImportPreview)
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
    // Each page's place in the setlist, the missing files included, which is what the setlist's rows are numbered by:
    // a setlist is read by its own order, and "2 / 3" over the song its screen calls number 3 would read as a mistake.
    // The pages only count themselves where one of them cannot be placed - the setlist is gone, or the library has not
    // caught up with a change to it yet - so that two numberings are never mixed in one bar.
    val setlistSlots = remember(setlist, songs) { setlist?.let { buildSetlistSlots(it.entries, songs.map { song -> song.fileName }) } }
    val shouldShowChords = userPreferences?.areChordsEnabled != false
    val chordDiagrams = rememberChordDiagrams(userPreferences = userPreferences)
    val (isMetronomeEnabled, isTunerInMenu) = songDetailsToolsOf(userPreferences, isImportPreview)
    val areSetlistsEnabled = userPreferences?.areSetlistsEnabled != false
    val isCoverArtEnabled = userPreferences?.isCoverArtEnabled == true
    val openCurrentSongInfo = rememberOpenSongInfo(
        viewModel = viewModel,
        song = currentSong?.takeIf { !isImportPreview },
        songText = currentSong?.let { songTexts[it.fileName] },
        isReadOnly = isReadOnly,
        areAttachmentsEnabled = userPreferences?.isAttachmentsEnabled != false,
    )
    val chordSpelling = userPreferences?.chordSpelling ?: UserPreferences.ChordSpelling.Default
    val layoutDirection = LocalLayoutDirection.current
    val dock = rememberSongDock(userPreferences, chordDiagrams, hasPagerBar = hasPagerControls, isImportPreview = isImportPreview, contentPadding = contentPadding, targetPage = pagerState.targetPage, songKey = songs.getOrNull(pagerState.targetPage)?.fileName, songText = songs.getOrNull(pagerState.targetPage)?.let { songTexts[it.fileName] }, dockFacts = viewModel.songTextStore.dockFacts)
    // The side panel covers the end inset for what stands beside it, which keeps only what of it is still its own.
    val songPadding = remember(contentPadding, dock) { PaddingBesidePanel(contentPadding) { dock.panelWidth } }
    val isUncovered = isSongDetailsUncovered(visibleDialog, viewModel.navigator.backStack.lastOrNull())
    val steps = rememberSongDetailsSteps(
        pagerState = pagerState,
        songs = songs,
        currentSong = currentSong,
        userPreferences = userPreferences,
        cover = autoScrollCoverOf(visibleDialog, viewModel.navigator.backStack.lastOrNull()),
        playback = viewModel.navigator.autoScrollPlayback,
        writeSpeed = viewModel.preferencesController::setAutoScrollSpeed,
        isNextSongAsked = destination.setlistFileName != null && !isImportPreview,
    )

    val isCompactHeight = LocalWindowInfo.current.containerDpSize.height < SHORT_WINDOW_HEIGHT
    val appBarScrollBehavior = TopAppBarDefaults.enterAlwaysScrollBehavior(canScroll = { isCompactHeight })
    // The room the pages have changes on every frame the bar collapses or comes back and the dock grows or shrinks, so the
    // pages decide their grid only once both are at rest, see SongDetailsPage's isViewportSettled.
    val isViewportSettled = { (!isCompactHeight || isAppBarSettled(appBarScrollBehavior.state.collapsedFraction)) && dock.isSettled }
    Column(
        modifier = modifier
            .fillMaxSize()
            .nestedScroll(appBarScrollBehavior.nestedScrollConnection)
            .keepScreenOn()
            .songDetailsKeyboardShortcuts(
                currentPageScrollState = { steps.current.scrollState },
                coroutineScope = steps.coroutineScope,
                pageStepper = steps.pageStepper,
                canStepBack = steps.canStepBackInSong || steps.hasPreviousSongToStepTo,
                canStepForward = steps.canStepForwardInSong || steps.hasNextSongToStepTo,
                hasPreviousSong = canPage && pagerState.targetPage > 0,
                hasNextSong = canPage && pagerState.targetPage < songs.lastIndex,
                isUncovered = isUncovered,
                stepActions = steps.stepActions,
                onToggleAutoScroll = steps.autoScrolling::togglePlaying.takeIf { steps.isContinuous },
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
            onTunerClicked = viewModel::openTunerSheet,
            destination = destination,
            currentSong = currentSong,
            isReadOnly = isReadOnly,
            isPerformanceModeEnabled = isPerformanceModeEnabled,
            shouldShowChords = shouldShowChords,
            isTunerInMenu = isTunerInMenu,
            dockContents = dock.contents,
            targetPage = { pagerState.targetPage },
            areSetlistsEnabled = areSetlistsEnabled,
            // Nothing is fetched for a song that is not in the library.
            isCoverArtEnabled = isCoverArtEnabled && !isImportPreview,
            chordSpelling = chordSpelling,
            openCurrentSongInfo = openCurrentSongInfo,
            songs = songs,
            settledWidth = settledWidth,
            scrollBehavior = if (isCompactHeight) appBarScrollBehavior else null,
            contentPadding = contentPadding,
            onBack = onBack,
        )
        // The paging bar, or the dock without one, sits below the pager and covers the bottom inset for it, so the pages
        // only keep the padding that is still theirs to apply.
        val pagerBarCover = if (hasPagerControls) contentPadding.calculateBottomPadding() else 0.dp
        val baseBottom = (contentPadding.calculateBottomPadding() - dock.height - pagerBarCover).coerceAtLeast(0.dp)
        val previewCard = @Composable { preview: ImportPreviewState?, padding: PaddingValues, bottom: Dp ->
            PreviewCard(viewModel = viewModel, importPreview = preview, currentPage = pagerState.currentPage, contentPadding = padding, baseBottom = bottom)
        }
        val metronome = rememberDockMetronome(viewModel.metronomeController, viewModel.libraryState, viewModel.songTextStore)
        Row(modifier = Modifier.weight(1f).fillMaxWidth()) {
            Column(modifier = Modifier.weight(1f)) {
                PagesAbovePreviewBar(
                    modifier = Modifier.weight(1f).fillMaxWidth().readAfterDock(),
                    baseBottom = baseBottom,
                    bar = { previewCard(importPreview.takeUnless { dock.isSidePanel }, songPadding, baseBottom) },
                ) { bottomPadding ->
                    // A preview's pictures are the ones its review brings, which no shelf keeps before Import.
                    CompositionLocalProvider(LocalSongImageLoader provides (importPreview?.images ?: LocalSongImageLoader.current)) {
                        SongDetailsPager(
                            modifier = Modifier.fillMaxSize(),
                            contentPadding = songPadding,
                            songTextStore = viewModel.songTextStore,
                            overrides = viewModel.overrides,
                            preferencesController = viewModel.preferencesController,
                            fontScaleController = viewModel.fontScaleController,
                            songRenderer = viewModel.songRenderer,
                            dialogHost = viewModel.dialogHost,
                            destination = destination,
                            importPreview = importPreview,
                            pageKey = pageKey,
                            songs = songs,
                            pagerState = pagerState,
                            pageStepper = steps.pageStepper,
                            pageScrollStates = steps.pageScrollStates,
                            dock = dock,
                            songTimings = songTimings,
                            isReadOnly = isReadOnly,
                            chordSpelling = chordSpelling,
                            shouldShowChords = shouldShowChords,
                            isMetronomeEnabled = isMetronomeEnabled,
                            canPage = canPage,
                            settledWidth = settledWidth - dock.panelTarget,
                            pageContentPadding = PaddingValues(
                                start = contentPadding.calculateStartPadding(layoutDirection),
                                end = songPadding.calculateEndPadding(layoutDirection),
                                bottom = bottomPadding,
                            ),
                            chordDiagrams = chordDiagrams,
                            isViewportSettled = isViewportSettled,
                            currentPageScrollState = { steps.current.scrollState },
                            currentPageStepper = { steps.current.stepper },
                            canStepBackInSong = steps.canStepBackInSong,
                            canStepForwardInSong = steps.canStepForwardInSong,
                            hasPreviousSongToStepTo = steps.hasPreviousSongToStepTo,
                            hasNextSongToStepTo = steps.hasNextSongToStepTo,
                            stepBack = steps.stepBack,
                            stepForward = steps.stepForward,
                            isContinuous = steps.isContinuous,
                            autoScrolling = steps.autoScrolling,
                            onCurrentPage = steps::onCurrentPage,
                        )
                    }
                }
                SongDock(
                    dock = dock,
                    metronome = metronome,
                    contentPadding = songPadding,
                    targetPage = pagerState.targetPage,
                    isUnderCard = importPreview != null && !dock.isSidePanel,
                )
            }
            DockSidePanel(
                dock = dock,
                metronome = metronome,
                contentPadding = contentPadding,
                targetPage = pagerState.targetPage,
                hasPagerBar = hasPagerControls,
                card = { previewCard(importPreview.takeIf { dock.isSidePanel }, PaddingValues(), 0.dp) },
            )
        }
        if (hasPagerControls) {
            SetlistPagerBar(setlistTitle = setlist?.title, setlistSlots = setlistSlots, pagerState = pagerState, songs = songs, steps = steps, contentPadding = contentPadding, dock = dock, hasCard = importPreview != null)
        }
    }
}

/** [SongDetailsPreviewBar] for [importPreview] on the page the pager is on ([currentPage]), padded by [contentPadding]. */
@Composable
private fun PreviewCard(
    viewModel: CampfireViewModel,
    importPreview: ImportPreviewState?,
    currentPage: Int,
    contentPadding: PaddingValues,
    baseBottom: Dp,
) = SongDetailsPreviewBar(
    importController = viewModel.importController,
    importReportState = viewModel.importReportState,
    importPreview = importPreview,
    currentPage = currentPage,
    isAddingToReview = viewModel.importController.isAddingToReview.collectAsStateWithLifecycle().value,
    contentPadding = contentPadding,
    layoutDirection = LocalLayoutDirection.current,
    baseBottom = baseBottom,
)

/**
 * [SongPagerControls] for a setlist read through [songs], stepping with [steps], as wide as the screen under the dock and
 * the side panel alike, on their surface, and read by screen readers with them. Where nothing stands on the surface
 * above it (no rows under the song in [dock], no import preview's card, [hasCard]) its top corners are rounded as the
 * dock's would be, but the end one under the side panel, which it meets square.
 */
@Composable
private fun SetlistPagerBar(
    setlistTitle: String?,
    setlistSlots: SetlistSlots?,
    pagerState: PagerState,
    songs: List<Song>,
    steps: SongDetailsSteps,
    contentPadding: PaddingValues,
    dock: SongDockState,
    hasCard: Boolean,
) = SongPagerControls(
    modifier = Modifier
        .clip(
            RoundedCornerShape(
                topStart = animateDpAsState(if (dock.layout == null && !hasCard) DOCK_CORNER else 0.dp).value,
                topEnd = animateDpAsState(if (dock.layout == null && !hasCard && !dock.isSidePanel) DOCK_CORNER else 0.dp).value,
            ),
        )
        // Read with the dock, after it and before the song, as its last row was.
        .semantics {
            isTraversalGroup = true
            traversalIndex = DOCK_TRAVERSAL_INDEX
        },
    setlistTitle = setlistTitle,
    setlistSlots = setlistSlots,
    currentPage = pagerState.currentPage,
    targetPage = pagerState.targetPage,
    pageCount = songs.size,
    contentPadding = contentPadding,
    nextSongTitle = songs.getOrNull(pagerState.targetPage + 1)?.title?.takeIf { steps.nextSongConfirmation.armedPage == pagerState.targetPage },
    onStep = steps.pageStepper::step,
    onConfirmNextSong = { steps.stepForward(null) },
)

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
 * Opens the sheet of what [song] is, from the app bar's title and its menu, or null where it is not offered. Performance
 * mode edits nothing, so there it is only offered where it has something in it, and it is read from the song's text, so
 * nowhere before that is at hand. Outside performance mode it is offered wherever the text is, so nothing of the text
 * needs to be read for it. A preview ([song] null) offers none: the sheet reads and writes the library's file, which the
 * preview's song is not yet.
 */
@Composable
private fun rememberOpenSongInfo(
    viewModel: CampfireViewModel,
    song: Song?,
    songText: String?,
    isReadOnly: Boolean,
    areAttachmentsEnabled: Boolean,
): (() -> Unit)? {
    val hasSongInfo = remember(songText, isReadOnly, areAttachmentsEnabled) {
        isReadOnly && songText?.let { viewModel.songMetadataEditing.hasSongInfo(it, areAttachmentsEnabled) } == true
    }
    if (song == null || songText == null || (isReadOnly && !hasSongInfo)) return null
    return { viewModel.dialogHost.showDialog(DialogType.SongInfo(song = song, target = SongEditTarget.File(song.fileName))) }
}

/**
 * Whether the app bar is at rest: Material's own snap leaves it fully expanded or fully collapsed, and below 1% it does
 * not snap at all (`settleAppBar`).
 */
internal fun isAppBarSettled(collapsedFraction: Float) = collapsedFraction < 0.01f || collapsedFraction == 1f
