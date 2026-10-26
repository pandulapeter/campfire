/*
 * This file is part of Campfire.
 * Copyright (c) Pandula Péter 2017-2026.
 * https://github.com/pandulapeter/campfire
 *
 * This Source Code Form is subject to the terms of the Mozilla Public License, v. 2.0.
 * If a copy of the MPL was not distributed with this file, You can obtain one at
 * https://mozilla.org/MPL/2.0/.
 */
package com.pandulapeter.campfire.chordpro.abc

/**
 * One ABC tune written as a ChordPro song by [AbcToChordPro.convert].
 *
 * @property text the song's ChordPro text.
 * @property title the `{title}` it was given (the tune's first `T:`, else the fallback), or null for none.
 * @property hasChords whether the tune had a chord symbol, and the song a chord grid: one without is a melody whose
 * notation is all there is, never "lyrics only".
 */
public data class AbcSong(
    val text: String,
    val title: String?,
    val hasChords: Boolean,
)
