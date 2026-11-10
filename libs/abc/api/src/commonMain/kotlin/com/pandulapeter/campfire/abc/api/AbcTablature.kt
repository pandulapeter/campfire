/*
 * This file is part of Campfire.
 * Copyright (c) Pandula Péter 2017-2026.
 * https://github.com/pandulapeter/campfire
 *
 * This Source Code Form is subject to the terms of the Mozilla Public License, v. 2.0.
 * If a copy of the MPL was not distributed with this file, You can obtain one at
 * https://mozilla.org/MPL/2.0/.
 */
package com.pandulapeter.campfire.abc.api

/**
 * How the string tablature of a tune (a voice in the tab clef) is fretted for its reader. The renderer works a tab's
 * frets out from its notes, so a tab is moved to other strings, a capo, or a transposition by saying where the strings
 * are rather than by rewriting its frets.
 *
 * A voice whose notes would not all fit on the neck is moved by the fewest whole octaves that fit them, the same for the
 * whole voice; one that no octave fits stays in its own tuning, moved by the transposition alone. A string a note is
 * written on (`!3s!`) is kept where the note still fits on it, and left to the renderer where it does not.
 *
 * @property tuning The open strings of the reader's instrument as MIDI note numbers, the lowest string first, which every
 *   tab voice is moved to; null keeps each voice in the tuning it names.
 * @property capo The fret the tab is counted from, added to the one a voice names (`capo=`): the notes sound as written
 *   and the frets are counted above it, so a capo keeps the shapes where the notes are moved up by as much.
 * @property maxFret The highest fret a note may be put on.
 * @property noteNames The names of the twelve pitch classes from C up, which name each tab staff's strings in front of it
 *   in place of the renderer's TAB, the highest of two strings of one name in lowercase.
 */
public data class AbcTablature(
    val tuning: List<Int>?,
    val capo: Int,
    val maxFret: Int,
    val noteNames: List<String>,
)
