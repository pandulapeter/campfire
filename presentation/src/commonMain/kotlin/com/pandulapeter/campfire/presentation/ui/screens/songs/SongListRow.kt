/*
 * This file is part of Campfire.
 * Copyright (c) Pandula Péter 2017-2026.
 * https://github.com/pandulapeter/campfire
 *
 * This Source Code Form is subject to the terms of the Mozilla Public License, v. 2.0.
 * If a copy of the MPL was not distributed with this file, You can obtain one at
 * https://mozilla.org/MPL/2.0/.
 */
package com.pandulapeter.campfire.presentation.ui.screens.songs

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.size
import androidx.compose.material3.Checkbox
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.painter.Painter
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.toggleableState
import androidx.compose.ui.state.ToggleableState
import androidx.compose.ui.unit.dp
import com.pandulapeter.campfire.data.model.domain.Song
import com.pandulapeter.campfire.domain.api.library.SongFilter
import com.pandulapeter.campfire.presentation.ui.CampfireViewModel
import com.pandulapeter.campfire.presentation.ui.components.menu.ACTION_BUTTON_OVERLAP
import com.pandulapeter.campfire.presentation.ui.components.menu.ActionsMenuItem
import com.pandulapeter.campfire.presentation.ui.components.labels.LabelsOnEverySong
import com.pandulapeter.campfire.presentation.ui.components.list.ListTopFade
import com.pandulapeter.campfire.presentation.ui.components.menu.OverflowMenuState
import com.pandulapeter.campfire.presentation.ui.components.actions.SetlistAssignmentsButton
import com.pandulapeter.campfire.presentation.ui.components.actions.SongActionHandler
import com.pandulapeter.campfire.presentation.ui.components.actions.SongActions
import com.pandulapeter.campfire.presentation.ui.components.songCard.SongCardOptions
import com.pandulapeter.campfire.presentation.ui.components.songCard.SongListItem
import com.pandulapeter.campfire.presentation.ui.components.list.fadingUnderListTop
import com.pandulapeter.campfire.presentation.ui.components.menu.rememberOverflowMenuState
import com.pandulapeter.campfire.presentation.ui.components.songCard.rememberSongCardKey
import com.pandulapeter.campfire.presentation.ui.dialogs.SongEditTarget
import com.pandulapeter.campfire.presentation.ui.playing.PlayingOverridesSnapshot
import com.pandulapeter.campfire.presentation.ui.playing.songPlaybackOf

/**
 * One song of the song list: its card, which opens it, or ticks it in the selection mode, and the menu hanging from it.
 *
 * @param itemKey The row's key in the grid, which a tag or a language tapped on it keeps in place by.
 */
@Composable
internal fun SongListRow(
    viewModel: CampfireViewModel,
    songActions: SongActionHandler,
    song: Song,
    itemKey: String,
    cardPadding: PaddingValues,
    topFade: ListTopFade,
    playingOverrides: PlayingOverridesSnapshot,
    cardOptions: SongCardOptions,
    labelsOnEverySong: LabelsOnEverySong,
    songFilter: SongFilter,
    isInSetlist: Boolean,
    isTicked: Boolean,
    isSelecting: Boolean,
    isPerformanceModeEnabled: Boolean,
    areSetlistsEnabled: Boolean,
    selectMultipleTitle: String,
    selectSongsIcon: Painter,
    onTagClicked: (itemKey: String, String) -> Unit,
    onLanguageClicked: (itemKey: String, String) -> Unit,
) {
    val keyboardController = LocalSoftwareKeyboardController.current
    val actionsMenuState = rememberOverflowMenuState()
    // A song opened from the library is transposed, capoed and timed in the preferences, so those are
    // the only values this list knows about: the setlists each hold their own. The key is remembered,
    // since the row is composed again as every scroll starts and ends and it is a whole transposition
    // to work out again.
    val (transposition, capo, tempo) = songPlaybackOf(song = song, setlistFileName = null, overrides = playingOverrides)
    val key = rememberSongCardKey(
        songRenderer = viewModel.songRenderer,
        song = song,
        transposition = transposition,
        capo = capo.fret,
        chordSpelling = cardOptions.chordSpelling,
    )
    // Remembered, since the semantics lambda is a new one on every pass, which would make the modifier
    // unequal and have every visible card composed again on every tick of the selection mode.
    val selectionSemantics = remember(isSelecting, isTicked) {
        Modifier.selectionSemantics(isSelecting = isSelecting, isTicked = isTicked)
    }
    SongListItem(
        modifier = Modifier.fadingUnderListTop(topFade).then(selectionSemantics),
        song = song,
        cardPadding = cardPadding,
        key = key.takeIf { cardOptions.isKeyShown },
        tempo = tempo.bpm.takeIf { cardOptions.isTempoShown },
        shouldShowChords = cardOptions.shouldShowChords,
        duration = song.duration?.takeIf { cardOptions.isDurationShown },
        coverArtUrl = song.coverArtUrl?.takeIf { cardOptions.isCoverArtEnabled },
        labelsOnEverySong = labelsOnEverySong,
        songFilter = songFilter,
        onTagClicked = { onTagClicked(itemKey, it) },
        onLanguageClicked = { onLanguageClicked(itemKey, it) },
        onClick = if (isSelecting) {
            { viewModel.toggleSongSelection(song.fileName) }
        } else {
            {
                keyboardController?.hide()
                viewModel.openSong(song)
            }
        },
        // A shortcut to the row's own overflow menu, for a right click and, where holding a row is a
        // natural way to ask for it, a long press: the same menu, hanging from the same button, since
        // the same entries shown two different ways would read as two different things. In
        // performance mode there is nothing to open either way, so a row does nothing but open a song.
        onContextClick = if (isPerformanceModeEnabled || isSelecting) {
            null
        } else {
            {
                keyboardController?.hide()
                actionsMenuState.open()
            }
        },
        actions = if (isPerformanceModeEnabled) {
            null
        } else {
            {
                SongRowActions(
                    viewModel = viewModel,
                    songActions = songActions,
                    song = song,
                    actionsMenuState = actionsMenuState,
                    isSelecting = isSelecting,
                    isTicked = isTicked,
                    isInSetlist = isInSetlist,
                    areSetlistsEnabled = areSetlistsEnabled,
                    selectMultipleTitle = selectMultipleTitle,
                    selectSongsIcon = selectSongsIcon,
                )
            }
        },
    )
}

/** The star and the menu of a song's card, or, in the selection mode, the box that says whether it is ticked. */
@Composable
private fun SongRowActions(
    viewModel: CampfireViewModel,
    songActions: SongActionHandler,
    song: Song,
    actionsMenuState: OverflowMenuState,
    isSelecting: Boolean,
    isTicked: Boolean,
    isInSetlist: Boolean,
    areSetlistsEnabled: Boolean,
    selectMultipleTitle: String,
    selectSongsIcon: Painter,
) = AnimatedContent(
    targetState = isSelecting,
    transitionSpec = { fadeIn() togetherWith fadeOut() },
    contentAlignment = Alignment.CenterEnd,
) { isShowingBox ->
    if (isShowingBox) {
        // The card is what ticks it, so the box takes no click of its own; it stands
        // in a touch target's room, where the menu button stood.
        Box(
            modifier = Modifier.size(SELECTION_BOX_SIZE),
            contentAlignment = Alignment.Center,
        ) {
            Checkbox(checked = isTicked, onCheckedChange = null)
        }
    } else {
        Row(
            horizontalArrangement = Arrangement.spacedBy(-ACTION_BUTTON_OVERLAP),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            if (areSetlistsEnabled) {
                SetlistAssignmentsButton(
                    actions = songActions,
                    song = song,
                    isInSetlist = isInSetlist,
                )
            }
            SongActions(
                state = actionsMenuState,
                actions = songActions,
                song = song,
                isDeletable = true,
                isDuplicable = true,
                // After the song's own metadata editors, the row's way into the
                // mode, which starts with this song ticked.
                fileEditItems = songLabelActions(
                    viewModel = viewModel,
                    song = song,
                    target = SongEditTarget.File(song.fileName),
                ) + ActionsMenuItem(
                    title = selectMultipleTitle,
                    icon = selectSongsIcon,
                    isAlwaysInMenu = true,
                    onClick = { viewModel.startSongSelection(song.fileName) },
                ),
            )
        }
    }
}

/** A card of the selection mode is one checkbox to a screen reader, the card being what ticks it. */
private fun Modifier.selectionSemantics(isSelecting: Boolean, isTicked: Boolean) = if (isSelecting) {
    semantics(mergeDescendants = true) {
        role = Role.Checkbox
        toggleableState = ToggleableState(isTicked)
    }
} else {
    this
}

/** The room a card's box stands in, which is a touch target's, where the menu button stood. */
private val SELECTION_BOX_SIZE = 48.dp
