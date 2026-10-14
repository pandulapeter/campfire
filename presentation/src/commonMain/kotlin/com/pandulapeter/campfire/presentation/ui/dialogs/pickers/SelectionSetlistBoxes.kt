/*
 * This file is part of Campfire.
 * Copyright (c) Pandula Péter 2017-2026.
 * https://github.com/pandulapeter/campfire
 *
 * This Source Code Form is subject to the terms of the Mozilla Public License, v. 2.0.
 * If a copy of the MPL was not distributed with this file, You can obtain one at
 * https://mozilla.org/MPL/2.0/.
 */
package com.pandulapeter.campfire.presentation.ui.dialogs.pickers

import androidx.compose.ui.state.ToggleableState
import com.pandulapeter.campfire.data.model.domain.Setlist

/**
 * What [setlist] holds of [selection] for the selection's Choose setlists sheet: what it held when the sheet was first
 * tapped on it, as [recorded] keeps it, or what it holds now where the sheet has not touched it yet - a setlist the
 * sheet has not written to is what the library says it is, however it got there (created from the sheet's own New
 * setlist, or synced in or changed by a sync run while the sheet is open).
 */
internal fun heldOf(recorded: Map<String, Set<String>>, setlist: Setlist, selection: Set<String>): Set<String> =
    recorded[setlist.fileName] ?: setlist.entries.mapNotNullTo(hashSetOf()) { entry -> entry.songFileName.takeIf { it in selection } }

/** [recorded] with [setlist] in it, as [heldOf] reads it now, where it is not in it yet: called by the sheet's tap. */
internal fun withHeldRecorded(recorded: Map<String, Set<String>>, setlist: Setlist, selection: Set<String>): Map<String, Set<String>> =
    if (setlist.fileName in recorded) recorded else recorded + (setlist.fileName to heldOf(recorded, setlist, selection))

/**
 * The box of a setlist in the selection's Choose setlists sheet: ticked when it holds every one of [selection], empty
 * when it holds none of them, and half ticked in between. [held] is what [heldOf] says it holds and [added] what the
 * sheet has put into it since, since the boxes follow the sheet's own record rather than the library. It counts over
 * [held] and [added], a setlist's worth each, rather than over [selection], which Select all makes the whole library.
 */
internal fun selectionBoxState(selection: Set<String>, held: Set<String>, added: Set<String>): ToggleableState {
    val count = held.count { it in selection } + added.count { it in selection && it !in held }
    return when {
        count == 0 -> ToggleableState.Off
        count == selection.size -> ToggleableState.On
        else -> ToggleableState.Indeterminate
    }
}
