/*
 * This file is part of Campfire.
 * Copyright (c) Pandula Péter 2017-2026.
 * https://github.com/pandulapeter/campfire
 *
 * This Source Code Form is subject to the terms of the Mozilla Public License, v. 2.0.
 * If a copy of the MPL was not distributed with this file, You can obtain one at
 * https://mozilla.org/MPL/2.0/.
 */
package com.pandulapeter.campfire.presentation.ui.tabs

import com.pandulapeter.campfire.abc.api.AbcTablature
import com.pandulapeter.campfire.chordpro.voicings.ChordVoicings

/**
 * How the tab staves of a song's sheet music are fretted for this reader, as its `{start_of_tab}` runs are: on the
 * player's strings, counted from [capo] (`SongPlayback.tabCapoFor`), their strings named in the reader's notation. Where
 * nothing is moved (the editor's preview) each keeps the strings it names.
 */
internal fun TabReading.abcTablature(capo: Int) = AbcTablature(
    tuning = player.tuning?.strings,
    capo = capo,
    maxFret = ChordVoicings.MAX_FRET,
    noteNames = (0 until SEMITONES_IN_OCTAVE).map(player.stringName),
)

private const val SEMITONES_IN_OCTAVE = 12
