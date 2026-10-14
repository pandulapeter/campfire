/*
 * This file is part of Campfire.
 * Copyright (c) Pandula Péter 2017-2026.
 * https://github.com/pandulapeter/campfire
 *
 * This Source Code Form is subject to the terms of the Mozilla Public License, v. 2.0.
 * If a copy of the MPL was not distributed with this file, You can obtain one at
 * https://mozilla.org/MPL/2.0/.
 */
package com.pandulapeter.campfire.chordpro.model

import com.pandulapeter.campfire.chordpro.ChordNotation

/**
 * The reader a song's tablature is shown to, which [com.pandulapeter.campfire.chordpro.tabs.ChordProTabs] and the
 * transposition move it for. Everything that knows about instruments is handed in, since this module knows the
 * instruments it draws chord shapes for and not the ones a tab may be written for (a bass, a seven-string guitar).
 *
 * @property selectors The names a tab's selector may call the reader's instrument by, the best match first: a
 *   twelve-string guitar's own name and then `guitar`. Of the tabs written for several instruments in a row, the ones
 *   for the first of these the row has are shown.
 * @property tuning The strings the tabs are moved to, null where they are not moved at all and only named (the editor's
 *   preview, which shows a song as it is written, or an instrument nothing is known about).
 * @property hidesTabs Whether the reader's instrument has nothing a tab could be played on (a keyboard, a violin):
 *   every tab is then left out, a notice standing in its place.
 * @property assumedTuning The standard tuning of the instrument a staff is written for, given the pitch classes it names
 *   its strings by (the bottom string first, as [TabTuning.strings] are, null for a string it leaves unnamed) and the
 *   selector of its environment, or null for a number of strings no instrument has: the instrument the selector names
 *   where it has as many strings, whatever the staff names; else the reader's own, the one a tab of that many strings
 *   is most likely for, or any other with as many — the first of them whose open strings sound the names the staff
 *   writes, and the first of them where none does (a guitar tuned down names strings no instrument has open). It says
 *   what instrument the tab is for and what octave each string it names is in; a staff that names nothing is in the
 *   tuning itself.
 * @property stringName The name a string tuned to a MIDI note is written with in front of the staff, in the notation
 *   the reader reads chords in, before the top string of a staff that names its note twice is put in lowercase.
 * @property notation The notation the reader reads chords in, which [stringName] names in too: a staff shown with the
 *   names it is written with (one that is not moved) has them respelled in it, its own spelling of an accidental kept,
 *   so that a German reader is never shown a `B|` that reads to them as B flat.
 */
public class TabPlayer(
    public val selectors: List<String>,
    public val tuning: TabTuning?,
    public val hidesTabs: Boolean,
    public val assumedTuning: (notes: List<Int?>, selector: String?) -> TabTuning?,
    public val stringName: (note: Int) -> String,
    public val notation: ChordNotation,
)
