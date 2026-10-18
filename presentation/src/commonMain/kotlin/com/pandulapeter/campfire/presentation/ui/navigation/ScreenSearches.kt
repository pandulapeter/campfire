/*
 * This file is part of Campfire.
 * Copyright (c) Pandula Péter 2017-2026.
 * https://github.com/pandulapeter/campfire
 *
 * This Source Code Form is subject to the terms of the Mozilla Public License, v. 2.0.
 * If a copy of the MPL was not distributed with this file, You can obtain one at
 * https://mozilla.org/MPL/2.0/.
 */
package com.pandulapeter.campfire.presentation.ui.navigation

import com.pandulapeter.campfire.presentation.ui.components.SearchState
import com.pandulapeter.campfire.presentation.ui.dialogs.DialogHost
import com.pandulapeter.campfire.presentation.ui.screens.chords.ChordLibraryState

/**
 * The searches of the screens as the keys that reach them from outside the composition see them: Ctrl / Cmd + F, which
 * the desktop window and the web page hear first, and the desktop window's Escape. [top] is the screen on top of the
 * back stack, whose search they are about.
 */
internal class ScreenSearches(
    private val top: () -> CampfireDestination?,
    private val dialogHost: DialogHost,
    private val songsSearch: () -> SearchState,
    private val setlistsSearch: () -> SearchState,
    private val importReportSearch: () -> SearchState,
    private val chordLibrary: () -> ChordLibraryState,
    private val isSetlistReordering: () -> Boolean,
) {

    /**
     * The search a back gesture is about, which is the one belonging to the screen that is on top. Only the two list
     * screens have one that opens and closes, and a search left open on a list screen is no business of the song that
     * was opened from it: there, back is back. The import screen's field is always there, so there is nothing for a
     * back gesture to close before the screen.
     *
     * The screens answer their own back gesture with a navigation event handler registered inside them, which is
     * composed after the navigation's own and therefore wins on its own; this is here for the desktop, whose window
     * key handler decides what Escape means from outside the composition entirely.
     */
    val currentSearch: SearchState?
        get() = when (top()) {
            CampfireDestination.Songs -> songsSearch()
            CampfireDestination.Setlists -> setlistsSearch()
            else -> null
        }

    /**
     * Whether a step back takes a search away before the screen on top: the open search of a list screen
     * ([currentSearch]), or the query of the Chords tab, whose field is always there and is emptied instead. For the
     * desktop's window key handler, as [currentSearch] is; the tab answers the back gesture itself.
     */
    val isCurrentSearchOpen: Boolean
        get() = currentSearch?.isOpen?.value == true || isChordLibraryTop && chordLibrary().query.isNotEmpty()

    /** Takes away what [isCurrentSearchOpen] says is there. */
    fun closeCurrentSearch() {
        currentSearch?.close()
        if (isChordLibraryTop) chordLibrary().query = ""
    }

    private val isChordLibraryTop get() = top() == CampfireDestination.Chords

    /**
     * Answers Ctrl / Cmd + F, which the desktop window and the web page both hear before anything in the composition
     * does: nothing on a list screen is focused while its search is closed, and a key event only travels along the
     * focus path. It opens the search of the list screen that is on top, or brings the caret back into it if it is
     * open already - the import screen's and the Chords tab's fields, which are always there, being given the caret the
     * same way - and answers whether it did, so that the key is left to whoever else wants it everywhere else - the
     * browser's own find bar among them. A dialog, a sheet or an overflow menu keeps it from reaching the screen under
     * it, the way it keeps Escape from reaching it.
     */
    fun openCurrentSearch(): Boolean {
        if (dialogHost.visibleDialog.value != null || dialogHost.overlayState.isAnyMenuOpen || isSetlistReordering()) return false
        if (isChordLibraryTop) {
            chordLibrary().focusSearch()
            return true
        }
        val search = currentSearch
            ?: importReportSearch().takeIf { top() == CampfireDestination.ImportReport }
            ?: return false
        search.openOrFocus()
        return true
    }
}
