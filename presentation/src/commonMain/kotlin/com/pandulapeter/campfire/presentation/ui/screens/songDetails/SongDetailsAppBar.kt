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
import androidx.compose.animation.Crossfade
import androidx.compose.animation.core.MutableTransitionState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.expandHorizontally
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkHorizontally
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.ScrollState
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.calculateEndPadding
import androidx.compose.foundation.layout.calculateStartPadding
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBarScrollBehavior
import androidx.compose.runtime.Composable
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshots.SnapshotStateMap
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.PointerIcon
import androidx.compose.ui.input.pointer.pointerHoverIcon
import androidx.compose.ui.layout.layout
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.offset
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.pandulapeter.campfire.chordpro.ChordProDuration
import com.pandulapeter.campfire.data.model.domain.Song
import com.pandulapeter.campfire.data.model.domain.UserPreferences
import com.pandulapeter.campfire.metronome.api.model.MetronomePattern
import com.pandulapeter.campfire.presentation.localization.stringResource
import com.pandulapeter.campfire.presentation.resources.Res
import com.pandulapeter.campfire.presentation.resources.back
import com.pandulapeter.campfire.presentation.resources.ic_back
import com.pandulapeter.campfire.presentation.resources.ic_expand
import com.pandulapeter.campfire.presentation.resources.song_details_scroll_to_top
import com.pandulapeter.campfire.presentation.resources.song_details_song_info
import com.pandulapeter.campfire.presentation.resources.song_details_tempo
import com.pandulapeter.campfire.presentation.resources.song_details_text_size
import com.pandulapeter.campfire.presentation.resources.songs_key
import com.pandulapeter.campfire.presentation.ui.CampfireViewModel
import com.pandulapeter.campfire.presentation.ui.components.ACTION_BUTTON_OVERLAP
import com.pandulapeter.campfire.presentation.ui.components.ActionsMenu
import com.pandulapeter.campfire.presentation.ui.components.CampfireTopAppBar
import com.pandulapeter.campfire.presentation.ui.components.CoverArtImage
import com.pandulapeter.campfire.presentation.ui.components.PRESSED_HEADING_ALPHA
import com.pandulapeter.campfire.presentation.ui.components.SetlistAssignmentsButton
import com.pandulapeter.campfire.presentation.ui.components.SongActions
import com.pandulapeter.campfire.presentation.ui.components.SongEditingActions
import com.pandulapeter.campfire.presentation.ui.components.overlappingAction
import com.pandulapeter.campfire.presentation.ui.components.setlistAssignmentsAction
import com.pandulapeter.campfire.presentation.ui.components.textResource
import com.pandulapeter.campfire.presentation.ui.dialogs.SongEditTarget
import com.pandulapeter.campfire.presentation.ui.metronome.MetronomeButton
import com.pandulapeter.campfire.presentation.ui.metronome.MetronomePanel
import com.pandulapeter.campfire.presentation.ui.metronome.SongTiming
import com.pandulapeter.campfire.presentation.ui.metronome.metronomeAction
import com.pandulapeter.campfire.presentation.ui.navigation.CampfireDestination
import com.pandulapeter.campfire.presentation.ui.playing.songPlaybackOf
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch
import org.jetbrains.compose.resources.painterResource

/**
 * The song details screen's app bar: the way back, the title block that opens the sheet of what the song is, the song's
 * actions and the metronome panel under them. A composable of its own so that [SongDetailsScreen] stays small enough
 * for HotSpot to compile, which it never does for a method of more than 8,000 bytes of bytecode, and that one runs on
 * every page change. What only the bar reads is collected and decided here, so that a change of it recomposes the bar
 * rather than the whole screen.
 *
 * @param currentPageScrollState Read where it is used rather than handed down as a value, so that passing the top
 *   recomposes the title alone.
 * @param metronomePanelState Created by the screen, whose pages read whether the panel is at rest, see
 *   SongDetailsPage's isViewportSettled.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun SongDetailsAppBar(
    viewModel: CampfireViewModel,
    destination: CampfireDestination.SongDetails,
    currentSong: Song?,
    isReadOnly: Boolean,
    isPerformanceModeEnabled: Boolean,
    shouldShowChords: Boolean,
    isMetronomeEnabled: Boolean,
    areSetlistsEnabled: Boolean,
    isCoverArtEnabled: Boolean,
    chordSpelling: UserPreferences.ChordSpelling,
    songTimings: SnapshotStateMap<String, SongTiming>,
    currentPageScrollState: () -> ScrollState?,
    openCurrentSongInfo: (() -> Unit)?,
    songs: List<Song>,
    settledWidth: Dp,
    isMetronomePanelShown: Boolean,
    isBeatFlashEnabled: Boolean,
    isMetronomePanelVisible: Boolean,
    metronomePanelState: MutableTransitionState<Boolean>,
    scrollBehavior: TopAppBarScrollBehavior?,
    contentPadding: PaddingValues,
    coroutineScope: CoroutineScope,
    onBack: () -> Unit,
) {
    val playingOverrides by viewModel.playingOverrides.collectAsStateWithLifecycle()
    val songFileNamesInSetlists by viewModel.songFileNamesInSetlists.collectAsStateWithLifecycle()
    val layoutDirection = LocalLayoutDirection.current
    val appBarWidth = settledWidth - contentPadding.calculateStartPadding(layoutDirection) - contentPadding.calculateEndPadding(layoutDirection)
    // Decided from the settled width and for every song of the pager at once, like the song actions, so that the
    // cover and the steppers do not come and go during a navigation transition or a page change. The cover's room is
    // reserved for every song of the pager, so that paging to a song without one does not move the star in and out of
    // the menu.
    val appBarButtons = appBarButtons(appBarWidth = appBarWidth, hasCover = isCoverArtEnabled && songs.any { it.coverArtUrl != null })
    val showsCoverInBar = isCoverArtEnabled && if (isReadOnly) showsCoverInPerformanceMode(appBarWidth) else appBarButtons.isCoverShown
    val showsFontScaleInBar = isReadOnly && showsFontScaleInPerformanceBar(appBarWidth)
    val showsSetlistAssignmentsInBar = !isReadOnly && areSetlistsEnabled && appBarButtons.isSetlistAssignmentsShown
    // Read only, the button stands next to the text size stepper, which is all that bar holds; otherwise it is always
    // in the bar, see appBarButtons.
    val showsMetronomeInBar = !isReadOnly || showsMetronomeInPerformanceBar(appBarWidth)
    val currentTempo = currentSong?.let { songPlaybackOf(song = it, setlistFileName = destination.setlistFileName, overrides = playingOverrides).tempo }
    val metronomeButton: @Composable () -> Unit = {
        MetronomeButton(
            isPanelShown = isMetronomePanelShown,
            playback = viewModel.metronomePlayback,
            bpm = currentTempo?.bpm ?: MetronomePattern.DEFAULT_BPM,
            beats = viewModel.metronomeBeats,
            isFlashEnabled = isBeatFlashEnabled,
            onClick = viewModel::toggleMetronomePanel,
        )
    }
    val metronomeAction = if (isMetronomeEnabled) {
        metronomeAction(
            isPanelShown = isMetronomePanelShown,
            bpm = currentTempo?.bpm ?: MetronomePattern.DEFAULT_BPM,
            onClick = viewModel::toggleMetronomePanel,
        )
    } else {
        null
    }
    CampfireTopAppBar(
        scrollBehavior = scrollBehavior,
        navigationIcon = {
            IconButton(onClick = onBack) {
                Icon(
                    painter = painterResource(Res.drawable.ic_back),
                    contentDescription = stringResource(Res.string.back),
                )
            }
        },
        title = {
            // The title and the cover are the heading of the sheet of what the song is, so a tap on them opens it
            // once there is nothing left to scroll back to. The scroll is read here rather than in the screen's
            // own body, so that passing the top recomposes the bar's title alone.
            val isScrolledToTop by remember { derivedStateOf { (currentPageScrollState()?.value ?: 0) == 0 } }
            val openSongInfoAtTop = openCurrentSongInfo?.takeIf { isScrolledToTop }
            val titleInteractionSource = remember { MutableInteractionSource() }
            val isTitlePressed by titleInteractionSource.collectIsPressedAsState()
            val titleAlpha by animateFloatAsState(if (isTitlePressed) PRESSED_HEADING_ALPHA else 1f)
            AnimatedContent(
                modifier = Modifier
                    .titleTouchTarget(
                        isEnabled = currentSong != null && (openSongInfoAtTop != null || !isScrolledToTop),
                        interactionSource = titleInteractionSource,
                        onClickLabel = stringResource(if (openSongInfoAtTop == null) Res.string.song_details_scroll_to_top else Res.string.song_details_song_info),
                        onClick = {
                            if (openSongInfoAtTop == null) {
                                currentPageScrollState()?.let { coroutineScope.launch { it.animateScrollTo(0) } }
                            } else {
                                openSongInfoAtTop()
                            }
                        },
                    )
                    .graphicsLayer { alpha = titleAlpha },
                targetState = currentSong,
                // Keyed by the file, so that only paging to another song cross-fades the block: the same song
                // arriving again (a tag added, a tempo set, a sync run) is recomposed in place, and what changed in
                // it animates on its own below.
                contentKey = { it?.fileName },
                transitionSpec = { fadeIn() togetherWith fadeOut() },
            ) { song ->
                // What the song sounds like where it is being read, the way a song card says it: the key with the
                // transposition and the capo applied, so this is the key the band hears rather than the one the
                // chords on the page spell, and the tempo the click would play at. Worked out for the song this
                // content was composed for rather than for the current one, since a crossfade between two songs
                // draws both at once. Lyrics only mode says nothing about either, as it says nothing in a row.
                val headerKey = song?.takeIf { shouldShowChords && it.hasChords }?.let {
                    val playback = songPlaybackOf(song = it, setlistFileName = destination.setlistFileName, overrides = playingOverrides)
                    viewModel.songRenderer.renderKey(
                        song = it,
                        transposition = playback.transposition,
                        capo = playback.capo.fret,
                        spelling = chordSpelling,
                    )
                }
                // The stretch the page is on where the song changes its tempo further down, as the click plays it.
                val headerTempo = song
                    ?.takeIf { isMetronomeEnabled }
                    ?.let { songTimings[it.fileName]?.bpm ?: songPlaybackOf(song = it, setlistFileName = destination.setlistFileName, overrides = playingOverrides).tempo.displayedBpm }
                    ?.let { stringResource(Res.string.song_details_tempo, it.toString()) }
                // The duration only inside a setlist, as the song's card there says it, since a set is what is
                // timed by its songs; and, as there, with the chords switched off too, since the singer is timed by it alike.
                val headerDuration = song?.takeIf { destination.setlistFileName != null }?.duration?.let(ChordProDuration::format)
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    // Only a window resized across the width that makes room for it, or a cover set or removed, animates:
                    // one that opens the screen with the cover out or in shows it that way from its first frame. The
                    // last address is kept while a removed cover leaves, and is the page's own, since this is
                    // composed afresh for every song.
                    val coverUrl = song?.coverArtUrl
                    var lastCoverUrl by remember { mutableStateOf(coverUrl) }
                    if (coverUrl != null) lastCoverUrl = coverUrl
                    AnimatedVisibility(
                        visible = showsCoverInBar && coverUrl != null,
                        enter = fadeIn() + expandHorizontally(),
                        exit = fadeOut() + shrinkHorizontally(),
                    ) {
                        lastCoverUrl?.let { url ->
                            Crossfade(targetState = url) {
                                CoverArtImage(
                                    modifier = Modifier.padding(end = APP_BAR_COVER_GAP).size(APP_BAR_COVER_SIZE),
                                    url = it,
                                )
                            }
                        }
                    }
                    Column {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            // Edited in place (Edit song details) rather than paged to, so it fades in place.
                            AnimatedContent(
                                modifier = Modifier.weight(1f, fill = false),
                                targetState = song?.title.orEmpty(),
                                transitionSpec = { fadeIn() togetherWith fadeOut() },
                            ) { title ->
                                Text(
                                    text = title,
                                    style = MaterialTheme.typography.titleMedium,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis,
                                )
                            }
                            // What says that the title opens the sheet, shown only while a tap does: scrolled down,
                            // a tap scrolls back to the top instead. Its room is kept while it is hidden wherever the
                            // sheet is offered at all, so that a long title is not cut off afresh every time the
                            // song passes its top.
                            if (openCurrentSongInfo != null) {
                                val chevronAlpha by animateFloatAsState(if (openSongInfoAtTop != null) 1f else 0f)
                                Icon(
                                    modifier = Modifier
                                        .padding(start = APP_BAR_TITLE_CHEVRON_GAP)
                                        .size(APP_BAR_TITLE_CHEVRON_SIZE)
                                        .graphicsLayer {
                                            alpha = chevronAlpha
                                            scaleX = chevronAlpha
                                            scaleY = chevronAlpha
                                        },
                                    painter = painterResource(Res.drawable.ic_expand),
                                    contentDescription = null,
                                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                                )
                            }
                        }
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            // Drawn for every song, blank or not, so that the bar is two lines tall whatever the
                            // song says about itself: the whole title block is what the cover beside it is as tall
                            // as, and a bar that changed height as the pager moved from a song with an artist to
                            // one without would take the lyrics with it.
                            AnimatedContent(
                                modifier = Modifier.weight(1f, fill = false),
                                targetState = song?.artist.orEmpty(),
                                transitionSpec = { fadeIn() togetherWith fadeOut() },
                            ) { artist ->
                                Text(
                                    text = artist,
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis,
                                )
                            }
                            SongHeaderNote(
                                text = headerKey,
                                description = headerKey?.let { textResource(Res.string.songs_key, it) },
                                isEmphasized = true,
                                hasPrecedingContent = song?.artist?.isNotBlank() == true,
                            )
                            SongHeaderNote(
                                text = headerTempo,
                                isEmphasized = false,
                                hasPrecedingContent = song?.artist?.isNotBlank() == true || headerKey != null,
                            )
                            SongHeaderNote(
                                text = headerDuration,
                                isEmphasized = false,
                                hasPrecedingContent = song?.artist?.isNotBlank() == true || headerKey != null || headerTempo != null,
                            )
                        }
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
                visible = isReadOnly && showsFontScaleInBar,
                enter = fadeIn() + expandHorizontally(),
                exit = fadeOut() + shrinkHorizontally(),
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    ActionsMenu(items = listOfNotNull(metronomeAction?.takeUnless { showsMetronomeInBar || !isPerformanceModeEnabled }))
                    AnimatedVisibility(
                        visible = showsMetronomeInBar && isMetronomeEnabled,
                        enter = fadeIn() + expandHorizontally(),
                        exit = fadeOut() + shrinkHorizontally(),
                    ) {
                        metronomeButton()
                    }
                    LiveFontScaleControls(
                        modifier = Modifier.padding(end = APP_BAR_STEPPER_END_PADDING),
                        viewModel = viewModel,
                    )
                }
            }
            // A song read from an archived setlist keeps its own menu, so there the song's Edit and Export take these in
            // rather than a second overflow button standing next to this one.
            AnimatedVisibility(
                visible = isPerformanceModeEnabled && !showsFontScaleInBar,
                enter = fadeIn() + expandHorizontally(),
                exit = fadeOut() + shrinkHorizontally(),
            ) {
                ActionsMenu(
                    items = listOfNotNull(metronomeAction),
                    menuFooter = {
                        MenuStepperRow(label = stringResource(Res.string.song_details_text_size)) {
                            LiveFontScaleControls(viewModel = viewModel)
                        }
                    },
                )
            }
            currentSong?.takeIf { !isPerformanceModeEnabled }?.let { song ->
                val editingActions = songInfoEditingActions(rememberSongInfoEditing(viewModel = viewModel, song = song, target = SongEditTarget.File(song.fileName)))
                val coverArtAction = if (isCoverArtEnabled) coverArtAction(viewModel = viewModel, song = song, target = SongEditTarget.File(song.fileName)) else null
                val isInSetlist = song.fileName in songFileNamesInSetlists
                AnimatedVisibility(
                    visible = !isReadOnly && isMetronomeEnabled,
                    enter = fadeIn() + expandHorizontally(),
                    exit = fadeOut() + shrinkHorizontally(),
                ) {
                    metronomeButton()
                }
                AnimatedVisibility(
                    modifier = Modifier.overlappingAction(start = ACTION_BUTTON_OVERLAP, end = 0.dp),
                    visible = showsSetlistAssignmentsInBar,
                    enter = fadeIn() + expandHorizontally(),
                    exit = fadeOut() + shrinkHorizontally(),
                ) {
                    SetlistAssignmentsButton(
                        viewModel = viewModel,
                        song = song,
                        isInSetlist = isInSetlist,
                        setlistFileName = destination.setlistFileName,
                    )
                }
                // An archived setlist's song keeps the editor alone, which is no menu of its own, so it stays in
                // the one menu next to Export.
                AnimatedVisibility(
                    modifier = Modifier.overlappingAction(start = ACTION_BUTTON_OVERLAP, end = 0.dp),
                    visible = !isReadOnly,
                    enter = fadeIn() + expandHorizontally(),
                    exit = fadeOut() + shrinkHorizontally(),
                ) {
                    SongEditingActions(
                        viewModel = viewModel,
                        song = song,
                        fileEditItems = editingActions.take(1) +
                            // The sheet edits what the two features show, so it goes once both are switched off.
                            listOfNotNull(
                                if (shouldShowChords || isMetronomeEnabled) {
                                    songPlayingAction(
                                        viewModel = viewModel,
                                        song = song,
                                        setlistFileName = destination.setlistFileName,
                                        target = SongEditTarget.File(song.fileName),
                                    )
                                } else {
                                    null
                                },
                            ) +
                            listOfNotNull(coverArtAction) +
                            editingActions.drop(1),
                    )
                }
                SongActions(
                    modifier = Modifier.overlappingAction(start = ACTION_BUTTON_OVERLAP, end = 0.dp),
                    viewModel = viewModel,
                    song = song,
                    isDeletable = destination.setlistFileName == null,
                    isEditAndExportOnly = isReadOnly,
                    isEditShown = isReadOnly,
                    setlistFileName = destination.setlistFileName,
                    leadingItems = when {
                        !isReadOnly -> listOfNotNull(
                            if (showsSetlistAssignmentsInBar || !areSetlistsEnabled) {
                                null
                            } else {
                                setlistAssignmentsAction(
                                    viewModel = viewModel,
                                    song = song,
                                    isInSetlist = isInSetlist,
                                    setlistFileName = destination.setlistFileName,
                                )
                            },
                        )
                        showsFontScaleInBar -> listOfNotNull(metronomeAction?.takeUnless { showsMetronomeInBar })
                        else -> listOfNotNull(metronomeAction)
                    },
                    // The transposition, the capo and the tempo are set in the song's own first section now; what
                    // is left for the menu is the text size, which belongs to the reader rather than to the song.
                    menuFooter = if (showsFontScaleInBar) {
                        null
                    } else {
                        {
                            MenuStepperRow(label = stringResource(Res.string.song_details_text_size)) {
                                LiveFontScaleControls(viewModel = viewModel)
                            }
                        }
                    },
                )
            }
        },
        bottomContent = {
            MetronomePanel(
                viewModel = viewModel,
                isVisible = isMetronomePanelVisible,
                contentPadding = contentPadding,
                visibleState = metronomePanelState,
            )
        },
    )
}

/**
 * The app bar's title as the touch target of what the caller does with a tap on it — scrolling the song back to its
 * top, the way tapping the bar does on a phone, and opening the sheet of what the song is once it is already there.
 * It is the whole of the bar that nothing else takes: as wide as the room the bar leaves the title and as tall
 * as the bar, so that a tap anywhere on its empty part counts. The bar pads that room by [TITLE_TOUCH_HORIZONTAL_OUTSET]
 * at either end and centers the title in its height, and the target reaches out over both while reporting only the room
 * itself, so the bar lays out the back button, the title and the actions exactly as it would without it. The outset
 * stops where the touch targets of the back button and the actions begin: the bar places the title after the back
 * button, so a target reaching any further would take its presses from it.
 *
 * It draws no indication: a press lighting up most of the bar reads as the bar itself reacting rather than as a button,
 * and a highlight around the title alone would need room the bar does not leave it, starting 4dp after the back
 * button's target. The caller dims the title through [interactionSource] instead, the way a text button on iOS answers
 * a press, which needs no room around it at all; a pointer is shown the hand over it while a click does anything.
 */
private fun Modifier.titleTouchTarget(
    isEnabled: Boolean,
    interactionSource: MutableInteractionSource,
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
    .then(if (isEnabled) Modifier.pointerHoverIcon(PointerIcon.Hand) else Modifier)
    .clickable(
        interactionSource = interactionSource,
        indication = null,
        enabled = isEnabled,
        onClickLabel = onClickLabel,
        onClick = onClick,
    )
    .padding(horizontal = TITLE_TOUCH_HORIZONTAL_OUTSET, vertical = TITLE_TOUCH_VERTICAL_OUTSET)
    .fillMaxWidth()

private val TITLE_TOUCH_HORIZONTAL_OUTSET = 4.dp // The padding the bar puts around its title.
private val APP_BAR_TITLE_CHEVRON_SIZE = 20.dp // A little under the titleMedium line it follows.
private val APP_BAR_TITLE_CHEVRON_GAP = 2.dp // The chevron's own artwork leaves the rest of the gap after the title.
private val TITLE_TOUCH_VERTICAL_OUTSET = 12.dp // From the two lines of title, 40dp, to the bar's 64dp.
