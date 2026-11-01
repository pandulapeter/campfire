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

import com.pandulapeter.campfire.chordpro.chords.ChordProTransposer
import com.pandulapeter.campfire.chordpro.edit.ChordProMetadataFields
import com.pandulapeter.campfire.data.model.preferences.UserPreferences
import com.pandulapeter.campfire.presentation.mapper.toPreferFlats

/**
 * How a song file is rewritten to say how it is played where it is shared from, for another ChordPro app to play it the
 * same way: its chords as the shapes fingered above the capo played, which is what a file's chords are, so that the
 * written capo raises them to what sounds here, whichever way the reader reads a capo.
 *
 * @param semitones How far the file's chords, its `{key}`, its `{define}`s, and its tabs' frets move.
 * @param capo The `{capo}` to write, none for 0; null where the file's own is the one played.
 * @param tempo The `{tempo}` to write; null where the file's own is the one played.
 * @param transposition The reader's own transposition, which names the change alongside [capo] and [tempo] as the song's
 *   steppers do; it is not [semitones], which a capo moved too.
 * @param preferFlats The reader's accidentals, as the PDF spells the chords with them; null to follow the song.
 */
internal data class SongAsPlayed(
    val semitones: Int,
    val capo: Int?,
    val tempo: Int?,
    val transposition: Int,
    val preferFlats: Boolean?,
) {

    /**
     * [text] rewritten, every other byte of it kept: its notation, its comments, its layout. Chords that do not move keep
     * their spelling too, which the reader's accidentals would otherwise respell.
     */
    fun applyTo(text: String): String = ChordProMetadataFields.set(
        text = if (semitones == 0) text else ChordProTransposer.transposeText(text, semitones, preferFlats),
        values = buildMap {
            capo?.let { put(ChordProMetadataFields.Field.CAPO, it.takeIf { fret -> fret != 0 }?.toString()) }
            tempo?.let { put(ChordProMetadataFields.Field.TEMPO, it.toString()) }
        },
    )
}

/**
 * How [playback] would be written into its song's file, or null where the file already says it all. The capo written is
 * the one shown ([isCapoShownFor]), none for a keyboard player who keeps the key, whose chords are then written as they
 * sound: what sounds here is the file's chords moved by [soundingTransposition], so the shapes above a capo of `c` are
 * those chords moved down by `c`.
 */
internal fun songAsPlayedOf(playback: SongPlayback, preferences: UserPreferences?): SongAsPlayed? {
    val capo = if (playback.isCapoShownFor(preferences)) playback.capo.fret else 0
    val semitones = wrapTransposition(playback.soundingTransposition - capo)
    val writtenCapo = capo.takeIf { it != playback.capo.songFret }
    val tempo = playback.tempo.takeUnless { it.isDefault }?.bpm
    return if (semitones == 0 && writtenCapo == null && tempo == null) {
        null
    } else {
        SongAsPlayed(
            semitones = semitones,
            capo = writtenCapo,
            tempo = tempo,
            transposition = playback.transposition,
            preferFlats = preferences?.chordSpelling?.accidentals?.toPreferFlats(),
        )
    }
}

/** [text] of the song file [fileName] as it goes out: rewritten where this names how it is played, else as stored. */
internal fun Map<String, SongAsPlayed>.rewritten(fileName: String, text: String) = this[fileName]?.applyTo(text) ?: text
