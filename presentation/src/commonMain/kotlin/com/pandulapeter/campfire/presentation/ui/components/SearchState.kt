/*
 * This file is part of Campfire.
 * Copyright (c) Pandula Péter 2017-2026.
 * https://github.com/pandulapeter/campfire
 *
 * This Source Code Form is subject to the terms of the Mozilla Public License, v. 2.0.
 * If a copy of the MPL was not distributed with this file, You can obtain one at
 * https://mozilla.org/MPL/2.0/.
 */
package com.pandulapeter.campfire.presentation.ui.components

import androidx.compose.foundation.text.input.TextFieldState
import androidx.compose.foundation.text.input.clearText
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine

/**
 * The search of one list screen: whether its field is open, and what is in it.
 *
 * It is held by the view model for the same reason [ScrollPosition] is — selecting a tab rebuilds the back stack
 * around the destination that was picked, and everything the screen that was left remembered goes with it — and
 * because the desktop's Escape handler, which sits outside the composition entirely, has to be able to close a
 * search that is open.
 *
 * Each screen gets its own: the two lists are searched for different things, and a query typed while looking at the
 * songs would silently narrow a setlist list it means something else for.
 */
internal class SearchState {

    private val _isOpen = MutableStateFlow(false)
    val isOpen: StateFlow<Boolean> = _isOpen.asStateFlow()

    /**
     * The field's own state, held here rather than remembered in the composition so that the text *and the caret*
     * survive a trip to another screen: a search left open is come back to with the caret where the typing stopped
     * rather than in front of the word that was typed.
     *
     * It also outlives [close] by design, since the field animates away rather than disappearing and one that
     * emptied itself on the first frame of that animation would be read as the text having been deleted.
     */
    val textFieldState = TextFieldState()

    /**
     * How far a back gesture that would close the search has been dragged, from 0 to 1, and 0 whenever there is no
     * such gesture. The field and the search action both draw from it, so that the gesture previews what letting go
     * of it will do instead of the search simply vanishing at the end of a drag that looked like it did nothing.
     *
     * Compose state rather than a flow because it changes on every frame of a drag and is only ever read while
     * drawing. The one back handler of the search is the only thing that writes it, see `SearchableTopAppBarTitle`.
     */
    var backProgress by mutableFloatStateOf(0f)

    /** What the list is actually narrowed by, which is nothing at all while the search is closed. */
    val activeQuery: Flow<String> = combine(_isOpen, snapshotFlow { textFieldState.text.toString() }) { isOpen, query ->
        if (isOpen) query else ""
    }

    /** Opens onto an empty field rather than onto the last search, which is a question the user has moved on from. */
    fun open() {
        textFieldState.clearText()
        _isOpen.value = true
    }

    fun close() {
        _isOpen.value = false
    }
}
