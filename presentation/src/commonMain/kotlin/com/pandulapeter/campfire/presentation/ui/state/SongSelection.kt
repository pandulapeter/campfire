/*
 * This file is part of Campfire.
 * Copyright (c) Pandula Péter 2017-2026.
 * https://github.com/pandulapeter/campfire
 *
 * This Source Code Form is subject to the terms of the Mozilla Public License, v. 2.0.
 * If a copy of the MPL was not distributed with this file, You can obtain one at
 * https://mozilla.org/MPL/2.0/.
 */
package com.pandulapeter.campfire.presentation.ui.state

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import com.pandulapeter.campfire.data.model.DataState
import com.pandulapeter.campfire.domain.api.models.ScreenData
import com.pandulapeter.campfire.presentation.ui.navigation.CampfireDestination
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch

/**
 * The selection mode of the songs screen: the songs ticked there, by file name, for as long as the mode lasts, so that
 * a search, a sort or a filter changed in between keeps them. Snapshot state, like the setlist reorder mode, so that the
 * web's history observes it, and never saved: it ends as the songs screen stops being on top, and a process restored
 * comes back to the songs without it.
 */
internal class SongSelection(
    private val scope: CoroutineScope,
    private val backStack: List<CampfireDestination>,
) {

    /** Null while the mode is off; a set, an empty one included, while it is on. */
    var selectedSongFileNames by mutableStateOf<Set<String>?>(null)
        private set

    /**
     * Counts the times the mode was started, which the list keys the order of its Selection section by: an order
     * remembered across two sessions would open the second with the songs the first one unticked still leading.
     */
    var session by mutableIntStateOf(0)
        private set

    /**
     * Counts Select all, which orders the Selection section again as a search would: what it ticks leads the list in
     * the list's own order rather than every listed song joining the section as a copy of itself.
     */
    var selectAllCount by mutableIntStateOf(0)
        private set

    val isSongSelecting: Boolean
        get() = backStack.lastOrNull() == CampfireDestination.Songs && selectedSongFileNames != null

    fun start(fileName: String) {
        session++
        selectedSongFileNames = setOf(fileName)
    }

    fun toggle(fileName: String) {
        val selected = selectedSongFileNames ?: return
        selectedSongFileNames = if (fileName in selected) selected - fileName else selected + fileName
    }

    /** Adds [fileNames] to what is ticked, which is how Select all ticks what is listed without unticking the rest. */
    fun select(fileNames: Collection<String>) {
        selectedSongFileNames = (selectedSongFileNames ?: return) + fileNames
        selectAllCount++
    }

    fun deselectAll() {
        if (selectedSongFileNames != null) selectedSongFileNames = emptySet()
    }

    fun end() {
        selectedSongFileNames = null
    }

    /**
     * Unticks the songs that leave the library, so that the count follows a sync run deleting a ticked song. Only a
     * library that was read whole prunes: a rescan publishes in batches, and one that had not reached a ticked song yet
     * would untick it.
     */
    fun startPruning(screenData: StateFlow<DataState<ScreenData>>) = scope.launch {
        screenData.collect { state ->
            val data = state.data
            if (state is DataState.Loading || data == null || !data.isWholeLibrary) return@collect
            val selected = selectedSongFileNames ?: return@collect
            val fileNames = data.unfilteredSongs.mapTo(hashSetOf()) { it.fileName }
            if (selected.any { it !in fileNames }) selectedSongFileNames = selected.filterTo(mutableSetOf()) { it in fileNames }
        }
    }
}
