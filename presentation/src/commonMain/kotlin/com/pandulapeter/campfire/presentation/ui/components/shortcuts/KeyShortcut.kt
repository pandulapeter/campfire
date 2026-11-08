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
 * table's ([platforms]): elsewhere it matches no key and shows no hint. Where it is listed in the shortcuts sheet is
 * [scope], so that a new entry cannot be left out of it; keys that mean something else on another screen (Page Down
 * turning a print preview's page, stepping through a song) are an entry each.
 */
internal enum class KeyShortcut(
    val chord: KeyChord,
    val scope: ShortcutScope,
    val platforms: ShortcutPlatforms = ShortcutPlatforms.EVERYWHERE,
) {
    /** Opens the shortcuts sheet from anywhere, a field being typed in included. */
    SHORTCUTS(KeyChord.command(Key.Slash, "/", alternates = setOf(Key.NumPadDivide)), ShortcutScope.APP),

    /** The same outside a field, where `?` is no character typed: Shift + slash, as on a US layout. */
    SHORTCUTS_QUESTION_MARK(KeyChord.plain(Key.Slash, "?", shift = KeyChord.Shift.IN_LABEL), ShortcutScope.APP),

    /** The window's Escape, which goes back or closes what is open; a phone's or a tablet's own back is the system's. */
    BACK(KeyChord.plain(Key.Escape, label = null), ShortcutScope.APP, ShortcutPlatforms.DESKTOP_AND_WEB),
    FIND(KeyChord.command(Key.F, "F"), ShortcutScope.APP),
    REPLACE(KeyChord.command(Key.R, "R"), ShortcutScope.EDITOR),

    /** Saves a sheet or the editor; on song details and a setlist, Share or export. */
    SAVE(KeyChord.command(Key.S, "S"), ShortcutScope.APP),
    EDIT(KeyChord.command(Key.E, "E"), ShortcutScope.SONG),
    PASTE(KeyChord.command(Key.V, "V"), ShortcutScope.LISTS),
    ZOOM_IN(KeyChord.command(Key.Equals, "+", alternates = setOf(Key.Plus, Key.NumPadAdd)), ShortcutScope.SONG),
    ZOOM_OUT(KeyChord.command(Key.Minus, "-", alternates = setOf(Key.NumPadSubtract)), ShortcutScope.SONG),
    ZOOM_RESET(KeyChord.command(Key.Zero, "0", alternates = setOf(Key.NumPad0)), ShortcutScope.SONG),
    BOLD(KeyChord.command(Key.B, "B", shift = KeyChord.Shift.ABSENT), ShortcutScope.EDITOR),
    ITALIC(KeyChord.command(Key.I, "I", shift = KeyChord.Shift.ABSENT), ShortcutScope.EDITOR),
    UNDERLINE(KeyChord.command(Key.U, "U", shift = KeyChord.Shift.ABSENT), ShortcutScope.EDITOR),

    /** The New song and New setlist entries of the list screens' New menu. */
    NEW(KeyChord.command(Key.N, "N", shift = KeyChord.Shift.ABSENT), ShortcutScope.LISTS, ShortcutPlatforms.NOT_WEB),

    /** Select all of the songs screen's selection mode; without Shift, which is Chrome's tab search. */
    SELECT_ALL(KeyChord.command(Key.A, "A", shift = KeyChord.Shift.ABSENT), ShortcutScope.LISTS),
    UNDO(KeyChord.command(Key.Z, "Z", shift = KeyChord.Shift.ABSENT), ShortcutScope.EDITOR),
    REDO(KeyChord.command(Key.Z, "Z", shift = KeyChord.Shift.REQUIRED), ShortcutScope.EDITOR),

    /** Redo as Windows and Linux also press it, never named in a hint, since [REDO] is; the sheet lists it beside it. */
    REDO_ALTERNATE(KeyChord.command(Key.Y, "Y", shift = KeyChord.Shift.ABSENT), ShortcutScope.EDITOR),

    /** The editor search's next and previous match, from anywhere in its sheet. */
    FIND_NEXT(KeyChord.plain(Key.F3, "F3", shift = KeyChord.Shift.ABSENT), ShortcutScope.EDITOR),
    FIND_PREVIOUS(KeyChord.plain(Key.F3, "F3", shift = KeyChord.Shift.REQUIRED), ShortcutScope.EDITOR),

    /** The same from the search's query, where Enter is what a search field is finished with. */
    FIND_NEXT_FROM_QUERY(
        KeyChord.plain(Key.Enter, label = null, shift = KeyChord.Shift.ABSENT, alternates = setOf(Key.NumPadEnter)),
        ShortcutScope.EDITOR,
    ),
    FIND_PREVIOUS_FROM_QUERY(
        KeyChord.plain(Key.Enter, label = null, shift = KeyChord.Shift.REQUIRED, alternates = setOf(Key.NumPadEnter)),
        ShortcutScope.EDITOR,
    ),

    /** The transposition stepper of the song being read. With Shift, since Ctrl / Cmd + an arrow moves by words and lines. */
    TRANSPOSE_UP(KeyChord.command(Key.DirectionUp, "↑", shift = KeyChord.Shift.REQUIRED), ShortcutScope.SONG),
    TRANSPOSE_DOWN(KeyChord.command(Key.DirectionDown, "↓", shift = KeyChord.Shift.REQUIRED), ShortcutScope.SONG),

    /** Starts and stops a song scrolling by itself. */
    PLAY(KeyChord.plain(Key.Spacebar, label = null), ShortcutScope.SONG),

    /**
     * The song's Up and Down: its step buttons' previous and next where it has them, a scroll elsewhere
     * (`songKeyboardShortcuts`). What a page turner pedal sends, with Shift or without.
     */
    SONG_UP(KeyChord.plain(Key.DirectionUp, "↑"), ShortcutScope.SONG),
    SONG_DOWN(KeyChord.plain(Key.DirectionDown, "↓"), ShortcutScope.SONG),

    /** The song's step buttons alone, which the other kind of pedal sends. */
    SONG_PAGE_UP(KeyChord.plain(Key.PageUp, label = null), ShortcutScope.SONG),
    SONG_PAGE_DOWN(KeyChord.plain(Key.PageDown, label = null), ShortcutScope.SONG),

    /** The song of the setlist lying that way in the pager, which in a right-to-left layout is the next one for Left. */
    SONG_LEFT(KeyChord.plain(Key.DirectionLeft, "←"), ShortcutScope.SONG),
    SONG_RIGHT(KeyChord.plain(Key.DirectionRight, "→"), ShortcutScope.SONG),

    /**
     * Starts and stops the click, on a song and on the Metronome tab alike. Not Space, which a focused button takes as its
     * press, and a click on the tab leaves a beat, a chip or the navigation rail focused.
     */
    METRONOME(KeyChord.plain(Key.M, "M"), ShortcutScope.METRONOME),

    /** The Tap segment of the tempo stepper, wherever [METRONOME] works and the stepper is there. */
    TAP_TEMPO(KeyChord.plain(Key.T, "T"), ShortcutScope.METRONOME),

    /** The print preview's pages: the previous and the next, the first and the last, and the one lying either way. */
    PREVIEW_PAGE_UP(KeyChord.plain(Key.PageUp, label = null), ShortcutScope.PRINT_PREVIEW),
    PREVIEW_PAGE_DOWN(KeyChord.plain(Key.PageDown, label = null), ShortcutScope.PRINT_PREVIEW),
    PREVIEW_HOME(KeyChord.plain(Key.MoveHome, label = null), ShortcutScope.PRINT_PREVIEW),
    PREVIEW_END(KeyChord.plain(Key.MoveEnd, label = null), ShortcutScope.PRINT_PREVIEW),
    PREVIEW_LEFT(KeyChord.plain(Key.DirectionLeft, "←"), ShortcutScope.PRINT_PREVIEW),
    PREVIEW_RIGHT(KeyChord.plain(Key.DirectionRight, "→"), ShortcutScope.PRINT_PREVIEW),

    /** The main screens switched on, in the chrome's order, Settings left out: it has [SETTINGS]. */
    SCREEN_1(KeyChord.command(Key.One, "1", alternates = setOf(Key.NumPad1)), ShortcutScope.APP, ShortcutPlatforms.DESKTOP_AND_WEB),
    SCREEN_2(KeyChord.command(Key.Two, "2", alternates = setOf(Key.NumPad2)), ShortcutScope.APP, ShortcutPlatforms.DESKTOP_AND_WEB),
    SCREEN_3(KeyChord.command(Key.Three, "3", alternates = setOf(Key.NumPad3)), ShortcutScope.APP, ShortcutPlatforms.DESKTOP_AND_WEB),
    SCREEN_4(KeyChord.command(Key.Four, "4", alternates = setOf(Key.NumPad4)), ShortcutScope.APP, ShortcutPlatforms.DESKTOP_AND_WEB),
    SCREEN_5(KeyChord.command(Key.Five, "5", alternates = setOf(Key.NumPad5)), ShortcutScope.APP, ShortcutPlatforms.DESKTOP_AND_WEB),
    SCREEN_6(KeyChord.command(Key.Six, "6", alternates = setOf(Key.NumPad6)), ShortcutScope.APP, ShortcutPlatforms.DESKTOP_AND_WEB),
    SETTINGS(KeyChord.command(Key.Comma, ",", shift = KeyChord.Shift.ABSENT), ShortcutScope.APP, ShortcutPlatforms.DESKTOP_AND_WEB),
    ;

    /** Whether this works on the platform the app runs on. */
    val isAvailable get() = platforms.includes(appBuild)

    fun matches(event: KeyEvent) = isAvailable && chord.matches(event)

    companion object {
        /** [SCREEN_1] to [SCREEN_6], in order. */
        val screens get() = listOf(SCREEN_1, SCREEN_2, SCREEN_3, SCREEN_4, SCREEN_5, SCREEN_6)
    }
}
