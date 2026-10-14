/*
 * This file is part of Campfire.
 * Copyright (c) Pandula Péter 2017-2026.
 * https://github.com/pandulapeter/campfire
 *
 * This Source Code Form is subject to the terms of the Mozilla Public License, v. 2.0.
 * If a copy of the MPL was not distributed with this file, You can obtain one at
 * https://mozilla.org/MPL/2.0/.
 */
package com.pandulapeter.campfire.presentation.ui.screens.setlists

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.MutableTransitionState
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.expandHorizontally
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkHorizontally
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.lazy.grid.LazyGridItemScope
import androidx.compose.foundation.lazy.grid.LazyGridState
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.semantics.CustomAccessibilityAction
import androidx.compose.ui.semantics.customActions
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.pandulapeter.campfire.presentation.localization.stringResource
import com.pandulapeter.campfire.presentation.resources.Res
import com.pandulapeter.campfire.presentation.resources.setlists_move_down
import com.pandulapeter.campfire.presentation.resources.setlists_move_up
import com.pandulapeter.campfire.presentation.ui.CampfireViewModel
import com.pandulapeter.campfire.presentation.ui.components.DragHandle
import com.pandulapeter.campfire.presentation.ui.components.LabelsOnEverySong
import com.pandulapeter.campfire.presentation.ui.components.list.ListTopFade
import com.pandulapeter.campfire.presentation.ui.components.MissingSongListItem
import com.pandulapeter.campfire.presentation.ui.components.menu.OverflowMenuState
import com.pandulapeter.campfire.presentation.ui.components.SongActionHandler
import com.pandulapeter.campfire.presentation.ui.components.songCard.SongCardOptions
import com.pandulapeter.campfire.presentation.ui.components.songCard.SongListItem
import com.pandulapeter.campfire.presentation.ui.components.songCard.draggedListItemContainerColor
import com.pandulapeter.campfire.presentation.ui.components.list.fadingUnderListTop
import com.pandulapeter.campfire.presentation.ui.components.list.listItemAnimation
import com.pandulapeter.campfire.presentation.ui.components.menu.rememberOverflowMenuState
import com.pandulapeter.campfire.presentation.ui.components.songCard.rememberSongCardKey
import com.pandulapeter.campfire.presentation.ui.components.songCard.songCardPadding
import com.pandulapeter.campfire.presentation.ui.playing.PlayingOverridesSnapshot
import com.pandulapeter.campfire.presentation.ui.playing.songPlaybackOf
import sh.calvin.reorderable.ReorderableCollectionItemScope
import sh.calvin.reorderable.ReorderableItem

/**
 * One song of a setlist on the setlists screen, its card numbered by the place the row sits in, lifted and moved by
 * [reorder] while the setlist is in its reorder mode.
 *
 * @param isLastRow Whether the row is the last of its setlist, which leaves it no step down.
 * @param songFileNames The setlist's songs in the order the rows are drawn in, which a step up or down moves this one in.
 */
@Composable
internal fun LazyGridItemScope.SetlistSongRow(
    viewModel: CampfireViewModel,
    songActions: SongActionHandler,
    setlistWithSongs: SetlistWithSongs,
    row: SetlistRow,
    rowIndex: Int,
    isLastRow: Boolean,
    songFileNames: List<String>,
    columnCount: Int,
    listState: LazyGridState,
    hasLoadedLibrary: Boolean,
    topFade: ListTopFade,
    isReordering: Boolean,
    reorderingSetlistFileName: String?,
    isPerformanceModeEnabled: Boolean,
    playingOverrides: PlayingOverridesSnapshot,
    cardOptions: SongCardOptions,
    labelsOnEverySong: LabelsOnEverySong,
    reorder: SetlistReorder,
) {
    val keyboardController = LocalSoftwareKeyboardController.current
    val entry = row.entry
    val key = SetlistItemKey(setlistFileName = setlistWithSongs.setlist.fileName, songFileName = entry.songFileName)
    // Explicit move actions remain available while browsing; only drag gestures require the mode.
    val canMove = !isPerformanceModeEnabled && setlistWithSongs.canReorder
    val isReorderable = canMove && isReordering && reorderingSetlistFileName == setlistWithSongs.setlist.fileName
    // Above the branch below, which composes the row anew whenever reorder mode starts or ends: a
    // visibility composed anew starts where its target is, so the grip would appear and vanish in one
    // frame. A row that scrolls in while the mode is on starts with its grip already there, which is
    // the list being shown rather than changed.
    val handleVisibility = remember { MutableTransitionState(isReorderable) }
    handleVisibility.targetState = isReorderable
    // Above the branch too, so that a menu opened by a long press or a right click is the one the
    // row's button still holds after the row is composed anew.
    val actionsMenuState = rememberOverflowMenuState()
    val hasActions = !isPerformanceModeEnabled && (!setlistWithSongs.setlist.isArchived || entry is SetlistWithSongs.Entry.Present)
    // The songs screen's shortcut to the row's own overflow menu. In reorder mode a long press lifts
    // the row instead, which is what a long press on a row of the setlist being reordered has to do.
    val onContextClick: (() -> Unit)? = if (!hasActions || isReorderable) {
        null
    } else {
        {
            keyboardController?.hide()
            actionsMenuState.open()
        }
    }
    // The drag written as two steps, for whoever cannot drag: a screen reader, a keyboard. Each is the
    // same single write a finished drag makes, and a row with nowhere to go in a direction is offered
    // no step that way.
    val onMoveUp: (() -> Unit)? = if (canMove && rowIndex > 0) {
        { viewModel.reorderSetlist(setlistFileName = setlistWithSongs.setlist.fileName, songFileNames = songFileNames.movedOnePlace(rowIndex, by = -1)) }
    } else null
    val onMoveDown: (() -> Unit)? = if (canMove && !isLastRow) {
        { viewModel.reorderSetlist(setlistFileName = setlistWithSongs.setlist.fileName, songFileNames = songFileNames.movedOnePlace(rowIndex, by = 1)) }
    } else null
    val moveActions = moveActionsSemantics(onMoveUp = onMoveUp, onMoveDown = onMoveDown)
    val onDragStarted: (Offset) -> Unit = { reorder.onDragStarted(setlistWithSongs.setlist.fileName) }
    // The setlist's own transposition and capo for this song, which is why the same song can be
    // listed in one key here and in another one two setlists down. Remembered as on the songs
    // screen, and above the branch below so that reorder mode starting or ending does not work it
    // out again: the row is composed again as every scroll starts and ends, and it is a whole
    // transposition to work out.
    val renderedKey = (entry as? SetlistWithSongs.Entry.Present)?.let { present ->
        val playback = songPlaybackOf(song = present.song, setlistFileName = setlistWithSongs.setlist.fileName, overrides = playingOverrides)
        rememberSongCardKey(viewModel.songRenderer, present.song, playback.transposition, playback.capo.fret, cardOptions.chordSpelling)
    }
    // ReorderableItem's drag tracking, and the elevation and color it animates while a row is
    // lifted, are only paid for while reorder mode is on. Every row of every setlist is wrapped
    // until the grid has been narrowed to the one being reordered, which is what keeps the other
    // setlists' rows out of the drag's drop targets during the scroll that brings it to the top.
    // Outside the mode every row would otherwise carry an Animatable that never leaves its rest
    // value for as long as the row is on screen, which is the very per-row coroutine
    // draggedListItemContainerColor's own documentation says a list of this size cannot afford.
    // scope is the ReorderableItem content's own receiver, which draggableHandle and
    // longPressDraggableHandle are members of; it is null outside reorder mode, where isReorderable
    // is always false and neither is ever called.
    val rowContent: @Composable (scope: ReorderableCollectionItemScope?, isBeingDragged: Boolean, itemModifier: Modifier) -> Unit = { scope, isBeingDragged, itemModifier ->
        SetlistRowContent(
            modifier = itemModifier.then(moveActions).fadingUnderListTop(topFade),
            scope = scope,
            isBeingDragged = isBeingDragged,
            isReordering = isReordering,
            isReorderable = isReorderable,
            isPerformanceModeEnabled = isPerformanceModeEnabled,
            onDragStarted = onDragStarted,
            onDragStopped = reorder.onDragStopped,
            viewModel = viewModel,
            songActions = songActions,
            setlistWithSongs = setlistWithSongs,
            row = row,
            cardPadding = songCardPadding(rowIndex, columnCount),
            renderedKey = renderedKey,
            playingOverrides = playingOverrides,
            cardOptions = cardOptions,
            labelsOnEverySong = labelsOnEverySong,
            hasActions = hasActions,
            handleVisibility = handleVisibility,
            actionsMenuState = actionsMenuState,
            onMoveUp = onMoveUp,
            onMoveDown = onMoveDown,
            onContextClick = onContextClick,
        )
    }
    if (isReordering) {
        ReorderableItem(
            state = reorder.reorderableState,
            key = key.string.orEmpty(),
            enabled = reorder.draggingSetlistFileName.let { it == null || it == setlistWithSongs.setlist.fileName },
            animateItemModifier = listItemAnimation(
                listState = listState,
                isEnabled = hasLoadedLibrary,
                isRearranging = reorder.draggedSetlist != null,
            ),
        ) { isBeingDragged -> rowContent(this, isBeingDragged, Modifier) }
    } else {
        rowContent(null, false, listItemAnimation(listState = listState, isEnabled = hasLoadedLibrary))
    }
}

/**
 * What a setlist's row draws, lifted by [scope] (the `ReorderableItem` content's own receiver, null outside reorder mode)
 * while [isBeingDragged].
 */
@Composable
private fun SetlistRowContent(
    modifier: Modifier,
    scope: ReorderableCollectionItemScope?,
    isBeingDragged: Boolean,
    isReordering: Boolean,
    isReorderable: Boolean,
    isPerformanceModeEnabled: Boolean,
    onDragStarted: (Offset) -> Unit,
    onDragStopped: () -> Unit,
    viewModel: CampfireViewModel,
    songActions: SongActionHandler,
    setlistWithSongs: SetlistWithSongs,
    row: SetlistRow,
    cardPadding: PaddingValues,
    renderedKey: String?,
    playingOverrides: PlayingOverridesSnapshot,
    cardOptions: SongCardOptions,
    labelsOnEverySong: LabelsOnEverySong,
    hasActions: Boolean,
    handleVisibility: MutableTransitionState<Boolean>,
    actionsMenuState: OverflowMenuState,
    onMoveUp: (() -> Unit)?,
    onMoveDown: (() -> Unit)?,
    onContextClick: (() -> Unit)?,
) {
    val elevation = if (isReordering) {
        val animated by animateDpAsState(if (isBeingDragged) 8.dp else 0.dp)
        animated
    } else {
        0.dp
    }
    val containerColor = if (isReordering) {
        draggedListItemContainerColor(isBeingDragged)
    } else {
        MaterialTheme.colorScheme.surfaceContainerLow
    }
    val cardModifier = modifier.let { base ->
        if (scope == null) base else with(scope) { base.longPressDraggableHandle(enabled = isReorderable, onDragStarted = onDragStarted, onDragStopped = onDragStopped) }
    }
    // Keep the overflow button available on touch platforms too, including while a
    // long press belongs to reordering rather than the song's actions.
    val actions: (@Composable () -> Unit)? = if (isPerformanceModeEnabled) {
        null
    } else {
        {
            SetlistRowActions(
                viewModel = viewModel,
                songActions = songActions,
                setlistWithSongs = setlistWithSongs,
                entry = row.entry,
                hasActions = hasActions,
                handleVisibility = handleVisibility,
                handleModifier = if (isReorderable && scope != null) {
                    with(scope) { Modifier.draggableHandle(onDragStarted = onDragStarted, onDragStopped = onDragStopped) }
                } else {
                    Modifier
                },
                actionsMenuState = actionsMenuState,
                onMoveUp = onMoveUp,
                onMoveDown = onMoveDown,
            )
        }
    }
    SetlistEntryCard(
        modifier = cardModifier,
        viewModel = viewModel,
        setlistWithSongs = setlistWithSongs,
        row = row,
        cardPadding = cardPadding,
        renderedKey = renderedKey,
        playingOverrides = playingOverrides,
        cardOptions = cardOptions,
        labelsOnEverySong = labelsOnEverySong,
        containerColor = containerColor,
        elevation = elevation,
        onContextClick = onContextClick,
        actions = actions,
    )
}

/** The overflow menu of a setlist's row, with the grip that drags the row in front of it in reorder mode. */
@Composable
private fun SetlistRowActions(
    viewModel: CampfireViewModel,
    songActions: SongActionHandler,
    setlistWithSongs: SetlistWithSongs,
    entry: SetlistWithSongs.Entry,
    hasActions: Boolean,
    handleVisibility: MutableTransitionState<Boolean>,
    handleModifier: Modifier,
    actionsMenuState: OverflowMenuState,
    onMoveUp: (() -> Unit)?,
    onMoveDown: (() -> Unit)?,
) = AnimatedVisibility(
    visible = hasActions,
    enter = fadeIn() + expandHorizontally(),
    exit = fadeOut() + shrinkHorizontally(),
) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
    ) {
        // The grip goes in front of the overflow button rather than after it, so that
        // the button lands exactly where the songs screen has its own.
        AnimatedVisibility(
            visibleState = handleVisibility,
            enter = fadeIn() + expandHorizontally(),
            exit = fadeOut() + shrinkHorizontally(),
        ) {
            DragHandle(modifier = handleModifier)
        }
        SetlistEntryActions(
            state = actionsMenuState,
            viewModel = viewModel,
            songActions = songActions,
            entry = entry,
            setlistFileName = setlistWithSongs.setlist.fileName,
            isArchived = setlistWithSongs.setlist.isArchived,
            onMoveUp = onMoveUp,
            onMoveDown = onMoveDown,
        )
    }
}

/** A setlist row's card: the song, or the place of one the library no longer has. */
@Composable
private fun SetlistEntryCard(
    modifier: Modifier,
    viewModel: CampfireViewModel,
    setlistWithSongs: SetlistWithSongs,
    row: SetlistRow,
    cardPadding: PaddingValues,
    renderedKey: String?,
    playingOverrides: PlayingOverridesSnapshot,
    cardOptions: SongCardOptions,
    labelsOnEverySong: LabelsOnEverySong,
    containerColor: Color,
    elevation: Dp,
    onContextClick: (() -> Unit)?,
    actions: (@Composable () -> Unit)?,
) = when (val entry = row.entry) {
    is SetlistWithSongs.Entry.Present -> {
        val setlistFileName = setlistWithSongs.setlist.fileName
        SongListItem(
            modifier = modifier,
            song = entry.song,
            index = row.index,
            cardPadding = cardPadding,
            key = renderedKey.takeIf { cardOptions.isKeyShown },
            // And the setlist's own tempo, for the same reason.
            tempo = songPlaybackOf(
                song = entry.song,
                setlistFileName = setlistFileName,
                overrides = playingOverrides,
            ).tempo.bpm.takeIf { cardOptions.isTempoShown },
            shouldShowChords = cardOptions.shouldShowChords,
            duration = entry.song.duration?.takeIf { cardOptions.isDurationShown },
            coverArtUrl = entry.song.coverArtUrl?.takeIf { cardOptions.isCoverArtEnabled },
            labelsOnEverySong = labelsOnEverySong,
            shouldShowLabels = false,
            containerColor = containerColor,
            shadowElevation = elevation,
            onClick = { viewModel.openSongInSetlist(setlistWithSongs, entry.song) },
            onContextClick = onContextClick,
            actions = actions,
        )
    }

    // Nothing to open, but it still takes its place in the order and can be removed.
    is SetlistWithSongs.Entry.Missing -> MissingSongListItem(
        modifier = modifier,
        index = row.index,
        songFileName = entry.songFileName,
        cardPadding = cardPadding,
        containerColor = containerColor,
        shadowElevation = elevation,
        onContextClick = onContextClick,
        actions = actions,
    )
}

/**
 * The drag written as two steps, for whoever cannot drag. Merged so that a missing song's row, which has no clickable of
 * its own, is still the one node a screen reader lands on; a present row is merged by its own click handling already.
 */
@Composable
private fun moveActionsSemantics(onMoveUp: (() -> Unit)?, onMoveDown: (() -> Unit)?): Modifier {
    val moveUpLabel = stringResource(Res.string.setlists_move_up)
    val moveDownLabel = stringResource(Res.string.setlists_move_down)
    return Modifier.semantics(mergeDescendants = true) {
        customActions = listOfNotNull(
            onMoveUp?.let { CustomAccessibilityAction(moveUpLabel) { it(); true } },
            onMoveDown?.let { CustomAccessibilityAction(moveDownLabel) { it(); true } },
        )
    }
}
