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

    /**
     * The same piece of structure inside a normalized name, which has no spaces around it to hold it apart from the
     * words: `tukorfurogep-arviz`. Each half is normalized on its own and the dash is put back between them, so the
     * name still says where the artist ends instead of reading as one run of underscored words.
     */
    const val NORMALIZED_ARTIST_TITLE_SEPARATOR = "-"

    /**
     * The longest a name may be before its extension. Long enough for any title somebody actually writes, short
     * enough to survive the path limits of every file system the library can end up on.
     */
    const val MAX_NAME_LENGTH = 120

    /**
     * [base] reduced to what every file system, shell and cloud service agrees about: lowercase unaccented words
     * joined with underscores, capped at [MAX_NAME_LENGTH] and never empty. The extension is the caller's to add.
     *
     * Every name the app writes is built this way, which is why the rule is vocabulary rather than any one caller's
     * own business. It is the name a file leaves the app under, where whatever is on the other side reads it instead
     * of Campfire — a shell that needs every space escaped, a service that lowercases a name behind one's back, a
     * file system that cannot store an "ő" — and it is equally the name a song is stored under, so that a library
     * assembled out of imports, hand written files and songs written in the app reads as one set rather than as the
     * spelling habits of everywhere its files have been.
     *
     * Three of the rules are about the same thing: the same song, written down by two people, has to arrive at one
     * name. A title is as readily typed "&" as "and" and a credit "feat." as "ft", and neither difference is one the
     * library should file under two names.
     */
    fun normalizedName(base: String): String {
        val words = base.lowercase()
            // The one piece of punctuation that binds rather than separates, and so is dropped instead of folded to
            // an underscore: "don't" is one word, in whichever of its spellings a keyboard produced it.
            .filterNot { it in APOSTROPHES }
            // Turned into the word it is read as rather than into a separator, so that the two ways of writing the
            // same title meet here instead of naming two files.
            .replace(AND_SIGN, " and ")
            // A character no accent table knows (a Cyrillic or CJK title) becomes a separator like punctuation does,
            // which is the one case where nothing is left of the name and the fallback has to stand in for it.
            .map { character ->
                when (val folded = character.withoutAccent()) {
                    // The three letters that are two letters once they are spelled out, which is the one thing the
                    // accent table cannot say: everything in it folds to a single character.
                    'ß' -> "ss"
                    'æ' -> "ae"
                    'œ' -> "oe"
                    else -> if (folded in 'a'..'z' || folded in '0'..'9') folded.toString() else NAME_SEPARATOR
                }
            }.joinToString(separator = "").split(NAME_SEPARATOR).filter { it.isNotEmpty() }
            .map { word -> ABBREVIATIONS[word] ?: word }
        // Trimmed after the cap rather than before it, since cutting mid-word leaves a trailing separator behind.
        return words.joinToString(NAME_SEPARATOR).take(MAX_NAME_LENGTH).trim(NAME_SEPARATOR_CHARACTER).ifEmpty { FALLBACK_NAME }
    }

    /** What a [normalizedName] is made of, and what a collision suffix is joined to it with. */
    const val NAME_SEPARATOR = "_"

    private const val NAME_SEPARATOR_CHARACTER = '_'
    private const val FALLBACK_NAME = "untitled"

    /** Straight, curly and the modifier letter, since all three reach a title as the same key on somebody's keyboard. */
    private const val APOSTROPHES = "'’ʼ"

    /** Both signs a title writes "and" with. */
    private val AND_SIGN = Regex("[&+]")

    /**
     * Spellings that are one word once the name is filed. Applied per word and after the folding, so what is matched
     * is the bare `feat` a "feat." has already become, and a name that has been through here once is left alone by a
     * second pass — which it has to be, since an exported name is normalized again on its way back in.
     */
    private val ABBREVIATIONS = mapOf(
        "feat" to "ft",
        "featuring" to "ft",
    )
}
