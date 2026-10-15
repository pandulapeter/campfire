/*
 * This file is part of Campfire.
 * Copyright (c) Pandula Péter 2017-2026.
 * https://github.com/pandulapeter/campfire
 *
 * This Source Code Form is subject to the terms of the Mozilla Public License, v. 2.0.
 * If a copy of the MPL was not distributed with this file, You can obtain one at
 * https://mozilla.org/MPL/2.0/.
 */
package com.pandulapeter.campfire.presentation.ui.screens.songEditor

import com.pandulapeter.campfire.chordpro.model.ChordProSummary
import com.pandulapeter.campfire.chordpro.model.displayTitle
import com.pandulapeter.campfire.data.model.library.LibraryFiles
import com.pandulapeter.campfire.data.model.song.Song
import com.pandulapeter.campfire.data.model.song.normalizedTags

/**
 * The song as the text being typed describes it, which is what the metadata dialogs of the preview's card and the cover
 * art sheet of the editor's menu are opened on: the library's entry describes the file, which the text may already
 * have moved away from.
 *
 * @param untitledTitle The title of a text that declares none.
 */
internal fun ChordProSummary.toEditorSong(
    fileName: String,
    untitledTitle: String = fileName.removeSuffix(LibraryFiles.SONG_EXTENSION),
) = Song(
    fileName = fileName,
    title = metadata.displayTitle(untitledTitle),
    artist = metadata.artist?.takeIf { it.isNotBlank() }.orEmpty(),
    key = metadata.key?.takeIf { it.isNotBlank() },
    transpose = metadata.transpose,
    tags = normalizedTags(metadata.tags),
    languages = metadata.languages,
    coverArtUrl = metadata.coverArt,
    hasChords = hasChords,
    canUpdateFileName = false,
    lastModified = 0,
    size = 0,
)

/**
 * The start of the line after the first `{start_of_…}`, which in a freshly created song is the blank line its
 * template leaves for the first verse.
 */
internal fun String.caretInsideFirstSection(): Int {
    val sectionStart = indexOf(SECTION_START)
    if (sectionStart == -1) return length
    val lineBreak = indexOf('\n', sectionStart)
    return if (lineBreak == -1) length else lineBreak + 1
}

private const val SECTION_START = "{start_of_"
