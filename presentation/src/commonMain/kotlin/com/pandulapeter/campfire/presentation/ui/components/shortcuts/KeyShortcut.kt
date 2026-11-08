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
import com.pandulapeter.campfire.presentation.ui.platform.appBuild

/**
 * Every keyboard shortcut of the app, each declared once: the handlers match keys against this table, and the hints
 * (tooltips, menu entries) are written from it, so that a hint cannot name keys that do not work. Where a shortcut
 * works is its handler's business (a screen, a sheet, the app's root); one entry may serve several screens that mean
 * the same by it, as [FIND] does the list screens' search and the editor's. On which platforms it works is the
 * table's ([platforms]): elsewhere it matches no key and shows no hint.
 */
internal enum class KeyShortcut(
    val chord: KeyChord,
    val platforms: ShortcutPlatforms = ShortcutPlatforms.EVERYWHERE,
) {
    FIND(KeyChord.command(Key.F, "F")),
    REPLACE(KeyChord.command(Key.R, "R")),

    /** Saves a sheet or the editor; on song details and a setlist, Share or export. */
    SAVE(KeyChord.command(Key.S, "S")),
    EDIT(KeyChord.command(Key.E, "E")),
    PASTE(KeyChord.command(Key.V, "V")),
    ZOOM_IN(KeyChord.command(Key.Equals, "+", alternates = setOf(Key.Plus, Key.NumPadAdd))),
    ZOOM_OUT(KeyChord.command(Key.Minus, "-", alternates = setOf(Key.NumPadSubtract))),
    ZOOM_RESET(KeyChord.command(Key.Zero, "0", alternates = setOf(Key.NumPad0))),
    BOLD(KeyChord.command(Key.B, "B", shift = KeyChord.Shift.ABSENT)),
    ITALIC(KeyChord.command(Key.I, "I", shift = KeyChord.Shift.ABSENT)),
    UNDERLINE(KeyChord.command(Key.U, "U", shift = KeyChord.Shift.ABSENT)),

    /** The New song and New setlist entries of the list screens' New menu. */
    NEW(KeyChord.command(Key.N, "N", shift = KeyChord.Shift.ABSENT), ShortcutPlatforms.NOT_WEB),

    /** Select all of the songs screen's selection mode; without Shift, which is Chrome's tab search. */
    SELECT_ALL(KeyChord.command(Key.A, "A", shift = KeyChord.Shift.ABSENT)),
    UNDO(KeyChord.command(Key.Z, "Z", shift = KeyChord.Shift.ABSENT)),
    REDO(KeyChord.command(Key.Z, "Z", shift = KeyChord.Shift.REQUIRED)),

    /** Redo as Windows and Linux also press it, never named in a hint, since [REDO] is. */
    REDO_ALTERNATE(KeyChord.command(Key.Y, "Y", shift = KeyChord.Shift.ABSENT)),

    /** The editor search's next and previous match, from anywhere in its sheet. */
    FIND_NEXT(KeyChord.plain(Key.F3, "F3", shift = KeyChord.Shift.ABSENT)),
    FIND_PREVIOUS(KeyChord.plain(Key.F3, "F3", shift = KeyChord.Shift.REQUIRED)),

    /** The same from the search's query, where Enter is what a search field is finished with. */
    FIND_NEXT_FROM_QUERY(KeyChord.plain(Key.Enter, label = null, shift = KeyChord.Shift.ABSENT, alternates = setOf(Key.NumPadEnter))),
    FIND_PREVIOUS_FROM_QUERY(KeyChord.plain(Key.Enter, label = null, shift = KeyChord.Shift.REQUIRED, alternates = setOf(Key.NumPadEnter))),

    /** The transposition stepper of the song being read. With Shift, since Ctrl / Cmd + an arrow moves by words and lines. */
    TRANSPOSE_UP(KeyChord.command(Key.DirectionUp, "↑", shift = KeyChord.Shift.REQUIRED)),
    TRANSPOSE_DOWN(KeyChord.command(Key.DirectionDown, "↓", shift = KeyChord.Shift.REQUIRED)),

    /** Starts and stops a song scrolling by itself. */
    PLAY(KeyChord.plain(Key.Spacebar, label = null)),

    /**
     * Starts and stops the click, on a song and on the Metronome tab alike. Not Space, which a focused button takes as its
     * press, and a click on the tab leaves a beat, a chip or the navigation rail focused.
     */
    METRONOME(KeyChord.plain(Key.M, "M")),

    /** The Tap segment of the tempo stepper, wherever [METRONOME] works and the stepper is there. */
    TAP_TEMPO(KeyChord.plain(Key.T, "T")),

    /** The main screens switched on, in the chrome's order, Settings left out: it has [SETTINGS]. */
    SCREEN_1(KeyChord.command(Key.One, "1", alternates = setOf(Key.NumPad1)), ShortcutPlatforms.DESKTOP_AND_WEB),
    SCREEN_2(KeyChord.command(Key.Two, "2", alternates = setOf(Key.NumPad2)), ShortcutPlatforms.DESKTOP_AND_WEB),
    SCREEN_3(KeyChord.command(Key.Three, "3", alternates = setOf(Key.NumPad3)), ShortcutPlatforms.DESKTOP_AND_WEB),
    SCREEN_4(KeyChord.command(Key.Four, "4", alternates = setOf(Key.NumPad4)), ShortcutPlatforms.DESKTOP_AND_WEB),
    SCREEN_5(KeyChord.command(Key.Five, "5", alternates = setOf(Key.NumPad5)), ShortcutPlatforms.DESKTOP_AND_WEB),
    SCREEN_6(KeyChord.command(Key.Six, "6", alternates = setOf(Key.NumPad6)), ShortcutPlatforms.DESKTOP_AND_WEB),
    SETTINGS(KeyChord.command(Key.Comma, ",", shift = KeyChord.Shift.ABSENT), ShortcutPlatforms.DESKTOP_AND_WEB),
    ;

    /** Whether this works on the platform the app runs on. */
    val isAvailable get() = platforms.includes(appBuild)

    fun matches(event: KeyEvent) = isAvailable && chord.matches(event)

    companion object {
        /** [SCREEN_1] to [SCREEN_6], in order. */
        val screens get() = listOf(SCREEN_1, SCREEN_2, SCREEN_3, SCREEN_4, SCREEN_5, SCREEN_6)
    }
}
