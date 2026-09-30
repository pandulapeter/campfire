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
import androidx.compose.animation.EnterExitState
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.tween
import androidx.compose.animation.expandHorizontally
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.animation.shrinkHorizontally
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.ScrollState
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.animateScrollBy
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
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
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.PagerState
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.SmallFloatingActionButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.focus.focusProperties
import androidx.compose.ui.graphics.painter.Painter
import androidx.compose.ui.keepScreenOn
import androidx.compose.ui.layout.layout
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.offset
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.pandulapeter.campfire.chordpro.model.ChordProSong
import com.pandulapeter.campfire.data.model.domain.Song
import com.pandulapeter.campfire.data.model.domain.Setlist
import com.pandulapeter.campfire.data.model.domain.UserPreferences
import com.pandulapeter.campfire.presentation.localization.stringResource
import com.pandulapeter.campfire.presentation.resources.Res
import com.pandulapeter.campfire.presentation.resources.back
import com.pandulapeter.campfire.presentation.resources.ic_back
import com.pandulapeter.campfire.presentation.resources.ic_error
import com.pandulapeter.campfire.presentation.resources.ic_move_down
import com.pandulapeter.campfire.presentation.resources.ic_move_up
import com.pandulapeter.campfire.presentation.resources.ic_songs
import com.pandulapeter.campfire.presentation.resources.ic_next
import com.pandulapeter.campfire.presentation.resources.ic_previous
import com.pandulapeter.campfire.presentation.resources.retry
import com.pandulapeter.campfire.presentation.resources.song_details_next_row
import com.pandulapeter.campfire.presentation.resources.song_details_next_section
import com.pandulapeter.campfire.presentation.resources.song_details_next_song
import com.pandulapeter.campfire.presentation.resources.song_details_empty
import com.pandulapeter.campfire.presentation.resources.song_details_no_data
import com.pandulapeter.campfire.presentation.resources.song_details_no_data_hint
import com.pandulapeter.campfire.presentation.resources.song_details_previous_row
import com.pandulapeter.campfire.presentation.resources.song_details_previous_section
import com.pandulapeter.campfire.presentation.resources.song_details_previous_song
import com.pandulapeter.campfire.presentation.resources.song_details_scroll_down
import com.pandulapeter.campfire.presentation.resources.song_details_scroll_to_top
import com.pandulapeter.campfire.presentation.resources.song_details_scroll_up
import com.pandulapeter.campfire.presentation.resources.song_details_song_position
import com.pandulapeter.campfire.presentation.resources.song_details_text_size
import com.pandulapeter.campfire.presentation.resources.song_details_transposition
import com.pandulapeter.campfire.presentation.ui.CampfireViewModel
import com.pandulapeter.campfire.presentation.ui.components.ACTION_BUTTON_OVERLAP
import com.pandulapeter.campfire.presentation.ui.components.ActionsMenu
import com.pandulapeter.campfire.presentation.ui.components.CampfireTopAppBar
import com.pandulapeter.campfire.presentation.ui.components.CoverArtImage
import com.pandulapeter.campfire.presentation.ui.components.DelayedLoadingIndicator
import com.pandulapeter.campfire.presentation.ui.components.EDGE_FADE_SIZE
import com.pandulapeter.campfire.presentation.ui.components.EmptyState
import com.pandulapeter.campfire.presentation.ui.components.EmptyStateAction
import com.pandulapeter.campfire.presentation.ui.components.fadingTopEdge
import com.pandulapeter.campfire.presentation.ui.components.overlappingAction
import com.pandulapeter.campfire.presentation.ui.components.SetlistAssignmentsButton
import com.pandulapeter.campfire.presentation.ui.components.SongActions
import com.pandulapeter.campfire.presentation.ui.navigation.CampfireDestination
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import org.jetbrains.compose.resources.painterResource

/**
 * The lyrics (and chords) of a song, or of a setlist's songs in a pager. The transposition and the text size are
 * adjusted with steppers in the app bar: the transposition is one of the bar's controls wherever it has the room
 * ([showsTranspositionInBar]) and the text size never is, except in performance mode, where it is the only control left
 * and the bar has nothing else to hold ([showsFontScaleInPerformanceBar]). Whatever the bar has no room for is a row at
 * the end of its overflow menu instead ([MenuStepperRow]). The text size can
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
    urlOpener: (String) -> Unit,
    onBack: () -> Unit,
) {
    val songsByFileName by viewModel.songsByFileName.collectAsStateWithLifecycle()
    val setlists by viewModel.setlists.collectAsStateWithLifecycle()
    val songTexts by viewModel.songTexts.collectAsStateWithLifecycle()
    val failedSongFileNames by viewModel.failedSongFileNames.collectAsStateWithLifecycle()
    val transpositions by viewModel.transpositions.collectAsStateWithLifecycle()
    val userPreferences by viewModel.userPreferences.collectAsStateWithLifecycle()
    // Only what is drawn over the screen, which the keys are then meant for instead.
    val visibleDialog by viewModel.visibleDialog.collectAsStateWithLifecycle()
    val isPerformanceModeEnabled by viewModel.isPerformanceModeEnabled.collectAsStateWithLifecycle()
    val songsBeingRenamed by viewModel.songsBeingRenamed.collectAsStateWithLifecycle()
    val songFileNamesInSetlists by viewModel.songFileNamesInSetlists.collectAsStateWithLifecycle()
    val songs = remember(destination, songsByFileName, songsBeingRenamed) {
        // A song whose file is being renamed is still this screen's song. The library drops the old name as the file
        // moves and the back stack is rewritten a few writes later, and in between the destination names a song the
        // library does not hold: resolving it to the song as it was keeps the pages - and their count - exactly
        // where they are, instead of this screen closing itself or a setlist settling on the next song.
        destination.songFileNames.mapNotNull { songsByFileName[it] ?: songsBeingRenamed[it] }
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
    val shouldShowChords = userPreferences?.isLyricsOnlyModeEnabled != true
    val isOneRowAtATimeEnabled = userPreferences?.isOneRowAtATimeEnabled == true
    val isCoverArtEnabled = userPreferences?.isCoverArtEnabled == true
    val chordSpelling = userPreferences?.chordSpelling ?: UserPreferences.ChordSpelling.Default
    val layoutDirection = LocalLayoutDirection.current
    val appBarWidth = settledWidth - contentPadding.calculateStartPadding(layoutDirection) - contentPadding.calculateEndPadding(layoutDirection)
    // Decided from the settled width and for every song of the pager at once, like the song actions below, so that the
    // cover and the steppers do not come and go during a navigation transition or a page change.
    val showsCoverInBar = isCoverArtEnabled && (!isPerformanceModeEnabled || showsCoverInPerformanceMode(appBarWidth))
    val showsFontScaleInBar = isPerformanceModeEnabled && showsFontScaleInPerformanceBar(appBarWidth)
    // Whatever else the bar holds: the back button with the bar's own start padding, the bar's end padding, the cover
    // in front of the title (reserved for every song of the pager, so that paging to a song without one does not move
    // the actions in and out of their menu), and the song's two buttons (the setlist assignments only for a song read
    // from the library).
    val otherAppBarContentWidth = APP_BAR_NAVIGATION_WIDTH + APP_BAR_END_PADDING + when {
        destination.setlistFileName == null -> APP_BAR_ACTION_WIDTH * 2
        else -> APP_BAR_ACTION_WIDTH
    } + if (showsCoverInBar && songs.any { it.coverArtUrl != null }) APP_BAR_COVER_SIZE + APP_BAR_COVER_GAP else 0.dp
    // The transposition comes out of the menu before any of the song's own actions do: it is what is reached for while a
    // song is being read, where the actions are about the file. The room is kept for it whether the song on screen has
    // chords or not, so that paging between the two does not move the actions in and out of their menu.
    val showsTranspositionInBar = !isPerformanceModeEnabled && showsTranspositionInBar(appBarWidth, otherAppBarContentWidth)
    // The song's own actions come out of their menu only as far as they leave the title
    // MIN_TITLE_WIDTH_BESIDE_SONG_ACTIONS: every one of them is a click further away in the menu, and they are several -
    // a title cut to its first words to make room for Export is a bar that has stopped saying which song it is. Worked
    // out from the settled width, so that the buttons do not come and go while a navigation transition runs. The
    // overflow button is counted among the other content above, and is what SongActions is handed the room for too.
    val songActionsMaxWidth = (
        appBarWidth - otherAppBarContentWidth + APP_BAR_ACTION_WIDTH - MIN_TITLE_WIDTH_BESIDE_SONG_ACTIONS -
            if (showsTranspositionInBar) TRANSPOSITION_STEPPER_WIDTH else 0.dp
        ).coerceAtLeast(APP_BAR_ACTION_WIDTH)

    val coroutineScope = rememberCoroutineScope()
    val pageStepper = remember(pagerState, coroutineScope) { PageStepper(pagerState, coroutineScope) }
    // The arrow keys scroll the song the reader is looking at, and the pages each scroll on their own, so the one
    // that is current hands its state up here for them to drive.
    var currentPageScrollState by remember { mutableStateOf<ScrollState?>(null) }
    // The step buttons, and the keys that press them, step between the sections or the rows of the song being read, for
    // the same reason.
    var currentPageStepper by remember { mutableStateOf<SongStepper?>(null) }
    // The steps go on to the song beside this one at either end of it, worked out from the page being headed for like the
    // pager bar's buttons. Both are decided again at the time of the press.
    val hasPreviousSongToStepTo = canPage && pagerState.targetPage > 0
    val hasNextSongToStepTo = canPage && pagerState.targetPage < songs.lastIndex
    val canStepBackInSong = currentPageStepper?.canStepBack == true
    val canStepForwardInSong = currentPageStepper?.canStepForward == true
    fun stepBack() {
        val stepper = currentPageStepper
        if (stepper?.canStepBack == true) coroutineScope.launch { stepper.step(-1) } else if (pagerState.targetPage > 0) pageStepper.step(-1)
    }
    fun stepForward() {
        val stepper = currentPageStepper
        if (stepper?.canStepForward == true) coroutineScope.launch { stepper.step(1) } else if (pagerState.targetPage < songs.lastIndex) pageStepper.step(1)
    }

    LaunchedEffect(currentSong?.fileName) { currentSong?.fileName?.let(viewModel::loadSongContent) }
    // The pages next to the current one are composed ahead of time (beyondViewportPageCount), so their text is read
    // ahead of time too: a swipe then lands on lyrics rather than on a loading indicator.
    LaunchedEffect(pagerState.currentPage, songs) {
        listOfNotNull(songs.getOrNull(pagerState.currentPage - 1), songs.getOrNull(pagerState.currentPage + 1))
            .filter { it.fileName !in songTexts && it.fileName !in failedSongFileNames }
            .forEach { viewModel.loadSongContent(it.fileName) }
    }

    Column(
        modifier = modifier
            .fillMaxSize()
            .keepScreenOn()
            .songKeyboardShortcuts(
                onScrollUp = { currentPageScrollState?.let { coroutineScope.launch { it.scrollByKeyStep(-1f) } } },
                onScrollDown = { currentPageScrollState?.let { coroutineScope.launch { it.scrollByKeyStep(1f) } } },
                // Whatever the step buttons would do where they are there, so that a pedal pressing Up and Down reads the
                // song the way the buttons do; where they are not, the keys scroll.
                onStepBack = if (canStepBackInSong || hasPreviousSongToStepTo) ::stepBack else null,
                onStepForward = if (canStepForwardInSong || hasNextSongToStepTo) ::stepForward else null,
                isUncovered = visibleDialog == null && viewModel.backStack.lastOrNull() is CampfireDestination.SongDetails,
                // The target page only decides whether the key does anything; the step itself is decided at the time of
                // the press.
                onPreviousSong = if (canPage && pagerState.targetPage > 0) {
                    { pageStepper.step(-1) }
                } else {
                    null
                },
                onNextSong = if (canPage && pagerState.targetPage < songs.lastIndex) {
                    { pageStepper.step(1) }
                } else {
                    null
                },
            )
    ) {
        CampfireTopAppBar(
            navigationIcon = {
                IconButton(onClick = onBack) {
                    Icon(
                        painter = painterResource(Res.drawable.ic_back),
                        contentDescription = stringResource(Res.string.back),
                    )
                }
            },
            title = {
                AnimatedContent(
                    modifier = Modifier.titleTouchTarget(
                        isEnabled = currentSong != null,
                        onClickLabel = stringResource(Res.string.song_details_scroll_to_top),
                        onClick = { currentPageScrollState?.let { coroutineScope.launch { it.animateScrollTo(0) } } },
                    ),
                    targetState = currentSong,
                    transitionSpec = { fadeIn() togetherWith fadeOut() },
                ) { song ->
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        song?.coverArtUrl?.let { url ->
                            // Only a window resized across the width that makes room for it animates: one that opens
                            // the screen with the cover out or in shows it that way from its first frame.
                            AnimatedVisibility(
                                visible = showsCoverInBar,
                                enter = fadeIn() + expandHorizontally(),
                                exit = fadeOut() + shrinkHorizontally(),
                            ) {
                                CoverArtImage(
                                    modifier = Modifier.padding(end = APP_BAR_COVER_GAP).size(APP_BAR_COVER_SIZE),
                                    url = url,
                                )
                            }
                        }
                        Column {
                            Text(
                                text = song?.title.orEmpty(),
                                style = MaterialTheme.typography.titleMedium,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                            )
                            Text(
                                text = song?.artist.orEmpty(),
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                            )
                        }
                    }
                }
            },
            actions = {
                // Performance mode leaves the bar with nothing else in it, and the text size is the one setting left
                // to it, so it is in the bar wherever the title leaves it the room, and alone in a menu where not.
                // Both are there, one leaving as the other arrives, so that a window resized across the width that
                // decides it hands the stepper over the way the song's actions move in and out of their menu.
                AnimatedVisibility(
                    visible = isPerformanceModeEnabled && showsFontScaleInBar,
                    enter = fadeIn() + expandHorizontally(),
                    exit = fadeOut() + shrinkHorizontally(),
                ) {
                    LiveFontScaleControls(
                        modifier = Modifier.padding(end = APP_BAR_STEPPER_END_PADDING),
                        viewModel = viewModel,
                    )
                }
                AnimatedVisibility(
                    visible = isPerformanceModeEnabled && !showsFontScaleInBar,
                    enter = fadeIn() + expandHorizontally(),
                    exit = fadeOut() + shrinkHorizontally(),
                ) {
                    ActionsMenu(
                        items = emptyList(),
                        menuFooter = {
                            MenuStepperRow(label = stringResource(Res.string.song_details_text_size)) {
                                LiveFontScaleControls(viewModel = viewModel)
                            }
                        },
                    )
                }
                currentSong?.takeIf { !isPerformanceModeEnabled }?.let { song ->
                    val isTranspositionShown = shouldShowChords && song.hasChords
                    val transposition = transpositions[song.fileName, destination.setlistFileName]
                    AnimatedVisibility(
                        visible = showsTranspositionInBar && isTranspositionShown,
                        enter = fadeIn() + expandHorizontally(),
                        exit = fadeOut() + shrinkHorizontally(),
                    ) {
                        SongTranspositionControls(
                            viewModel = viewModel,
                            song = song,
                            setlistFileName = destination.setlistFileName,
                            transposition = transposition,
                            chordSpelling = chordSpelling,
                        )
                    }
                    // Only a song read from the library gets the button: one read from a setlist is already filed, and
                    // is taken out of it from the setlist's own row.
                    val isReadFromLibrary = destination.setlistFileName == null
                    if (isReadFromLibrary) {
                        SetlistAssignmentsButton(
                            viewModel = viewModel,
                            song = song,
                            isInSetlist = song.fileName in songFileNamesInSetlists,
                        )
                    }
                    SongActions(
                        modifier = Modifier
                            .overlappingAction(start = if (isReadFromLibrary) ACTION_BUTTON_OVERLAP else 0.dp, end = 0.dp)
                            .widthIn(max = songActionsMaxWidth),
                        viewModel = viewModel,
                        song = song,
                        isExpandable = true,
                        isEditAlwaysInMenu = !isReadFromLibrary,
                        isDeletable = isReadFromLibrary,
                        menuFooter = {
                            MenuStepperRow(label = stringResource(Res.string.song_details_text_size)) {
                                LiveFontScaleControls(viewModel = viewModel)
                            }
                            if (!showsTranspositionInBar && isTranspositionShown) {
                                MenuStepperRow(label = stringResource(Res.string.song_details_transposition)) {
                                    SongTranspositionControls(
                                        viewModel = viewModel,
                                        song = song,
                                        setlistFileName = destination.setlistFileName,
                                        transposition = transposition,
                                        chordSpelling = chordSpelling,
                                    )
                                }
                            }
                        },
                    )
                }
            },
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
        if (songs.isEmpty()) {
            // The library has not been read yet (or the screen is on its way out, see above), so there is nothing to page
            // through; a pager with no pages would leave the screen blank under an app bar with no title in it.
            Box(
                modifier = Modifier.weight(1f).fillMaxWidth().padding(contentPadding),
                contentAlignment = Alignment.Center,
            ) {
                DelayedLoadingIndicator()
            }
        } else {
            // The step buttons are drawn over the pager rather than over each page, so there is one pair of them that
            // stays where it is while the songs slide past under it.
            Box(
                modifier = Modifier
                    .weight(1f)
                    .fillMaxWidth(),
            ) {
                HorizontalPager(
                    modifier = Modifier
                        .fillMaxSize()
                        .fontScaleGestures(
                            fontScale = { viewModel.fontScale },
                            onFontScaleChanged = viewModel::setFontScale,
                        ),
                    state = pagerState,
                    key = { songs[it].fileName },
                    beyondViewportPageCount = 1,
                ) { page ->
                    val song = songs[page]
                    val scrollState = rememberScrollState()
                    val flingBehavior = rememberRowSnapFlingBehavior(scrollState)
                    val stepper = remember(scrollState, flingBehavior) { SongStepper(scrollState, flingBehavior) }
                    if (page == pagerState.currentPage) SideEffect {
                        currentPageScrollState = scrollState
                        currentPageStepper = stepper
                    }
                    // Only the song being read follows a pinch frame by frame; the pages beside it are composed and laid out
                    // too, and take the scale once it has settled. A swipe makes its page the target, which follows at once.
                    val isFollowingGesture = page == pagerState.currentPage || page == pagerState.targetPage
                    val text = songTexts[song.fileName]
                    // The first swap of a page from loading to its lyrics lays the whole song out in one frame, so a page
                    // that is neither on screen nor being swiped to is held back from it until the pager has come to rest,
                    // or it would land in the middle of the settle animation. A page shows its lyrics once and keeps them,
                    // a newer text included; only that first swap waits.
                    var hasShownLyrics by remember { mutableStateOf(text != null) }
                    val shownText = if (!hasShownLyrics && !isFollowingGesture && pagerState.isScrollInProgress) null else text
                    if (shownText != null && !hasShownLyrics) SideEffect { hasShownLyrics = true }
                    SongDetailsPage(
                        song = song,
                        scrollState = scrollState,
                        flingBehavior = flingBehavior,
                        text = shownText,
                        hasFailed = song.fileName in failedSongFileNames,
                        transposition = transpositions[song.fileName, destination.setlistFileName],
                        shouldShowChords = shouldShowChords,
                        fontScale = if (isFollowingGesture) ({ viewModel.fontScale }) else ({ viewModel.settledFontScale }),
                        // In a setlist the step buttons page to the songs beside every song, whether it scrolls or not.
                        keepsStepButtonInset = canPage,
                        isOneRowAtATimeEnabled = isOneRowAtATimeEnabled,
                        // One set per song, wherever it is opened from: folding is how this reader reads it, not how the
                        // setlist has the band play it.
                        foldedSections = userPreferences?.foldedSections?.get(song.fileName).orEmpty(),
                        onFoldToggled = { key -> viewModel.toggleSectionFold(songFileName = song.fileName, key = key) },
                        chordSpelling = chordSpelling,
                        settledWidth = settledWidth,
                        contentPadding = pageContentPadding,
                        renderSong = viewModel::renderSong,
                        onRetry = { viewModel.loadSongContent(song.fileName) },
                        // Tagging writes the song's own file, so in performance mode the header's chips are read the way
                        // the editor's preview reads them.
                        onManageTags = if (isPerformanceModeEnabled) null else {
                            { viewModel.showDialog(CampfireViewModel.DialogType.SongTags(song = song)) }
                        },
                        onRemoveTag = if (isPerformanceModeEnabled) null else {
                            { tag -> viewModel.removeSongTag(fileName = song.fileName, tag = tag) }
                        },
                        onEditLanguages = if (isPerformanceModeEnabled) null else {
                            { viewModel.showDialog(CampfireViewModel.DialogType.SongLanguages(song)) }
                        },
                        // Following a link reads the file rather than writing it, so it is the one chip that still acts in
                        // performance mode.
                        onOpenLink = urlOpener,
                        onAddLink = if (isPerformanceModeEnabled) null else {
                            { viewModel.showDialog(CampfireViewModel.DialogType.AddSongLink(song = song)) }
                        },
                        onRemoveLink = if (isPerformanceModeEnabled) null else {
                            { url -> viewModel.setSongLink(fileName = song.fileName, url = url, isAdded = false) }
                        },
                        onEditCoverArt = if (isPerformanceModeEnabled || !isCoverArtEnabled) null else {
                            { viewModel.showDialog(CampfireViewModel.DialogType.CoverArtSearch(song)) }
                        },
                        // Performance mode takes every way into the editor out of the app, this one included.
                        onOpenEditor = if (isPerformanceModeEnabled) null else {
                            { viewModel.openEditor(song.fileName) }
                        },
                    )
                }
                StepButtons(
                    isSteppedByRow = currentPageStepper?.isSteppedByRow == true,
                    isPagingBack = currentPageStepper?.isPagingBack == true,
                    isPagingForward = currentPageStepper?.isPagingForward == true,
                    canStepBackInSong = canStepBackInSong,
                    canStepForwardInSong = canStepForwardInSong,
                    hasPreviousSong = hasPreviousSongToStepTo,
                    hasNextSong = hasNextSongToStepTo,
                    contentPadding = PaddingValues(
                        top = PAGE_TOP_PADDING + STEP_BUTTON_EDGE_MARGIN,
                        end = pageContentPadding.calculateEndPadding(layoutDirection) + 16.dp,
                        bottom = pageContentPadding.calculateBottomPadding() + STEP_BUTTON_EDGE_MARGIN,
                    ),
                    onStepBack = ::stepBack,
                    onStepForward = ::stepForward,
                )
            }
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
 * The bar under the lyrics that steps through the songs of a setlist without swiping. Between the two buttons it
 * names the setlist being played and how far along it the current song is.
 *
 * @param setlistSlots Where each page sits in the setlist, which is what the label numbers it by; null where the
 *   pages are numbered by themselves.
 * @param currentPage The song on screen, which is what the label names.
 * @param targetPage The song the pager is on its way to, which is what decides whether there is anywhere left to step.
 */
@Composable
private fun SongPagerControls(
    setlistTitle: String?,
    setlistSlots: SetlistSlots?,
    currentPage: Int,
    targetPage: Int,
    pageCount: Int,
    contentPadding: PaddingValues,
    onStep: (Int) -> Unit,
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
                bottom = contentPadding.calculateBottomPadding(),
            )
            .height(PAGER_CONTROLS_HEIGHT),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        IconButton(
            enabled = targetPage > 0,
            onClick = { onStep(-1) },
        ) {
            Icon(
                painter = painterResource(Res.drawable.ic_previous),
                contentDescription = stringResource(Res.string.song_details_previous_song),
            )
        }
        // The label takes whatever the two buttons leave. Only the setlist name gives way when that is not enough:
        // the position is short and always worth showing in full.
        Row(
            modifier = Modifier.weight(1f),
            horizontalArrangement = Arrangement.Center,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            if (setlistTitle != null) {
                Text(
                    modifier = Modifier.weight(1f, fill = false),
                    text = setlistTitle,
                    style = MaterialTheme.typography.labelLarge,
                    textAlign = TextAlign.Center,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                Text(
                    modifier = Modifier.padding(horizontal = 8.dp),
                    text = LABEL_SEPARATOR,
                    style = MaterialTheme.typography.labelLarge,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            AnimatedContent(
                targetState = currentPage,
                transitionSpec = { fadeIn() togetherWith fadeOut() },
            ) { page ->
                val position = setlistSlots?.slotByPage?.getOrNull(page)
                Text(
                    text = if (position != null) {
                        stringResource(Res.string.song_details_song_position, position + 1, setlistSlots.entryCount)
                    } else {
                        stringResource(Res.string.song_details_song_position, page + 1, pageCount)
                    },
                    style = MaterialTheme.typography.labelLarge,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    textAlign = TextAlign.Center,
                )
            }
        }
        IconButton(
            enabled = targetPage < pageCount - 1,
            onClick = { onStep(1) },
        ) {
            Icon(
                painter = painterResource(Res.drawable.ic_next),
                contentDescription = stringResource(Res.string.song_details_next_song),
            )
        }
    }
}

/**
 * @param text The ChordPro text of the song, null while it is still being read.
 * @param hasFailed Whether the file could not be read. The page then offers a retry rather than a loading indicator
 *   that has nothing left to wait for.
 * @param scrollState Owned by the pager rather than by the page, so that the arrow keys can reach the scroll of the
 *   song being read, and so that a page keeps where it was left while its text is loaded again.
 * @param fontScale Read where the lyrics are built rather than passed as a value: a pinch changes it on every frame,
 *   and read here it invalidates only this page's content rather than the screen and the pager around it.
 */
@Composable
private fun SongDetailsPage(
    song: Song,
    scrollState: ScrollState,
    flingBehavior: RowSnapFlingBehavior,
    text: String?,
    hasFailed: Boolean,
    transposition: Int,
    shouldShowChords: Boolean,
    fontScale: () -> Float,
    keepsStepButtonInset: Boolean,
    isOneRowAtATimeEnabled: Boolean,
    foldedSections: Set<String>,
    onFoldToggled: (key: String) -> Unit,
    chordSpelling: UserPreferences.ChordSpelling,
    settledWidth: Dp,
    contentPadding: PaddingValues,
    renderSong: (text: String, transposition: Int, spelling: UserPreferences.ChordSpelling) -> ChordProSong,
    onRetry: () -> Unit,
    onManageTags: (() -> Unit)?,
    onRemoveTag: ((String) -> Unit)?,
    onEditLanguages: (() -> Unit)?,
    onOpenLink: (String) -> Unit,
    onAddLink: (() -> Unit)?,
    onRemoveLink: ((String) -> Unit)?,
    onEditCoverArt: (() -> Unit)?,
    onOpenEditor: (() -> Unit)?,
) = AnimatedContent(
    modifier = Modifier.fillMaxSize(),
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
        val labels = rememberDefaultSectionLabels()
        val model = rememberSongLyricsModel(
            inputs = SongLyricsInputs(
                text = songText,
                transposition = transposition,
                spelling = chordSpelling,
                shouldShowChords = shouldShowChords,
                labels = labels,
            ),
        ) { inputs ->
            prepareSongLyrics(
                song = renderSong(inputs.text, inputs.transposition, inputs.spelling),
                shouldShowChords = inputs.shouldShowChords,
                labels = inputs.labels,
            )
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
        val bottomPadding = contentPadding.calculateBottomPadding() + 32.dp
        // The lyrics scroll, so they need to be told from the outside how much room there is for them without
        // scrolling: that is what decides how many columns they are flowed into.
        BoxWithConstraints(
            modifier = Modifier.fillMaxSize()
        ) {
            val currentFontScale = fontScale()
            // A single change - a window maximised, a stepper tapped - springs the sections to their new place; a burst
            // of them, from a pinch or a window edge being dragged, is followed, gliding only over the grid's jumps.
            val isChangingContinuously = rememberContinuousChange(maxWidth, currentFontScale)
            val density = LocalDensity.current
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
                    .fadingTopEdge(scrollState, MaterialTheme.colorScheme.background)
                    .verticalScroll(state = scrollState, flingBehavior = flingBehavior)
                    .padding(
                        start = contentPadding.calculateStartPadding(layoutDirection) + 16.dp,
                        end = contentPadding.calculateEndPadding(layoutDirection) + 16.dp,
                        top = topPadding,
                        bottom = bottomPadding,
                    ),
                model = model,
                availableHeight = maxHeight - topPadding - bottomPadding,
                // The pages fill the screen, so whatever the screen is still missing this layout is missing too.
                extraWidth = (settledWidth - maxWidth).coerceAtLeast(0.dp),
                sectionMotion = if (isChangingContinuously) SectionMotion.GLIDE else SectionMotion.SPRING,
                fontScale = currentFontScale,
                foldedSections = foldedSections,
                onFoldToggled = onFoldToggled,
                onManageTags = onManageTags,
                onRemoveTag = onRemoveTag,
                onEditLanguages = onEditLanguages,
                onOpenLink = onOpenLink,
                onAddLink = onAddLink,
                onRemoveLink = onRemoveLink,
                onEditCoverArt = onEditCoverArt,
                onOpenEditor = onOpenEditor,
                // The padding is inside the scroll, so a row is at the top of the viewport once the song is scrolled by
                // its position plus the padding above it.
                onRowsPlaced = { rows ->
                    val offsetRows = rows.offsetBy(topPaddingPx)
                    if (offsetRows != flingBehavior.rows) flingBehavior.rows = offsetRows
                },
                rowViewportHeight = maxHeight,
                isOneRowAtATime = isOneRowAtATimeEnabled,
                rowViewportBottomPadding = bottomPadding,
                // The buttons are as far from the end of the screen as the text is, so the text only has to leave
                // them their own width and a gap.
                stepButtonInset = STEP_BUTTON_SIZE + STEP_BUTTON_GAP,
                keepsStepButtonInset = keepsStepButtonInset,
            )
        }
    }
}

/**
 * The two buttons that step through the song being read: between its rows where it is read across the columns and
 * scrolls, however few of them there are, and between its sections everywhere else, the header above them counting as one,
 * paging through a row or a section taller than the screen on the way ([isPagingBack], [isPagingForward], which only
 * change what they are called). The previous one is at the top of the end edge, the next one at its bottom, each there
 * only for as long as there is something of the song to step to in its direction. In a setlist they go on to the song beside this one where there is nothing left in their
 * direction ([hasPreviousSong], [hasNextSong]), and turn to point the way the pager goes to say so; they only leave at
 * the ends of the setlist. There is one pair for the whole pager, answering whichever song is current, rather than one
 * on every page sliding past with it.
 *
 * They are drawn over the song, which leaves them that edge of the screen wherever it has to be scrolled
 * (`SongLyrics`' `stepButtonInset`), so neither ever covers a line of it; the header leaves it too.
 */
@Composable
private fun BoxScope.StepButtons(
    isSteppedByRow: Boolean,
    isPagingBack: Boolean,
    isPagingForward: Boolean,
    canStepBackInSong: Boolean,
    canStepForwardInSong: Boolean,
    hasPreviousSong: Boolean,
    hasNextSong: Boolean,
    contentPadding: PaddingValues,
    onStepBack: () -> Unit,
    onStepForward: () -> Unit,
) {
    val layoutDirection = LocalLayoutDirection.current
    // A quarter turn one way makes the arrow pointing down point to where the next song is, and the arrow pointing up to
    // where the previous one is, whichever way the layout reads.
    val pagingRotation = if (layoutDirection == LayoutDirection.Ltr) -90f else 90f
    StepButton(
        modifier = Modifier
            .align(Alignment.TopEnd)
            .padding(top = contentPadding.calculateTopPadding(), end = contentPadding.calculateEndPadding(layoutDirection)),
        isVisible = canStepBackInSong || hasPreviousSong,
        icon = painterResource(Res.drawable.ic_move_up),
        iconRotation = if (canStepBackInSong) 0f else pagingRotation,
        contentDescription = stringResource(
            when {
                !canStepBackInSong -> Res.string.song_details_previous_song
                isPagingBack -> Res.string.song_details_scroll_up
                isSteppedByRow -> Res.string.song_details_previous_row
                else -> Res.string.song_details_previous_section
            },
        ),
        onClick = onStepBack,
    )
    StepButton(
        modifier = Modifier
            .align(Alignment.BottomEnd)
            .padding(bottom = contentPadding.calculateBottomPadding(), end = contentPadding.calculateEndPadding(layoutDirection)),
        isVisible = canStepForwardInSong || hasNextSong,
        icon = painterResource(Res.drawable.ic_move_down),
        iconRotation = if (canStepForwardInSong) 0f else pagingRotation,
        contentDescription = stringResource(
            when {
                !canStepForwardInSong -> Res.string.song_details_next_song
                isPagingForward -> Res.string.song_details_scroll_down
                isSteppedByRow -> Res.string.song_details_next_row
                else -> Res.string.song_details_next_section
            },
        ),
        onClick = onStepForward,
    )
}

@Composable
private fun StepButton(
    modifier: Modifier = Modifier,
    isVisible: Boolean,
    icon: Painter,
    iconRotation: Float,
    contentDescription: String,
    onClick: () -> Unit,
) = AnimatedVisibility(
    modifier = modifier,
    visible = isVisible,
    enter = scaleIn() + fadeIn(),
    exit = scaleOut() + fadeOut(),
) {
    // The arrow only turns on a button that stays: at the end of a song or a setlist the direction changes in the very
    // frame the button starts leaving, and it would otherwise spin as it scales away. One arriving is composed afresh
    // and comes already pointing where it has to.
    val rotation = remember { Animatable(iconRotation) }
    val isStaying = transition.targetState == EnterExitState.Visible
    LaunchedEffect(iconRotation, isStaying) {
        if (isStaying) rotation.animateTo(iconRotation)
    }
    SmallFloatingActionButton(
        // A button that has the focus takes it away with it as it leaves, which leaves nothing on the screen focused
        // and every key after that to Compose's own focus search, which scrolls the song instead of stepping it. They
        // are pressed by the same keys the screen hears anyway.
        modifier = Modifier
            .size(STEP_BUTTON_SIZE)
            .focusProperties { canFocus = false },
        onClick = onClick,
    ) {
        Icon(
            modifier = Modifier.rotate(rotation.value),
            painter = icon,
            contentDescription = contentDescription,
        )
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

/** What [rememberContinuousChange] remembers between changes. Not state, since only its effect reads it. */
private class ContinuousChangeTracker {
    var isFirst = true
    var isRecent = false
}

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

/**
 * The app bar's title as the touch target that scrolls the song back to its top, the way tapping the bar does on a
 * phone. It is the whole of the bar that nothing else takes: as wide as the room the bar leaves the title and as tall
 * as the bar, so that a tap anywhere on its empty part scrolls. The bar pads that room by [TITLE_TOUCH_HORIZONTAL_OUTSET]
 * at either end and centers the title in its height, and the target reaches out over both while reporting only the room
 * itself, so the bar lays out the back button, the title and the actions exactly as it would without it. The outset
 * stops where the touch targets of the back button and the actions begin: the bar places the title after the back
 * button, so a target reaching any further would take its presses from it. It draws no indication, since a press
 * lighting up most of the bar reads as the bar itself reacting rather than as a button.
 */
private fun Modifier.titleTouchTarget(
    isEnabled: Boolean,
    onClickLabel: String,
    onClick: () -> Unit,
) = layout { measurable, constraints ->
    val horizontalOutset = TITLE_TOUCH_HORIZONTAL_OUTSET.roundToPx()
    val verticalOutset = TITLE_TOUCH_VERTICAL_OUTSET.roundToPx()
    val placeable = measurable.measure(constraints.offset(horizontal = horizontalOutset * 2, vertical = verticalOutset * 2))
    layout(placeable.width - horizontalOutset * 2, placeable.height - verticalOutset * 2) {
        placeable.placeRelative(-horizontalOutset, -verticalOutset)
    }
}
    .clickable(
        interactionSource = null,
        indication = null,
        enabled = isEnabled,
        onClickLabel = onClickLabel,
        onClick = onClick,
    )
    .padding(horizontal = TITLE_TOUCH_HORIZONTAL_OUTSET, vertical = TITLE_TOUCH_VERTICAL_OUTSET)
    .fillMaxWidth()

/**
 * Previous and Next as steps from the page the last of them asked for, rather than from the page on screen:
 * `PagerState.currentPage` only moves once an animation is past halfway, and even `PagerState.targetPage` only once the
 * launched animation has started, so presses that come quicker than that would each ask for the same page. A request
 * is forgotten when its animation ends, however it ends (a swipe cancels it), and whatever the pager then settles on
 * is where the next press starts from.
 *
 * Only touched from the main thread - key and click handlers, and the coroutine on the composition's dispatcher - so a
 * plain field is enough. The request is an object rather than the page number, so that two requests for the same page
 * are still told apart.
 */
private class PageStepper(
    private val pagerState: PagerState,
    private val coroutineScope: CoroutineScope,
) {
    private var request: Request? = null

    private class Request(val page: Int)

    fun step(delta: Int) {
        val from = request?.page ?: pagerState.targetPage
        val page = (from + delta).coerceIn(0, pagerState.pageCount - 1)
        if (page == from) return
        val request = Request(page).also { request = it }
        coroutineScope.launch {
            try {
                pagerState.animateScrollToPage(page)
            } finally {
                // Only the latest request is cleared: an earlier one ends when the next press cancels it.
                if (this@PageStepper.request === request) this@PageStepper.request = null
            }
        }
    }
}

/** Where each page of a setlist's pager sits in the setlist, see [SongPagerControls]. */
internal data class SetlistSlots(
    val slotByPage: List<Int>,
    val entryCount: Int,
)

/** A missing page invalidates the numbering, while absent entries still count toward the total. */
internal fun buildSetlistSlots(entries: List<Setlist.Entry>, songFileNames: List<String>): SetlistSlots? {
    val entryIndexByFileName = mutableMapOf<String, Int>()
    entries.forEachIndexed { index, entry ->
        if (entry.songFileName !in entryIndexByFileName) entryIndexByFileName[entry.songFileName] = index
    }
    val slotByPage = songFileNames.map { entryIndexByFileName[it] ?: return null }
    return SetlistSlots(slotByPage = slotByPage, entryCount = entries.size)
}

/**
 * Whether the app bar of a screen [appBarWidth] wide has room for the text size stepper in performance mode, where it is
 * the only control the bar holds: wherever it leaves the title [MIN_TITLE_WIDTH], since the title is what tells the
 * player which song is up. Where it does not, the stepper is in a menu of its own. The cover is not counted, since it
 * is the first to leave ([showsCoverInPerformanceMode]).
 */
internal fun showsFontScaleInPerformanceBar(appBarWidth: Dp) = appBarWidth - APP_BAR_NAVIGATION_WIDTH - APP_BAR_END_PADDING -
    STEPPER_WIDTH - APP_BAR_STEPPER_END_PADDING >= MIN_TITLE_WIDTH

/**
 * Whether the app bar of a screen [appBarWidth] wide still has room for the cover in performance mode. The cover is
 * decoration there, so it is only shown next to the text size stepper ([showsFontScaleInPerformanceBar]) and only where
 * the title is still left [MIN_TITLE_WIDTH] beside the two.
 */
internal fun showsCoverInPerformanceMode(appBarWidth: Dp) = showsFontScaleInPerformanceBar(appBarWidth) &&
    appBarWidth - APP_BAR_NAVIGATION_WIDTH - APP_BAR_END_PADDING - STEPPER_WIDTH - APP_BAR_STEPPER_END_PADDING -
    APP_BAR_COVER_SIZE - APP_BAR_COVER_GAP >= MIN_TITLE_WIDTH

/**
 * Whether the app bar of a screen [appBarWidth] wide has room for the transposition stepper outside performance mode,
 * next to [otherContentWidth] of everything else the bar always holds: wherever the title is still left
 * [MIN_TITLE_WIDTH_BESIDE_SONG_ACTIONS], the room the song's own actions leave it too.
 */
internal fun showsTranspositionInBar(appBarWidth: Dp, otherContentWidth: Dp) = appBarWidth - otherContentWidth -
    TRANSPOSITION_STEPPER_WIDTH >= MIN_TITLE_WIDTH_BESIDE_SONG_ACTIONS

private const val LABEL_SEPARATOR = "·"
private val PAGE_TOP_PADDING = 8.dp // Inside the scroll, above the header.
private val STEP_BUTTON_SIZE = 40.dp
private val STEP_BUTTON_GAP = 8.dp
private val STEP_BUTTON_EDGE_MARGIN = 16.dp
private val PAGER_CONTROLS_HEIGHT = 48.dp

/**
 * What the text size stepper of performance mode leaves after it, the last thing in the bar. The transposition stepper
 * has none: a button always follows it, and that button's touch target already keeps its icon off the pill by as much
 * as two neighboring buttons keep their icons apart.
 */
private val APP_BAR_STEPPER_END_PADDING = 8.dp

/** The room kept for the transposition stepper, which is wider than [STEPPER_WIDTH] by the key after the amount. */
private val TRANSPOSITION_STEPPER_WIDTH = 140.dp
private val APP_BAR_NAVIGATION_WIDTH = 52.dp // The 48dp button and the 4dp the bar pads its start by.
private val APP_BAR_END_PADDING = 4.dp
private val APP_BAR_ACTION_WIDTH = 48.dp

/** As tall as the title and the artist next to it: a titleMedium and a bodySmall line. The editor's bar shares it. */
internal val APP_BAR_COVER_SIZE = 40.dp
internal val APP_BAR_COVER_GAP = 12.dp
private val MIN_TITLE_WIDTH_BESIDE_SONG_ACTIONS = 280.dp // About thirty characters, most titles whole.
private val MIN_TITLE_WIDTH = 160.dp // Enough of a title to tell which song is up.
private val TITLE_TOUCH_HORIZONTAL_OUTSET = 4.dp // The padding the bar puts around its title.
private val TITLE_TOUCH_VERTICAL_OUTSET = 12.dp // From the two lines of title, 40dp, to the bar's 64dp.
private const val KEY_SCROLL_STEP_FRACTION = 0.1f // Of the height of the scrolling viewport.
private const val KEY_SCROLL_STEP_DURATION = 120
private const val CONTINUOUS_CHANGE_MILLIS = 200L
