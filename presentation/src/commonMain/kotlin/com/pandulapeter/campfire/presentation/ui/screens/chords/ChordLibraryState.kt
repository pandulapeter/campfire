/*
 * This file is part of Campfire.
 * Copyright (c) Pandula Péter 2017-2026.
 * https://github.com/pandulapeter/campfire
 *
 * This Source Code Form is subject to the terms of the Mozilla Public License, v. 2.0.
 * If a copy of the MPL was not distributed with this file, You can obtain one at
 * https://mozilla.org/MPL/2.0/.
 */
package com.pandulapeter.campfire.presentation.ui.screens.chords

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import com.pandulapeter.campfire.presentation.ui.components.ScrollPosition
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.asSharedFlow

/**
 * Where the Chords tab was left: the key, the search, the results and the scroll position, held by the view model for the
 * session for the reason [ScrollPosition] gives, since the tab is taken off the back stack whenever another one is picked.
 * The instrument and its tuning are the preferences' (Settings → Songs), so they are not here.
 */
internal class ChordLibraryState {

    /** The key picked, by its place in `chordLibraryKeys`, from C: the same key whichever way its accidental is spelled. */
    var keyIndex by mutableIntStateOf(0)

    var query by mutableStateOf("")

    private val _searchFocusRequests = MutableSharedFlow<Unit>(extraBufferCapacity = 1)

    /**
     * Asks the search field for the caret, with what it holds selected, see [focusSearch]. Events rather than state, for
     * the reason `SearchState.focusRequests` gives.
     */
    val searchFocusRequests: Flow<Unit> = _searchFocusRequests.asSharedFlow()

    /** What Ctrl / Cmd + F does on the tab, see `ScreenSearches.openCurrentSearch`. */
    fun focusSearch() {
        _searchFocusRequests.tryEmit(Unit)
    }

    val scrollPosition = ScrollPosition()

    /**
     * The results last drawn, which the next visit opens on where they are still the answer, a plain property since only
     * that visit's first composition reads it.
     */
    var shown: ChordLibraryShown? = null
}
