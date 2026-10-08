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
private fun SetlistWithSongs.rows(dragOrder: List<String>?): List<SetlistRow> {
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
