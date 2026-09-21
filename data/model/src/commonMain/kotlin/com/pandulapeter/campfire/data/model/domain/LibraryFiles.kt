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
    const val MAX_NAME_BYTES = 120

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
        val folded = StringBuilder()
        var isAfterForeignCharacter = false
        for (character in base.lowercase()) {
            if (character in APOSTROPHES) continue
            val isForeignCharacter = when {
                character.isMark() -> isAfterForeignCharacter
                character.isLatin() -> false
                else -> character.isLetterOrDigit()
            }
            when {
                isForeignCharacter -> folded.append(character)
                character.isCombiningMark() -> Unit
                character in AND_SIGNS -> folded.append(NAME_SEPARATOR).append("and").append(NAME_SEPARATOR)
                else -> when (val plain = character.withoutAccent()) {
                    'ß' -> folded.append("ss")
                    'æ' -> folded.append("ae")
                    'œ' -> folded.append("oe")
                    else -> folded.append(if (plain in 'a'..'z' || plain in '0'..'9') plain else NAME_SEPARATOR)
                }
            }
            isAfterForeignCharacter = isForeignCharacter
        }
        val words = folded.split(NAME_SEPARATOR).filter { it.isNotEmpty() }.map { word -> ABBREVIATIONS[word] ?: word }
        // Capped by whole words rather than by characters: a word cut short can become a different word on the next
        // pass ("feather" cut to "feat" is filed as "ft"), and the name would then not survive being normalized again.
        // Only a first word that is longer than the cap on its own is cut, and no abbreviation is anywhere near that long.
        val name = StringBuilder()
        var nameBytes = 0
        for (word in words) {
            val addedBytes = (if (name.isEmpty()) 0 else NAME_SEPARATOR.length) + word.encodeToByteArray().size
            if (nameBytes + addedBytes > MAX_NAME_BYTES) break
            if (name.isNotEmpty()) name.append(NAME_SEPARATOR)
            name.append(word)
            nameBytes += addedBytes
        }
        if (name.isEmpty() && words.isNotEmpty()) name.append(words.first().takeBytes(MAX_NAME_BYTES))
        return name.toString().ifEmpty { FALLBACK_NAME }
    }

    /** What a [normalizedName] is made of, and what a collision suffix is joined to it with. */
    const val NAME_SEPARATOR = "_"

    /**
     * [name] — a file name without its extension — without the number a collision added to it, or null where it
     * carries none. Both shapes are recognised: the `_2` of a name the app derived itself, and the ` (2)` sync gives
     * the copy of a file that changed on both sides. `route_66` reads as a numbered `route`, which is harmless to
     * everyone who asks: a family is only ever where to look for a file, never proof that one belongs to it.
     */
    fun withoutCollisionSuffix(name: String): String? = COLLISION_SUFFIX.find(name)
        ?.let { name.substring(startIndex = 0, endIndex = it.range.first) }
        ?.takeIf { it.isNotEmpty() }

    private const val FALLBACK_NAME = "untitled"

    /** Both shapes a colliding name is numbered in, anchored to the end of the name. */
    private val COLLISION_SUFFIX = Regex("""(_\d+| \(\d+\))$""")

    /** Straight, curly and the modifier letter, since all three reach a title as the same key on somebody's keyboard. */
    private const val APOSTROPHES = "'’ʼ"

    /** Both signs a title writes "and" with. */
    private const val AND_SIGNS = "&+"

    private fun Char.isLatin() = this < '\u0370' || this in '\u1E00'..'\u1EFF' || this in '\u2C60'..'\u2C7F' ||
        this in '\uA720'..'\uA7FF' || this in '\uAB30'..'\uAB6F'

    private fun Char.isMark() = category == CharCategory.NON_SPACING_MARK || category == CharCategory.COMBINING_SPACING_MARK

    private fun String.takeBytes(limit: Int): String {
        var bytes = 0
        return takeWhile { character ->
            bytes += when {
                character.code < 0x80 -> 1
                character.code < 0x800 -> 2
                else -> 3
            }
            bytes <= limit
        }
    }

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
