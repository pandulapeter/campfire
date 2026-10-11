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

import androidx.compose.runtime.Composable
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import com.pandulapeter.campfire.presentation.ui.components.ChecklistOrder
import com.pandulapeter.campfire.presentation.ui.components.rememberChecklistOrder

/**
 * The song list with the songs [order] leads in one group in front of its sections, under [SongGroupHeader.Selection],
 * the way the Choose songs sheet leads with its ticked rows ([ChecklistOrder]): the songs ticked when the list was last
 * refreshed, in the list's own order and taken out of their sections, then every tick since in the order it was made,
 * as a copy - the card that was tapped stays where it is, so the list never moves under the finger. A section the
 * refresh emptied goes, header and all. A song unticked since the last refresh stays in the group until the next one.
 *
 * With nothing to lead, the list is the one it was given, the same instance.
 */
internal fun List<SongGroup>.withSelectionLeading(order: ChecklistOrder): List<SongGroup> {
    val selected = order.selectedGroup(flatMap { it.songs }) { it.fileName }
    if (selected.isEmpty()) return this
    val selection = SongGroup(
        header = SongGroupHeader.Selection,
        songs = selected,
        copiedFileNames = selected.mapNotNullTo(hashSetOf()) { song -> song.fileName.takeIf { it !in order.heldKeys } },
    )
    return listOf(selection) + mapNotNull { group ->
        group.songs.filterNot { it.fileName in order.heldKeys }.takeIf { it.isNotEmpty() }?.let { group.copy(songs = it) }
    }
}

/** The grid keys of the Selection section's items, which a tick, the start and the end of the mode change. */
internal fun List<SongGroup>.selectionItemKeys(): List<String> =
    firstOrNull { it.header == SongGroupHeader.Selection }?.let { group -> group.songs.map(group::itemKey) }.orEmpty()

/**
 * [songGroups] with [selection] leading it ([withSelectionLeading]), refreshed whenever the search, the sorting or a
 * filter changes, which is what [SongGroups.filterKey] carries - the key the list was built for rather than one read
 * off the preferences, so that the reseeded order and the list it reorders arrive in one composition - and by Select
 * all ([selectAllCount]). The caller keys it by the selection session.
 *
 * The song the mode is started from is a copy too: seeded with what is ticked, the first order would take that card
 * out of its section from under the finger that started the mode.
 */
@Composable
internal fun rememberSongGroupsWithSelection(songGroups: SongGroups, selection: Set<String>, selectAllCount: Int): SongGroups {
    var hasSeeded by rememberSaveable { mutableStateOf(false) }
    SideEffect { hasSeeded = true }
    val order = rememberChecklistOrder(
        checkedKeys = selection,
        refreshKey = songGroups.filterKey to selectAllCount,
        heldKeys = { if (hasSeeded) selection else emptySet() },
    )
    return remember(songGroups, order) {
        val groups = songGroups.groups.withSelectionLeading(order)
        if (groups === songGroups.groups) songGroups else songGroups.copy(groups = groups)
    }
}
