/*
 * This file is part of Campfire.
 * Copyright (c) Pandula Péter 2017-2026.
 * https://github.com/pandulapeter/campfire
 *
 * This Source Code Form is subject to the terms of the Mozilla Public License, v. 2.0.
 * If a copy of the MPL was not distributed with this file, You can obtain one at
 * https://mozilla.org/MPL/2.0/.
 */
package com.pandulapeter.campfire.presentation.ui.components.shortcuts

import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEvent

/**
 * Every keyboard shortcut of the app, each declared once: the handlers match keys against this table, and the hints
 * (tooltips, menu entries) are written from it, so that a hint cannot name keys that do not work. Where a shortcut
 * works is its handler's business (a screen, a sheet, the app's root); one entry may serve several screens that mean
 * the same by it, as [FIND] does the list screens' search and the editor's.
 */
internal enum class KeyShortcut(val chord: KeyChord) {
    FIND(KeyChord.command(Key.F, "F")),
    REPLACE(KeyChord.command(Key.R, "R")),
    SAVE(KeyChord.command(Key.S, "S")),
    EDIT(KeyChord.command(Key.E, "E")),
    PASTE(KeyChord.command(Key.V, "V")),
    ZOOM_IN(KeyChord.command(Key.Equals, "+", alternates = setOf(Key.Plus, Key.NumPadAdd))),
    ZOOM_OUT(KeyChord.command(Key.Minus, "-", alternates = setOf(Key.NumPadSubtract))),
    ZOOM_RESET(KeyChord.command(Key.Zero, "0", alternates = setOf(Key.NumPad0))),
    BOLD(KeyChord.command(Key.B, "B", shift = KeyChord.Shift.ABSENT)),
    ITALIC(KeyChord.command(Key.I, "I", shift = KeyChord.Shift.ABSENT)),
    UNDERLINE(KeyChord.command(Key.U, "U", shift = KeyChord.Shift.ABSENT)),

    /** Plays and stops: the Metronome tab's click, a song scrolling by itself. */
    PLAY(KeyChord.plain(Key.Spacebar, label = null)),

    /** Starts and stops the click from a song as well as from the Metronome tab. */
    METRONOME(KeyChord.plain(Key.M, "M")),
    ;

    fun matches(event: KeyEvent) = chord.matches(event)
}
