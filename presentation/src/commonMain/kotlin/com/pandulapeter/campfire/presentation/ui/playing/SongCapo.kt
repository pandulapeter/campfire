/*
 * This file is part of Campfire.
 * Copyright (c) Pandula Péter 2017-2026.
 * https://github.com/pandulapeter/campfire
 *
 * This Source Code Form is subject to the terms of the Mozilla Public License, v. 2.0.
 * If a copy of the MPL was not distributed with this file, You can obtain one at
 * https://mozilla.org/MPL/2.0/.
 */
package com.pandulapeter.campfire.presentation.ui.playing

import com.pandulapeter.campfire.chordpro.model.ChordProSong
import com.pandulapeter.campfire.data.model.domain.Song

/**
 * Every song's capo override, see [SongOverrides]. One band plays a song capoed at the second fret and the next set
 * plays it open, which is the setlist's business rather than the file's, so nothing here writes `{capo}`.
 */
internal typealias Capos = SongOverrides<Int>

/**
 * The fret a song is capoed at where it is opened.
 *
 * @param songFret The song's own `{capo}`, or zero where it names none, which is what a reset goes back to.
 */
internal data class EffectiveCapo(
    val fret: Int,
    val songFret: Int,
) {

    /** Whether nothing overrides the song's own capo, which is what the stepper is drawn plainly for. */
    val isDefault get() = fret == songFret
}

/**
 * The setlist's entry, then the library's override (only for a song opened from the library), then the file's
 * `{capo}`. An override equal to the song's own is no override, so that a `{capo}` edited later shows through; zero
 * is a value of its own, a capo taken off a song whose file asks for one.
 */
internal fun effectiveCapo(song: Song?, setlistFileName: String?, capos: Capos, songFileName: String = song?.fileName.orEmpty()): EffectiveCapo {
    val songFret = song?.capo?.coerceIn(Song.CAPO_RANGE) ?: 0
    val override = capos[songFileName, setlistFileName]?.coerceIn(Song.CAPO_RANGE)
    return EffectiveCapo(fret = override ?: songFret, songFret = songFret)
}

/** The song with its capo line reading [fret], where there is one: what the page and the PDF show for an override. */
internal fun ChordProSong.withCapo(fret: Int?) = if (fret == null) this else copy(metadata = metadata.copy(capo = fret))
