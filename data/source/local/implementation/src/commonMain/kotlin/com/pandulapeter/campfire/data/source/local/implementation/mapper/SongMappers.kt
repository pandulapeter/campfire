/*
 * This file is part of Campfire.
 * Copyright (c) Pandula Péter 2017-2026.
 * https://github.com/pandulapeter/campfire
 *
 * This Source Code Form is subject to the terms of the Mozilla Public License, v. 2.0.
 * If a copy of the MPL was not distributed with this file, You can obtain one at
 * https://mozilla.org/MPL/2.0/.
 */
package com.pandulapeter.campfire.data.source.local.implementation.mapper

import com.pandulapeter.campfire.chordpro.ChordProDuration
import com.pandulapeter.campfire.chordpro.ChordProParser
import com.pandulapeter.campfire.chordpro.ChordProTempo
import com.pandulapeter.campfire.chordpro.model.ChordProSummary
import com.pandulapeter.campfire.chordpro.model.displayTitle
import com.pandulapeter.campfire.chordpro.scales.SongKeys
import com.pandulapeter.campfire.data.model.song.Song
import com.pandulapeter.campfire.data.model.song.normalizedTags
import com.pandulapeter.campfire.data.model.library.normalizedToNfc
import com.pandulapeter.campfire.data.source.local.implementation.isNamed
import com.pandulapeter.campfire.data.source.local.implementation.knownExtension
import com.pandulapeter.campfire.data.source.local.implementation.songFileName
import com.pandulapeter.campfire.data.source.local.implementation.storage.file.StoredFileInfo
import com.pandulapeter.campfire.data.source.local.implementation.withoutExtension

/**
 * A song file becomes a list entry: the directives win, and whatever they leave out falls back on the file name,
 * which is the one thing every file in the library is guaranteed to have.
 */
internal fun StoredFileInfo.toSong(summary: ChordProSummary, text: String): Song {
    val key = summary.metadata.key?.takeIf { it.isNotBlank() }
    val title = summary.metadata.displayTitle(fallback = name.withoutExtension())
    val artist = summary.metadata.artist?.takeIf { it.isNotBlank() }.orEmpty()
    return Song(
        fileName = name,
        title = title,
        artist = artist,
        key = key,
        transpose = summary.metadata.transpose,
        // Composed, so that a tag written on a Mac and the same tag typed anywhere else are one tag to the filter:
        // the two forms look the same and every comparison above this is by case only. See normalizedToNfc.
        tags = normalizedTags(summary.metadata.tags),
        languages = summary.metadata.languages,
        coverArtUrl = summary.metadata.coverArt,
        hasChords = summary.hasChords,
        // A file that names no title of its own is titled by its file name, so there is nothing better to rename it
        // to: deriving a name from the name would only fold the user's own spelling of it.
        canUpdateFileName = summary.metadata.title?.isNotBlank() == true &&
            !name.isNamed(songFileName(title = title, artist = artist, extension = name.knownExtension())),
        lastModified = lastModified,
        size = size,
        duration = ChordProDuration.parse(summary.metadata.duration),
        tempo = ChordProTempo.parse(summary.metadata.tempo),
        time = summary.metadata.time?.takeIf { it.isNotBlank() },
        capo = summary.metadata.capo,
        // Only a capo chart's key can mean the chords' or the sounding one, and only its chords tell which, so only such a
        // file is read whole: the scan of every other file stays the one walk the summary is.
        capoChartKey = if (key == null || (summary.metadata.capo ?: 0) == 0) null else SongKeys.capoChartKeyOf(ChordProParser.parse(text)),
        hasSheetMusic = summary.hasSheetMusic,
    )
}
