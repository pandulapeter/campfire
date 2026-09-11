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
 * The export name of a song file, which keeps whichever extension of the ChordPro family it is stored under.
 *
 * A song is named after its artist and its title with a separator between them, and that one piece of structure is
 * worth keeping: each half is normalized on its own and they are joined by the dash again, so the name still says
 * where the artist ends instead of reading as one run of underscored words.
 *
 * Which separator it is split on decides whether the name survives being exported twice. A library name written by
 * hand still has the spaced [LibraryFiles.ARTIST_TITLE_SEPARATOR] in it; one the app named itself has already been
 * through here and carries the bare [LibraryFiles.NORMALIZED_ARTIST_TITLE_SEPARATOR], which normalizing the whole
 * name in one piece would fold into an underscore like any other character that is not a letter.
 */
internal fun String.toSongExportFileName(): String {
    val extension = LibraryFiles.SONG_EXTENSIONS.firstOrNull { endsWith(it, ignoreCase = true) }.orEmpty()
    val base = dropLast(extension.length)
    val separator = if (base.contains(LibraryFiles.ARTIST_TITLE_SEPARATOR)) {
        LibraryFiles.ARTIST_TITLE_SEPARATOR
    } else {
        LibraryFiles.NORMALIZED_ARTIST_TITLE_SEPARATOR
    }
    return base.split(separator)
        .joinToString(LibraryFiles.NORMALIZED_ARTIST_TITLE_SEPARATOR) { exportFileName(base = it, extension = "") } + extension
}
