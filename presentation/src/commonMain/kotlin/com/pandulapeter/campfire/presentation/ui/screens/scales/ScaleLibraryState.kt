/*
 * This file is part of Campfire.
 * Copyright (c) Pandula Péter 2017-2026.
 * https://github.com/pandulapeter/campfire
 *
 * This Source Code Form is subject to the terms of the Mozilla Public License, v. 2.0.
 * If a copy of the MPL was not distributed with this file, You can obtain one at
 * https://mozilla.org/MPL/2.0/.
 */
package com.pandulapeter.campfire.presentation.ui.screens.scales

import androidx.compose.runtime.MutableIntState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.asSharedFlow
import com.pandulapeter.campfire.presentation.ui.components.ScrollPosition
import com.pandulapeter.campfire.presentation.ui.components.ToolSearch
import com.pandulapeter.campfire.presentation.ui.scales.ScaleLabels

/**
 * Where the Scales tab was left: the key, the family, the search and the scroll position, and how the scale sheet labels its notes,
 * held by the view model for the session for the reason [ScrollPosition] gives. Nothing here is saved: the instrument and
 * its tuning, the one thing about scales a player sets once, are the preferences'.
 */
internal class ScaleLibraryState(
    keyIndex: MutableIntState,
) : ToolSearch {

    /**
     * The key picked, by its place in `libraryKeys`, from C: the same key whichever way its accidental is spelled. One
     * state shared by the Chords and the Scales tabs, so that moving between them keeps the key the player is in.
     */
    var keyIndex by keyIndex

    /** The family picked: 0 for the common scales, else one more than the family's place in `ScaleFamily`. */
    var familyIndex by mutableIntStateOf(0)

    /** What the search finds scales by, their names and the other names they go by, across every family. */
    override var query by mutableStateOf("")

    private val _searchFocusRequests = MutableSharedFlow<Unit>(extraBufferCapacity = 1)

    override val searchFocusRequests: Flow<Unit> = _searchFocusRequests.asSharedFlow()

    override fun focusSearch() {
        _searchFocusRequests.tryEmit(Unit)
    }

    /** What the scale sheet writes in its dots, wherever it is opened from. */
    var labels by mutableStateOf(ScaleLabels.DEGREES)

    val scrollPosition = ScrollPosition()
}
