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

import com.pandulapeter.campfire.presentation.ui.CampfireViewModel

/**
 * One setlist's rows in the order they are drawn, which is the order a drag in flight has put them in rather than
 * the setlist's own. Each row takes the number of the slot it now sits in instead of the one it arrived with, so
 * that the rows a drag passes are renumbered as it passes them - and since the slots are the ones the visible rows
 * already occupied, the numbers do not change again when [CampfireViewModel.reorderSetlist] writes the same
 * dealing out to the file.
 */
internal fun SetlistWithSongs.rows(dragOrder: List<String>?): List<SetlistRow> {
    val songFileNames = dragOrder ?: return entries.map { SetlistRow(entry = it, index = it.index) }
    val entriesBySongFileName = entries.associateBy { it.songFileName }
    return songFileNames.mapIndexedNotNull { position, songFileName ->
        entriesBySongFileName[songFileName]?.let { entry ->
            SetlistRow(entry = entry, index = entries.getOrNull(position)?.index ?: entry.index)
        }
    }
}

/** Keeps unchanged setlists' row objects through each drag update. */
internal class SetlistRowsCache {
    private val cached = mutableMapOf<String, CachedRows>()

    fun retainOnly(setlists: List<SetlistWithSongs>) {
        cached.keys.retainAll(setlists.mapTo(mutableSetOf()) { it.setlist.fileName })
    }

    fun rowsFor(setlist: SetlistWithSongs, draggedSetlist: DraggedSetlist?): SetlistRows {
        val dragOrder = draggedSetlist?.takeIf { it.setlistFileName == setlist.setlist.fileName }?.songFileNames
        val previous = cached[setlist.setlist.fileName]
        if (previous != null && previous.setlist === setlist && previous.dragOrder == dragOrder) return previous.rows

        val rows = setlist.rows(dragOrder)
        val result = SetlistRows(rows = rows, songFileNames = rows.map { it.entry.songFileName })
        cached[setlist.setlist.fileName] = CachedRows(setlist = setlist, dragOrder = dragOrder, rows = result)
        return result
    }

    private class CachedRows(
        val setlist: SetlistWithSongs,
        val dragOrder: List<String>?,
        val rows: SetlistRows,
    )
}

internal class SetlistRows(
    val rows: List<SetlistRow>,
    val songFileNames: List<String>,
)

/** One row of a setlist as it is drawn: the entry, and the place it sits in right now. */
internal data class SetlistRow(
    val entry: SetlistWithSongs.Entry,
    val index: Int,
)

/** The songs of one setlist in the order a drag has put them, which nothing outside this screen knows about yet. */
internal data class DraggedSetlist(
    val setlistFileName: String,
    val songFileNames: List<String>,
)

/**
 * The index the grid holds a setlist's header at, counted the way [SetlistList] emits the items of the setlists before
 * it while performance mode is off, which it is whenever a setlist is being reordered.
 */
internal fun List<SetlistWithSongs>.headerIndexOf(setlistFileName: String?): Int? {
    var index = 0
    forEach { setlistWithSongs ->
        if (setlistWithSongs.setlist.fileName == setlistFileName) return index
        index += 2 + setlistWithSongs.entries.size + if (setlistWithSongs.setlist.description.isNotBlank()) 1 else 0
    }
    return null
}

/** Swap the row at [from] with its neighbor in the already checked direction. */
internal fun List<String>.movedOnePlace(from: Int, by: Int): List<String> {
    val result = toMutableList()
    val to = from + by
    val neighbor = result[to]
    result[to] = result[from]
    result[from] = neighbor
    return result
}

/** Whether a setlist's songs can be put in another order: an archived setlist is read only, and one song has none. */
internal val SetlistWithSongs.canReorder get() = !setlist.isArchived && entries.size > 1

/**
 * The order a drag's move from [from] to [to] leaves the rows in, taken from the order the drag has put this setlist in
 * already or else from [setlists]; null for a move that changes nothing, since songs can only be reordered within their
 * own setlist.
 */
internal fun DraggedSetlist?.afterMove(from: SetlistItemKey, to: SetlistItemKey, setlists: List<SetlistWithSongs>): DraggedSetlist? {
    val setlistFileName = from.setlistFileName ?: return null
    val fromSongFileName = from.songFileName ?: return null
    val toSongFileName = to.songFileName ?: return null
    if (setlistFileName != to.setlistFileName) return null
    val songFileNames = this?.takeIf { it.setlistFileName == setlistFileName }?.songFileNames
        ?: setlists.firstOrNull { it.setlist.fileName == setlistFileName }?.entries?.map { it.songFileName }
        ?: return null
    return DraggedSetlist(
        setlistFileName = setlistFileName,
        songFileNames = songFileNames.toMutableList().apply {
            val fromIndex = indexOf(fromSongFileName)
            val toIndex = indexOf(toSongFileName)
            if (fromIndex >= 0 && toIndex >= 0) add(toIndex, removeAt(fromIndex))
        },
    )
}

/**
 * Whether the drawn order can be given up for [setlist], the dragged one as the library has it now: once the library
 * agrees with it, and just as readily when the setlist turns out to hold different songs than the drag was working
 * from, when it has gone or when it has been archived, since an archived setlist is never reordered.
 */
internal fun DraggedSetlist.isSettledBy(setlist: SetlistWithSongs?): Boolean {
    val libraryOrder = setlist?.entries?.map { it.songFileName }
    return setlist?.setlist?.isArchived == true || libraryOrder == songFileNames || libraryOrder?.toSet() != songFileNames.toSet()
}
