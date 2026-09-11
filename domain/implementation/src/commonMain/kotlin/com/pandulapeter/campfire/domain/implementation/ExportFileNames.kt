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
 * The name a file leaves the app under: [base] reduced to lowercase unaccented words joined with underscores, plus
 * [extension] as it is given.
 *
 * Inside the library a name is the user's own text, capitals, spaces, accents and all, because it is the identity of
 * the song and has to read like its title. A file handed to another system is read by whatever is there instead — a
 * shell that needs every space escaped, a service that lowercases a name behind one's back, a file system that cannot
 * store an "ő" — so on the way out the name is reduced to the characters every one of them agrees about.
 *
 * Only the name of the file being handed over is normalized. The entries inside an exported archive keep the names
 * the library gave them, since a setlist points at its songs by file name.
 */
internal fun exportFileName(base: String, extension: String): String {
    // A character no accent table knows (a Cyrillic or CJK title) becomes a separator like punctuation does, which is
    // the one case where nothing is left of the name and the fallback has to stand in for it.
    val words = base.lowercase().map { character ->
        when (val folded = character.withoutAccent()) {
            // The three letters that are two letters once they are spelled out, which is the one thing the accent
            // table cannot say: everything in it folds to a single character.
            'ß' -> "ss"
            'æ' -> "ae"
            'œ' -> "oe"
            else -> if (folded in 'a'..'z' || folded in '0'..'9') folded.toString() else SEPARATOR
        }
    }.joinToString(separator = "").split(SEPARATOR).filter { it.isNotEmpty() }
    return words.joinToString(SEPARATOR).ifEmpty { FALLBACK_NAME } + extension
}

/**
 * The export name of a song file, which keeps whichever extension of the ChordPro family it is stored under.
 *
 * A song is named after its artist and its title with [LibraryFiles.ARTIST_TITLE_SEPARATOR] between them, and that
 * one piece of structure is worth keeping: each half is normalized on its own and they are joined by the dash again,
 * so the name still says where the artist ends instead of reading as one run of underscored words.
 */
internal fun String.toSongExportFileName(): String {
    val extension = LibraryFiles.SONG_EXTENSIONS.firstOrNull { endsWith(it, ignoreCase = true) }.orEmpty()
    // The separator without its spaces, since an exported name has none of those to keep it apart from the words.
    return dropLast(extension.length).split(LibraryFiles.ARTIST_TITLE_SEPARATOR)
        .joinToString(LibraryFiles.ARTIST_TITLE_SEPARATOR.trim()) { exportFileName(base = it, extension = "") } + extension
}

private const val SEPARATOR = "_"
private const val FALLBACK_NAME = "untitled"
