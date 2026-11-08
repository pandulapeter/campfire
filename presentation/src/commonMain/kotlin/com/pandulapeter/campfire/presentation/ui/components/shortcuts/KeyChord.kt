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

import androidx.compose.runtime.Immutable
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEvent
import androidx.compose.ui.input.key.isAltPressed
import androidx.compose.ui.input.key.isCtrlPressed
import androidx.compose.ui.input.key.isMetaPressed
import androidx.compose.ui.input.key.isShiftPressed
import androidx.compose.ui.input.key.key

/**
 * The keys of one [KeyShortcut]: [key] (or one of [alternates], the same key elsewhere on the keyboard), with Ctrl or
 * Cmd where [isCommand], and Shift as [shift] says. Never with Alt, whatever else: AltGr arrives as Ctrl + Alt on
 * Windows and the web, and with these keys types a character on some layouts. Ctrl and Cmd are one, so that a Mac's
 * keyboard and a PC's both work wherever the app runs, as they always have.
 *
 * @param label How [key] is written in a hint: its character, or null for a key with a name of its own, which is a
 *   translated string ([KeyNames]).
 */
@Immutable
internal data class KeyChord(
    val key: Key,
    val label: String?,
    val isCommand: Boolean,
    val shift: Shift = Shift.IGNORED,
    val alternates: Set<Key> = emptySet(),
) {

    /** What Shift does to a chord. */
    enum class Shift {
        /** Not looked at: the plus of a US layout is Shift + equals, and Space and M are the same keys with it. */
        IGNORED,

        /** Must not be held, so that the chord with it stays the browser's or the platform's (Ctrl + Shift + I). */
        ABSENT,

        /** Must be held, and is written in the hint. */
        REQUIRED,

        /** Must be held, and is not written: the label is the character the two type together (`?`, Shift + slash). */
        IN_LABEL,
    }

    fun matches(key: Key, isCtrlOrMeta: Boolean, isShift: Boolean, isAlt: Boolean) =
        (key == this.key || key in alternates) && isCtrlOrMeta == isCommand && !isAlt && when (shift) {
            Shift.IGNORED -> true
            Shift.ABSENT -> !isShift
            Shift.REQUIRED, Shift.IN_LABEL -> isShift
        }

    fun matches(event: KeyEvent) = matches(
        key = event.key,
        isCtrlOrMeta = event.isCtrlPressed || event.isMetaPressed,
        isShift = event.isShiftPressed,
        isAlt = event.isAltPressed,
    )

    companion object {
        fun command(key: Key, label: String, shift: Shift = Shift.IGNORED, alternates: Set<Key> = emptySet()) =
            KeyChord(key = key, label = label, isCommand = true, shift = shift, alternates = alternates)

        fun plain(key: Key, label: String?, shift: Shift = Shift.IGNORED, alternates: Set<Key> = emptySet()) =
            KeyChord(key = key, label = label, isCommand = false, shift = shift, alternates = alternates)
    }
}
