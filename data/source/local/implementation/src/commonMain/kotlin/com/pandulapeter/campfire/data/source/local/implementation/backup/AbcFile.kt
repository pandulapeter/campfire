/*
 * This file is part of Campfire.
 * Copyright (c) Pandula Péter 2017-2026.
 * https://github.com/pandulapeter/campfire
 *
 * This Source Code Form is subject to the terms of the Mozilla Public License, v. 2.0.
 * If a copy of the MPL was not distributed with this file, You can obtain one at
 * https://mozilla.org/MPL/2.0/.
 */
package com.pandulapeter.campfire.data.source.local.implementation.backup

import com.pandulapeter.campfire.chordpro.abc.AbcToChordPro
import com.pandulapeter.campfire.chordpro.abc.AbcTunes
import com.pandulapeter.campfire.data.model.importing.ImportedFile
import com.pandulapeter.campfire.data.model.library.LibraryFiles

/**
 * Reads an ABC file, an `.abc` or a text that reads as one (`AbcText`), into one ChordPro song per tune, each with its
 * chord grid and the tune itself kept in a `{start_of_abc}` block (see `:chordpro`'s `abc` package).
 */
internal object AbcFile {

    /**
     * The tunes of [text] as `.cho` files under names unique within the batch, the tune's title or, for a tune with no
     * `T:`, [fallbackTitle] (the file's name), which then titles the song too. Empty where the text holds no tune.
     */
    fun read(text: String, fallbackTitle: String): List<ImportedFile> {
        val usedNames = BatchNames()
        return AbcTunes.split(text).map { tune ->
            val song = AbcToChordPro.convert(tune, fallbackTitle = fallbackTitle)
            ImportedFile(
                name = ForeignSongHeader.uniqueName(base = song.title ?: fallbackTitle, extension = LibraryFiles.SONG_EXTENSION, usedNames = usedNames),
                bytes = song.text.encodeToByteArray(),
            )
        }
    }
}
