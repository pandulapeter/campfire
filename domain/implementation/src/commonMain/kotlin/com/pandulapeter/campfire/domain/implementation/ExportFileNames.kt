/*
 * This file is part of Campfire.
 * Copyright (c) Pandula Péter 2017-2026.
 * https://github.com/pandulapeter/campfire
 *
 * This Source Code Form is subject to the terms of the Mozilla Public License, v. 2.0.
 * If a copy of the MPL was not distributed with this file, You can obtain one at
 * https://mozilla.org/MPL/2.0/.
 */
package com.pandulapeter.campfire.domain.implementation

import com.pandulapeter.campfire.data.model.domain.LibraryFiles
import com.pandulapeter.campfire.data.model.domain.SongContent
import com.pandulapeter.campfire.data.repository.api.SongRepository

/**
 * The name a file leaves the app under: [base] run through [LibraryFiles.normalizedName], plus [extension] as it is
 * given.
 *
 * A song inside the library keeps the user's own text, capitals, spaces, accents and all, because its file name is
 * what titles it wherever the file itself does not say. A file handed to another system is read by whatever is there
 * instead, so on the way out the name is reduced to the characters every one of them agrees about.
 *
 * Only the name of the file being handed over is normalized. The entries inside an exported archive keep the names
 * the library gave them, since a setlist points at its songs by file name.
 */
internal fun exportFileName(base: String, extension: String) = LibraryFiles.normalizedName(base) + extension

/**
 * The export name of a song: the one its own header gives it, by the rule the import names an incoming song by
 * ([SongRepository.importFileName]), so that a song leaves under the name it would come back under - whatever its
 * library file happens to be called, numbered sibling or name from before its title changed. The file name stands in
 * as the title only where the song declares none, as it does for the import. The extension is the stored one, since
 * the library reads the whole ChordPro family and handing a file out under another one would be a claim about how it
 * is written.
 */
internal fun SongRepository.songExportFileName(content: SongContent): String {
    val extension = LibraryFiles.SONG_EXTENSIONS.firstOrNull { content.fileName.endsWith(it, ignoreCase = true) } ?: LibraryFiles.SONG_EXTENSION
    return importFileName(fallbackTitle = content.fileName.substringBeforeLast('.'), text = content.text)
        .removeSuffix(LibraryFiles.SONG_EXTENSION) + extension
}
