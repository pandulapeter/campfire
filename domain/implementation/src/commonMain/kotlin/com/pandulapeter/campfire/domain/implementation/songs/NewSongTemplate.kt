/*
 * This file is part of Campfire.
 * Copyright (c) Pandula Péter 2017-2026.
 * https://github.com/pandulapeter/campfire
 *
 * This Source Code Form is subject to the terms of the Mozilla Public License, v. 2.0.
 * If a copy of the MPL was not distributed with this file, You can obtain one at
 * https://mozilla.org/MPL/2.0/.
 */
package com.pandulapeter.campfire.domain.implementation.songs

import com.pandulapeter.campfire.chordpro.edit.ChordProMetadataFields.Field

/**
 * A new song with no text of its own holds what the user typed, a line for each of the four values the song is played
 * by and the skeleton of a first verse, so that the editor opens on something that is already shaped like a song rather
 * than on an empty page. The capo, the tempo and the time signature start on the values the app plays a song by when it
 * declares none (no capo, the metronome's 120 and 4/4), so writing them changes nothing until one is edited; the key has
 * no such default and starts empty, as does the artist where none was given, to be filled in.
 */
internal fun newSongTemplate(title: String, artist: String) = buildString {
    append("{title: ").append(title.trim()).append("}\n")
    append("{artist: ").append(artist.trim()).append("}\n")
    append("{key: }\n")
    append("{capo: 0}\n")
    append("{tempo: 120}\n")
    append("{time: 4/4}\n")
    append("\n")
    append("{start_of_verse}\n")
    // The blank line the editor puts the caret on.
    append("\n")
    append("{end_of_verse}\n")
}

/** The header lines of [newSongTemplate] a pasted song is given where it has none of its own. */
internal val NEW_SONG_TEMPLATE_VALUES = linkedMapOf(
    Field.ARTIST to "",
    Field.KEY to "",
    Field.CAPO to "0",
    Field.TEMPO to "120",
    Field.TIME to "4/4",
)
