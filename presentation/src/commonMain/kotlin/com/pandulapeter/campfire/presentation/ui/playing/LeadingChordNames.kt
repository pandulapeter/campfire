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

import com.pandulapeter.campfire.chordpro.chords.ChordProChords
import com.pandulapeter.campfire.chordpro.chords.ChordProNotation
import com.pandulapeter.campfire.data.model.preferences.UserPreferences
import com.pandulapeter.campfire.data.model.song.Song
import com.pandulapeter.campfire.presentation.mapper.toChordNotation
import com.pandulapeter.campfire.presentation.ui.rendering.SongRenderer

/**
 * The first [count] chords of [song] (whose file reads [text]) moved by [transposition], each once, as the song's page
 * names them in the reader's [spelling]: read the way the page reads its song ([SongRenderer.transposedSong], a
 * numbering counting a song naming no key from [inferredKey] moved along), so that [transposition] being the sounding
 * one or the one a capo's shapes are named at (`SongPlayback.namingTransposition`) gives the names the page shows either
 * way. A chord written two ways (A# and Bb) is one, under the name it is first written with, as the Chords section lists
 * them. Parses the whole song, so never on the main thread.
 */
internal fun SongRenderer.leadingChordNames(
    song: Song,
    text: String,
    transposition: Int,
    spelling: UserPreferences.ChordSpelling,
    inferredKey: String?,
    count: Int,
): List<String> {
    val transposed = transposedSong(text = text, transposition = transposition, spelling = spelling)
    val fallbackKey = inferredKey?.takeIf { song.key.isNullOrBlank() }?.let { movedKey(inferredKey = it, transposition = transposition, spelling = spelling) }
    return ChordProNotation.shownNames(transposed, spelling.notation.toChordNotation(), fallbackKey = fallbackKey).entries
        .mapNotNull { (name, shownName) -> ChordProChords.parse(name)?.let { chord -> chord to shownName } }
        .distinctBy { (chord, _) -> chord }
        .take(count)
        .map { (_, shownName) -> ChordProNotation.withAccidentalSigns(shownName) }
}
