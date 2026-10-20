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

/** What one step back takes away from a screen with no dialog, sheet or menu over it, see [escapeStepOf]. */
internal enum class EscapeStep {
    END_REORDERING,
    CLOSE_SEARCH,
    END_SELECTION,
    GO_BACK,
    CLEAR_FILTERS,
    EXIT,
}

/**
 * The precedence of the ways back out of a screen. The setlist reorder mode goes first. The open search of the list
 * screen on top comes before the back stack, and not only because closing it is the smaller step: selecting a tab
 * rebuilds the stack around it, so the setlists screen is reached with the songs screen still under it, and every step
 * back there would otherwise leave the tab with the search still open behind it. The song selection comes after the
 * search rather than before it, unlike the reorder mode: the selection is put together through the search, and ending
 * the mode is the step that loses something. Only then is a screen left. On the last one, the tag and language filters
 * the songs screen shows ([isFilterShown]) are reset before the app asks to close: a filtered list is a state to leave,
 * like a search, and the close question over it would skip that step.
 */
internal fun escapeStepOf(
    isReordering: Boolean,
    isSearchOpen: Boolean,
    isSelecting: Boolean,
    stackSize: Int,
    isFilterShown: Boolean,
) = when {
    isReordering -> EscapeStep.END_REORDERING
    isSearchOpen -> EscapeStep.CLOSE_SEARCH
    isSelecting -> EscapeStep.END_SELECTION
    stackSize > 1 -> EscapeStep.GO_BACK
    isFilterShown -> EscapeStep.CLEAR_FILTERS
    else -> EscapeStep.EXIT
}
