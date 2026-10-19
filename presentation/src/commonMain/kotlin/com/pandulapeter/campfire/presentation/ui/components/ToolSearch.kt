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

import kotlinx.coroutines.flow.Flow

/**
 * The search of a tool's page (the Chords and the Scales tabs), whose field is always there ([pinnedSearchField]): what
 * it holds, and the caret asked for it by Ctrl / Cmd + F from outside the composition (`ScreenSearches`).
 */
internal interface ToolSearch {

    var query: String

    /**
     * Asks the search field for the caret, with what it holds selected, see [focusSearch]. Events rather than state,
     * for the reason `SearchState.focusRequests` gives.
     */
    val searchFocusRequests: Flow<Unit>

    /** What Ctrl / Cmd + F does on the tab, see `ScreenSearches.openCurrentSearch`. */
    fun focusSearch()
}
