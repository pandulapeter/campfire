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

import androidx.compose.runtime.MutableIntState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import com.pandulapeter.campfire.presentation.ui.components.ScrollPosition
import com.pandulapeter.campfire.presentation.ui.components.ToolSearch
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.asSharedFlow

/**
 * Where the Chords tab was left: the key, the search, the results and the scroll position, held by the view model for the
 * session for the reason [ScrollPosition] gives, since the tab is taken off the back stack whenever another one is picked.
 * The instrument and its tuning are the preferences' (Settings → Songs), so they are not here.
 */
internal class ChordLibraryState(
    keyIndex: MutableIntState,
) : ToolSearch {

    /**
     * The key picked, by its place in `libraryKeys`, from C: the same key whichever way its accidental is spelled. One
     * state shared by the Chords and the Scales tabs, so that moving between them keeps the key the player is in.
     */
    var keyIndex by keyIndex

    override var query by mutableStateOf("")

    private val _searchFocusRequests = MutableSharedFlow<Unit>(extraBufferCapacity = 1)

    override val searchFocusRequests: Flow<Unit> = _searchFocusRequests.asSharedFlow()

    override fun focusSearch() {
        _searchFocusRequests.tryEmit(Unit)
    }

    val scrollPosition = ScrollPosition()

    /**
     * The results last drawn, which the next visit opens on where they are still the answer, a plain property since only
     * that visit's first composition reads it.
     */
    var shown: ChordLibraryShown? = null
}
