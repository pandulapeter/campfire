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

/**
 * The reader a song's tablature is shown to, which [com.pandulapeter.campfire.chordpro.chords.ChordProTabs] and the
 * transposition move it for. Everything that knows about instruments is handed in, since this module knows the
 * instruments it draws chord shapes for and not the ones a tab may be written for (a bass, a seven-string guitar).
 *
 * @property selectors The names a tab's selector may call the reader's instrument by, the best match first: a
 *   twelve-string guitar's own name and then `guitar`. Of the tabs written for several instruments in a row, the ones
 *   for the first of these the row has are shown.
 * @property tuning The strings the tabs are moved to, null where they are not moved at all and only named (the editor's
 *   preview, which shows a song as it is written, or an instrument nothing is known about).
 * @property hidesTabs Whether the reader's instrument has nothing a tab could be played on (a keyboard): every tab is
 *   then left out, a notice standing in its place.
 * @property assumedTuning The tuning a tab of so many strings is written in where its staff does not name its strings,
 *   or null for a number of strings no instrument has: the standard tuning of the instrument its selector names, of the
 *   reader's own instrument where that has as many strings, and of the one a tab of that many strings is most likely
 *   for otherwise. It also says what octave each string a staff names is in, and what instrument the tab is for.
 * @property stringName The name a string tuned to a MIDI note is written with in front of the staff, in the notation
 *   the reader reads chords in, before the top string of a staff that names its note twice is put in lowercase.
 */
public class TabPlayer(
    public val selectors: List<String>,
    public val tuning: TabTuning?,
    public val hidesTabs: Boolean,
    public val assumedTuning: (strings: Int, selector: String?) -> TabTuning?,
    public val stringName: (note: Int) -> String,
)
