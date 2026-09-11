/*
 * This file is part of Campfire.
 * Copyright (c) Pandula Péter 2017-2026.
 * https://github.com/pandulapeter/campfire
 *
 * This Source Code Form is subject to the terms of the Mozilla Public License, v. 2.0.
 * If a copy of the MPL was not distributed with this file, You can obtain one at
 * https://mozilla.org/MPL/2.0/.
 */
package com.pandulapeter.campfire.data.model.domain

/**
 * What the files of the library are called. This is vocabulary rather than a storage detail: a song's identity is its
 * file name, so the import rules, the storage layer and the file types registered with each operating system all have
 * to agree about what a song file looks like.
 */
object LibraryFiles {

    /** What Campfire writes, and the one it asks each system to open with it by default. */
    const val SONG_EXTENSION = ".cho"

    /**
     * Every extension a ChordPro song is found under, the preferred one first. These are read from the library
     * folder, offered by the pickers and registered as file types.
     *
     * The list is the one the ChordPro project itself names; Campfire recognises all of them but only ever writes
     * [SONG_EXTENSION].
     */
    val SONG_EXTENSIONS = listOf(SONG_EXTENSION, ".chopro", ".chordpro", ".crd", ".chord", ".pro")

    /** A file that holds several songs separated by `{new_song}` is just as often a plain text file. */
    const val TEXT_EXTENSION = ".txt"

    const val SETLIST_EXTENSION = ".setlist.json"

    const val ARCHIVE_EXTENSION = ".zip"

    /**
     * Everything an import will look inside, whether or not Campfire registers itself for it. Plain text, zip and
     * JSON belong to everyone: the app happily reads one that is handed to it, but claiming them system wide would
     * put Campfire in the way of every archive and note on the device.
     */
    val IMPORTABLE_EXTENSIONS = SONG_EXTENSIONS + listOf(TEXT_EXTENSION, ARCHIVE_EXTENSION, ".json")

    /**
     * What stands between the artist and the title in a song's file name: the one piece of a library name that is
     * structure rather than the user's own text, which is why naming a new song and naming an exported one both have
     * to know about it.
     */
    const val ARTIST_TITLE_SEPARATOR = " - "
}
