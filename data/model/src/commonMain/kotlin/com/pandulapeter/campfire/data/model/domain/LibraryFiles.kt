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
     * The library backups of other apps that are zip archives under a name of their own: SongbookPro's whole-library
     * `.sbpbackup` and the `.sbp` it shares a set or a song as. Named so that a picker offers them and an archive is
     * looked inside when it holds one; a file under any other unknown name is still unpacked when its bytes are a zip
     * archive (see [isZipArchive]), so this list is about being offered, not about being understood.
     */
    val LIBRARY_BACKUP_EXTENSIONS = listOf(".sbpbackup", ".sbp")

    /** Document inputs converted locally to ChordPro, never registered as system Open with file types. */
    val DOCUMENT_EXTENSIONS = listOf(".pdf", ".docx")

    /** Read only far enough to report the obsolete binary Word format as an unreadable document. */
    const val LEGACY_DOCUMENT_EXTENSION = ".doc"

    /**
     * Everything an import will look inside, whether or not Campfire registers itself for it. Documents, plain text,
     * zip and JSON belong to everyone: the app happily reads one that is handed to it, but claiming them system wide would
     * put Campfire in the way of every archive and note on the device.
     */
    val IMPORTABLE_EXTENSIONS = SONG_EXTENSIONS + DOCUMENT_EXTENSIONS + LIBRARY_BACKUP_EXTENSIONS +
        listOf(TEXT_EXTENSION, ARCHIVE_EXTENSION, ".json", LEGACY_DOCUMENT_EXTENSION)

    /** Whether a file called [name] is unpacked for what it holds by its name alone: a zip, or another app's backup. */
    fun isArchiveFileName(name: String) = (LIBRARY_BACKUP_EXTENSIONS + ARCHIVE_EXTENSION).any { name.endsWith(it, ignoreCase = true) }

    /** Whether an import knows a file called [name] by its extension, and so never has to look at its bytes to decide. */
    fun isImportableFileName(name: String) = IMPORTABLE_EXTENSIONS.any { name.endsWith(it, ignoreCase = true) }

    /**
     * Whether [bytes] start the way a zip archive does: with a local file header, or with the end of the central
     * directory where the archive is empty. Asked only of a file whose name says nothing an import knows, since the
     * other apps' library backups are mostly zip archives under a name of their own; a `.docx` is one too, and is read
     * as the document its name says it is.
     */
    fun isZipArchive(bytes: ByteArray) = bytes.size >= 4 && bytes[0] == 'P'.code.toByte() && bytes[1] == 'K'.code.toByte() &&
        ((bytes[2] == 3.toByte() && bytes[3] == 4.toByte()) || (bytes[2] == 5.toByte() && bytes[3] == 6.toByte()))

    /**
     * Whether [name] is a file some tool wrote for itself rather than one somebody put there: macOS leaves an
     * AppleDouble `._name.cho` next to every file it copies to a volume that cannot hold extended attributes, under
     * the extension of the file it belongs to, and a `.DS_Store` in every folder it has shown. Every system marks
     * these the same way, with a leading dot, and no name the app writes starts with one.
     */
    fun isHiddenFileName(name: String) = name.startsWith(HIDDEN_NAME_PREFIX)

    /**
     * Whether a file called [name] in the songs folder is a song. Campfire writes [SONG_EXTENSION], but a folder the
     * user can also open in a file manager will hold whatever they put in it, and every ChordPro extension names the
     * same thing. The library scan and the listing sync works from both ask this, which is what keeps them agreeing
     * about which files exist: one that only sync saw would be uploaded without ever being shown, and one that only
     * the scan saw would never leave the device.
     */
    fun isSongFileName(name: String) = !isHiddenFileName(name) && SONG_EXTENSIONS.any { name.endsWith(it, ignoreCase = true) }

    /** The same question about the setlists folder. */
    fun isSetlistFileName(name: String) = !isHiddenFileName(name) && name.endsWith(SETLIST_EXTENSION, ignoreCase = true)

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
        // Composed first, and before the case is folded, which does not commute with it for every script: the same
        // name is decomposed on macOS and iOS and composed everywhere else, and the fold below keeps a mark that follows
        // a non-Latin letter, so the two forms would be two names. Composing is also what keeps the rule idempotent
        // across platforms - a name this produced on a Mac is handed back to it by the file system in the other form.
        val text = base.normalizedToNfc().lowercase()
        var index = 0
        while (index < text.length) {
            val character = text[index]
            val low = text.getOrNull(index + 1)
            // A letter outside the Basic Multilingual Plane is two surrogates, neither of which is a letter to Char, so
            // the pair is weighed as the one character it is.
            if (character.isHighSurrogate() && low != null && low.isLowSurrogate()) {
                val isKept = isSupplementaryLetter(SUPPLEMENTARY_START + ((character.code - HIGH_SURROGATE_START) shl 10) + (low.code - LOW_SURROGATE_START))
                if (isKept) folded.append(character).append(low) else folded.append(NAME_SEPARATOR)
                isAfterForeignCharacter = isKept
                index += 2
                continue
            }
            index++
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
                    'æ', 'ǣ', 'ǽ' -> folded.append("ae")
                    'œ' -> folded.append("oe")
                    'þ' -> folded.append("th")
                    'ĳ' -> folded.append("ij")
                    'ǆ', 'ǳ' -> folded.append("dz")
                    'ǉ' -> folded.append("lj")
                    'ǌ' -> folded.append("nj")
                    // Letters of their own rather than letters with marks, so withoutAccent does not know them, spelled
                    // the way a keyboard without them spells them: Azerbaijani ə, the open ɛ and ɔ of Twi, Ewe and
                    // Lingala, and the ŋ of those and of Sámi. Left out, each would become a hole in the name.
                    'ə', 'ɛ' -> folded.append('e')
                    'ɔ' -> folded.append('o')
                    'ŋ' -> folded.append('n')
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

    /**
     * The one key under which two spellings of a library name are the same file: composed to NFC, then every character
     * mapped through `uppercaseChar().lowercaseChar()` - exactly what `String.equals(ignoreCase = true)` compares - so
     * the key and [isSameLibraryName] always agree. The simple case mappings come from Kotlin's own tables, so the key is
     * the same on every platform, unlike a full `lowercase()`, which changes the length of `İ` and applies the Greek
     * final sigma rule wherever the runtime does.
     *
     * A cased letter above U+FFFF (Deseret, Osage, Vithkuqi, Old Hungarian, Warang Citi, Medefaidrin, Adlam) is folded
     * as one code point rather than as two surrogates, which Kotlin cannot case: the JVM's `equalsIgnoreCase` folds
     * them, and the key has to be the same on Kotlin/Native and Kotlin/Wasm, which compare `Char` by `Char`.
     */
    fun identityKey(name: String): String {
        val composed = name.normalizedToNfc()
        return buildString(composed.length) {
            var index = 0
            while (index < composed.length) {
                val high = composed[index]
                val low = composed.getOrNull(index + 1)
                if (high.isHighSurrogate() && low != null && low.isLowSurrogate()) {
                    val codePoint = (((high.code - 0xD800) shl 10) or (low.code - 0xDC00)) + 0x10000
                    val folded = lowercaseSupplementary(codePoint) - 0x10000
                    append(Char(0xD800 + (folded shr 10)))
                    append(Char(0xDC00 + (folded and 0x3FF)))
                    index += 2
                } else {
                    append(high.uppercaseChar().lowercaseChar())
                    index++
                }
            }
        }
    }

    /** Whether [first] and [second] name one library file, see [identityKey]. */
    fun isSameLibraryName(first: String, second: String) = identityKey(first) == identityKey(second)

    private const val FALLBACK_NAME = "untitled"

    /**
     * The small letter of a capital above U+FFFF, or [codePoint] itself: the bicameral scripts of that range as Unicode
     * 15 has them, which is what the JVM the desktop ships compares by.
     */
    private fun lowercaseSupplementary(codePoint: Int) = when (codePoint) {
        in 0x10400..0x10427 -> codePoint + 0x28
        in 0x104B0..0x104D3 -> codePoint + 0x28
        in 0x10570..0x1057A, in 0x1057C..0x1058A, in 0x1058C..0x10592, in 0x10594..0x10595 -> codePoint + 0x27
        in 0x10C80..0x10CB2 -> codePoint + 0x40
        in 0x118A0..0x118BF -> codePoint + 0x20
        in 0x16E40..0x16E5F -> codePoint + 0x20
        in 0x1E900..0x1E921 -> codePoint + 0x22
        else -> codePoint
    }

    private const val HIDDEN_NAME_PREFIX = "."

    /** Both shapes a colliding name is numbered in, anchored to the end of the name. */
    private val COLLISION_SUFFIX = Regex("""(_\d+| \(\d+\))$""")

    /** Straight, curly and the modifier letter, since all three reach a title as the same key on somebody's keyboard. */
    private const val APOSTROPHES = "'’ʼ"

    /** Both signs a title writes "and" with. */
    private const val AND_SIGNS = "&+"

    private fun Char.isLatin() = this < '\u0370' || this in '\u1E00'..'\u1EFF' || this in '\u2C60'..'\u2C7F' ||
        this in '\uA720'..'\uA7FF' || this in '\uAB30'..'\uAB6F'

    private fun Char.isMark() = category == CharCategory.NON_SPACING_MARK || category == CharCategory.COMBINING_SPACING_MARK

    /**
     * Whether a code point above U+FFFF is a letter a name keeps. Common Kotlin knows no category for one, and nearly
     * everything up there is letters and ideographs; what is not is a few blocks of symbols, which become separators the
     * way an emoji always has: musical symbols, the mathematical alphanumerics (styled Latin, which only NFKC would fold
     * back), the emoji, cards and pictographs of U+1F000 on, and planes 14 to 16 (tags, variation selectors, private use).
     */
    private fun isSupplementaryLetter(codePoint: Int) = codePoint !in 0x1D000..0x1D24F && codePoint !in 0x1D400..0x1D7FF &&
        codePoint !in 0x1F000..0x1FFFF && codePoint < 0xE0000

    /** The first [limit] UTF-8 bytes of this string, never cutting a surrogate pair in two. */
    private fun String.takeBytes(limit: Int): String {
        var bytes = 0
        var end = 0
        while (end < length) {
            val isPair = this[end].isHighSurrogate() && getOrNull(end + 1)?.isLowSurrogate() == true
            val added = when {
                isPair -> 4
                this[end].code < 0x80 -> 1
                this[end].code < 0x800 -> 2
                else -> 3
            }
            if (bytes + added > limit) break
            bytes += added
            end += if (isPair) 2 else 1
        }
        return substring(0, end)
    }

    private const val SUPPLEMENTARY_START = 0x10000
    private const val HIGH_SURROGATE_START = 0xD800
    private const val LOW_SURROGATE_START = 0xDC00

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
