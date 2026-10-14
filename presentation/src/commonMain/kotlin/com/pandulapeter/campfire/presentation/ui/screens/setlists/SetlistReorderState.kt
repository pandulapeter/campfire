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

import androidx.compose.foundation.lazy.grid.LazyGridState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.State
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalHapticFeedback
import sh.calvin.reorderable.ReorderableLazyGridState
import sh.calvin.reorderable.rememberReorderableLazyGridState

/**
 * A drag of a setlist's rows: the order they are drawn in while it is in flight ([draggedSetlist]) and the setlist it
 * was started in ([draggingSetlistFileName]), both read through their states so that only what draws them follows a
 * move, and what lifts and sets the row down.
 */
internal class SetlistReorder(
    val reorderableState: ReorderableLazyGridState,
    private val draggedSetlistState: State<DraggedSetlist?>,
    private val draggingSetlistFileNameState: State<String?>,
    val onDragStarted: (setlistFileName: String) -> Unit,
    val onDragStopped: () -> Unit,
) {
    val draggedSetlist get() = draggedSetlistState.value

    val draggingSetlistFileName get() = draggingSetlistFileNameState.value
}

/** The [SetlistReorder] of the setlists screen's grid, writing a finished drag through [onReorder]. */
@Composable
internal fun rememberSetlistReorder(
    listState: LazyGridState,
    setlistsWithSongs: List<SetlistWithSongs>,
    onReorder: (setlistFileName: String, songFileNames: List<String>, onNotWritten: () -> Unit) -> Unit,
): SetlistReorder {
    // The order the rows are drawn in while a drag is in flight, before any of it has been written down. The
    // reorderable state has to see every move answered in the frame it reports it, and the library is several frames
    // away: a move that had to go to disk and come back through the repository left the row under the finger
    // snapping between where it was and where it had been put.
    val draggedSetlist = remember { mutableStateOf<DraggedSetlist?>(null) }
    // The setlist a drag was started in, known from the press that starts it rather than from its first move: the rows
    // of every other setlist stop being places to drop it for as long as it lasts. A move onto one of them is refused
    // below, but only after the reorderable state has locked itself for up to a second waiting for the list to answer
    // it, which froze the rows under the finger.
    val draggingSetlistFileName = remember { mutableStateOf<String?>(null) }
    // A row lifted, every place it moves to and the row set down are felt: the row is under the finger moving it, which
    // covers the slot number that would otherwise say where it has got to.
    val hapticFeedback = LocalHapticFeedback.current
    val reorderableState = rememberReorderableLazyGridState(listState) { from, to ->
        draggedSetlist.value.afterMove(from = SetlistItemKey(from.key as? String), to = SetlistItemKey(to.key as? String), setlists = setlistsWithSongs)?.let {
            draggedSetlist.value = it
            hapticFeedback.performHapticFeedback(HapticFeedbackType.SegmentFrequentTick)
        }
    }
    // Written once the finger lifts rather than on every move, so that one drag is one write.
    val onDragStopped = {
        draggingSetlistFileName.value = null
        hapticFeedback.performHapticFeedback(HapticFeedbackType.GestureEnd)
        draggedSetlist.value?.let { dragged ->
            // Compared by identity: a later drag of the same setlist that ended on the same order is another drag,
            // whose own write is still on its way.
            onReorder(dragged.setlistFileName, dragged.songFileNames) { if (draggedSetlist.value === dragged) draggedSetlist.value = null }
        }
        Unit
    }
    // The drawn order is given up only once the library agrees with it, not the moment the finger lifts: the write
    // has to reach the disk and come back, and the rows would sit in their old order until it did. It is given up
    // just as readily when the setlist turns out to hold different songs than the drag was working from, which is
    // what a removal or a sync landing mid drag looks like, and when it has been archived, since an archived setlist
    // is never reordered and the drag will not be written. A write that fails is told by reorderSetlist instead.
    LaunchedEffect(setlistsWithSongs, draggedSetlist.value) {
        draggedSetlist.value?.let { dragged ->
            if (dragged.isSettledBy(setlistsWithSongs.firstOrNull { it.setlist.fileName == dragged.setlistFileName })) draggedSetlist.value = null
        }
    }
    return SetlistReorder(
        reorderableState = reorderableState,
        draggedSetlistState = draggedSetlist,
        draggingSetlistFileNameState = draggingSetlistFileName,
        onDragStarted = { setlistFileName ->
            draggingSetlistFileName.value = setlistFileName
            hapticFeedback.performHapticFeedback(HapticFeedbackType.GestureThresholdActivate)
        },
        onDragStopped = onDragStopped,
    )
}
