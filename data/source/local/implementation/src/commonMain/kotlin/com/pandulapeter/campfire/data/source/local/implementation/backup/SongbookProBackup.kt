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

import com.pandulapeter.campfire.chordpro.ChordProDuration
import com.pandulapeter.campfire.chordpro.ChordProLinks
import com.pandulapeter.campfire.chordpro.ChordProTime
import com.pandulapeter.campfire.data.model.domain.ImportedFile
import com.pandulapeter.campfire.data.model.domain.LibraryFiles
import com.pandulapeter.campfire.data.model.domain.MetronomeSettings
import com.pandulapeter.campfire.data.model.domain.Song
import com.pandulapeter.campfire.data.model.domain.decodeLibraryText
import com.pandulapeter.campfire.data.source.local.implementation.model.SetlistDocument
import com.pandulapeter.campfire.data.source.local.implementation.model.SetlistDocumentFormat
import com.pandulapeter.campfire.data.source.local.implementation.model.SetlistSongDocument
import kotlinx.datetime.LocalDate
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlin.time.Duration.Companion.seconds

/**
 * Reads a SongbookPro library - its `.sbpbackup` backup or the `.sbp` it shares a set as - into the files an import of
 * Campfire's own would have brought: one ChordPro song per song and one setlist per set. Both are zip archives that hold
 * the whole library as one JSON document, `dataFile.txt`, behind a line naming its version; everything else in them
 * (`settings.hive`, `dataFile.hash`) is SongbookPro's own bookkeeping.
 *
 * Nothing about the format is published, so every value is read the way it has been seen written - numbers and
 * booleans as often as strings of them - and one that cannot be read is left out rather than failing the song. A song's
 * own text is already ChordPro; what SongbookPro keeps beside it (the title, the artist, the key, the capo, the tempo,
 * the time signature, the duration, the copyright, a link, the folders it is filed in) is written into its header,
 * except where the text declares the same thing itself. What has no place in a file - a transposition, which Campfire
 * keeps per device, the zoom, the drawings - is not carried over.
 */
internal object SongbookProBackup {

    /** The entry that holds the library, which may be much larger than any one song file. */
    const val DATA_FILE_NAME = "dataFile.txt"

    /** What SongbookPro keeps beside [DATA_FILE_NAME] for itself, which is what marks an archive as its own. */
    private val BOOKKEEPING_FILE_NAMES = setOf("dataFile.hash", "settings.hive")

    /** What an archive's entries turned out to be. */
    sealed interface Result {

        /** Not SongbookPro's: the entries are imported as the files they are. */
        data object NotABackup : Result

        /** SongbookPro's, translated into the files of a Campfire library. */
        data class Library(val files: List<ImportedFile>) : Result

        /**
         * SongbookPro's by its shape, but its document could not be read - a format version this does not know, or a
         * truncated backup - so it is reported as one file that was left out rather than imported as text.
         */
        data object Unreadable : Result
    }

    /**
     * What [entries] are. Entries are the archive's, by their names without paths, and include those that were left
     * unread (empty), since SongbookPro's bookkeeping is never read and is still what tells its backup apart from a
     * user's own archive that happens to hold a `dataFile.txt`: without it, or the version line SongbookPro starts its
     * document with, a document that cannot be read is an ordinary file.
     */
    fun read(entries: List<ImportedFile>): Result {
        val data = entries.singleOrNull { it.name == DATA_FILE_NAME } ?: return Result.NotABackup
        val text = data.bytes.decodeLibraryText()
        val library = parse(text)
        val songs = library?.array("songs")
        if (library == null || songs == null) {
            val isSongbookPros = entries.any { it.name in BOOKKEEPING_FILE_NAMES } || text.startsWithVersionLine()
            return if (isSongbookPros) Result.Unreadable else Result.NotABackup
        }
        return Result.Library(files(library = library, songs = songs))
    }

    private fun files(library: JsonObject, songs: JsonArray): List<ImportedFile> {
        val folderNames = library.array("folders").orEmpty().mapNotNull { element ->
            val folder = element as? JsonObject ?: return@mapNotNull null
            if (folder.isDeleted) return@mapNotNull null
            val id = folder.int("Id") ?: return@mapNotNull null
            val name = folder.text("Name") ?: folder.text("name") ?: return@mapNotNull null
            id to name
        }.toMap()
        val songFiles = mutableMapOf<Int, SongFile>()
        val usedNames = mutableSetOf<String>()
        val files = songs.mapNotNull { element ->
            val song = element as? JsonObject ?: return@mapNotNull null
            val content = song.text("content")
            // A song that is a PDF or a picture in SongbookPro keeps its file outside the backup, and has no text here.
            if (song.isDeleted || content == null) return@mapNotNull null
            val title = song.text("name")
            val file = ImportedFile(
                name = uniqueName(base = title ?: "untitled", extension = LibraryFiles.SONG_EXTENSION, usedNames = usedNames),
                bytes = songText(song = song, title = title, content = content, folderNames = folderNames).encodeToByteArray(),
            )
            song.int("Id")?.let { id -> songFiles[id] = SongFile(name = file.name, capo = song.capo ?: 0) }
            file
        }
        val setlists = library.array("sets").orEmpty().mapIndexedNotNull { index, element ->
            val set = element as? JsonObject ?: return@mapIndexedNotNull null
            val details = set.objectOf("details") ?: return@mapIndexedNotNull null
            if (details.isDeleted) return@mapIndexedNotNull null
            val date = details.text("date")?.take(ISO_DATE_LENGTH)?.takeIf { runCatching { LocalDate.parse(it) }.isSuccess }
            // SongbookPro names a set by its date as often as not, and a setlist needs a title for its file to be named by. One
            // with neither is numbered rather than named: the title is written into the file, and the data layer cannot say a
            // word in the language of whoever reads it.
            val title = details.text("name") ?: date ?: "#${details.int("Id") ?: (index + 1)}"
            val entries = set.array("contents").orEmpty()
                .mapNotNull { it as? JsonObject }
                .filter { !it.isDeleted && (it.int("ItemType") ?: SONG_ITEM_TYPE) == SONG_ITEM_TYPE }
                .sortedBy { it.int("Order") ?: Int.MAX_VALUE }
                .mapNotNull { entry ->
                    val song = entry.int("SongId")?.let(songFiles::get) ?: return@mapNotNull null
                    // Only a capo the set plays the song with differently is an override; the song's own is in its file.
                    SetlistSongDocument(file = song.name, capo = entry.int("Capo")?.takeIf { it in Song.CAPO_RANGE && it != song.capo })
                }
            ImportedFile(
                name = uniqueName(base = title, extension = LibraryFiles.SETLIST_EXTENSION, usedNames = usedNames),
                bytes = SetlistDocumentFormat.encode(SetlistDocument(title = title, date = date, songs = entries)).encodeToByteArray(),
            )
        }
        return files + setlists
    }

    /** Whether the text's first non-blank line is a bare version number (`1.0`, `2.0`) with more text after it. */
    private fun String.startsWithVersionLine(): Boolean {
        val text = trimStart()
        return VERSION_LINE.matches(text.substringBefore('\n').trim()) && text.substringAfter('\n', missingDelimiterValue = "").isNotBlank()
    }

    /** The document after its version line (`1.0`), or null where the text is not one. */
    private fun parse(text: String): JsonObject? {
        val json = if (text.trimStart().startsWith("{")) text else text.substringAfter('\n', missingDelimiterValue = "")
        return parseObject(json)
    }

    private fun parseObject(text: String) = runCatching { Json.parseToJsonElement(text) as? JsonObject }.getOrNull()

    private fun songText(song: JsonObject, title: String?, content: String, folderNames: Map<Int, String>): String {
        val declared = DIRECTIVE.findAll(content).mapTo(mutableSetOf()) { it.groupValues[1].lowercase() }
        val header = buildList {
            fun directive(name: String, value: String?, vararg aliases: String) {
                val written = value?.let(::headerValue) ?: return
                if ((aliases.toList() + name).none { it in declared }) add("{$name: $written}")
            }
            directive("title", title, "t")
            directive("subtitle", song.text("subTitle"), "st", "su")
            directive("artist", song.text("author"))
            directive("key", song.int("key")?.let(KEYS::getOrNull))
            directive("capo", song.capo?.takeIf { it > 0 }?.toString())
            directive("tempo", song.int("TempoInt")?.takeIf { it in MetronomeSettings.TEMPO_RANGE }?.toString())
            directive("time", song.text("timeSig")?.takeIf { ChordProTime.parse(it) != null })
            directive("duration", song.int("Duration")?.takeIf { it > 0 }?.let { ChordProDuration.format(it.seconds) })
            directive("copyright", song.text("Copyright"))
            song.text("Url")?.let(::linkUrl)?.let { add("{meta: link $it}") }
            // Folders are how a SongbookPro library is sorted, which is what tags are for here.
            val folders = song.textList("_folders").mapNotNull { folder -> folder.toIntOrNull()?.let(folderNames::get) ?: folder.takeIf { it.toIntOrNull() == null } }
            (folders + song.textList("_tags"))
                .mapNotNull(::headerValue)
                .distinct()
                .forEach { add("{tag: $it}") }
        }
        return (header + content.trimStart('\n', '\r')).joinToString(separator = "\n").trimEnd() + "\n"
    }

    /**
     * A value as one directive can hold it: on one line, its line breaks a " / " and every other run of whitespace one
     * space, and no braces, which would end the directive early. SongbookPro's fields are free text, and a multi-line
     * copyright reads as two credits, which is why the line breaks are kept as a separator rather than a space.
     */
    private fun headerValue(text: String) = text
        .split(LINE_BREAK).map { it.trim() }.filter { it.isNotEmpty() }.joinToString(" / ")
        .replace(WHITESPACE, " ")
        .replace('{', '(').replace('}', ')')
        .takeIf { it.isNotBlank() }

    /**
     * [text] as an address a `{meta: link}` directive keeps whole, or null where the parser would not read one: a space
     * or a brace has no place in a directive's address, so they are percent-encoded - which still opens the page
     * SongbookPro did, since a browser encodes them the same way - and a line break is dropped.
     */
    private fun linkUrl(text: String) = ChordProLinks.usableUrl(
        text.replace(LINE_BREAK, "").replace(WHITESPACE, "%20").replace("{", "%7B").replace("}", "%7D"),
    )

    /**
     * A name for the file in the batch, which only has to be unique within it: a setlist entry points at its song by it,
     * and the song is named by its own header once it is imported. It is what the import report shows for the file, so
     * it is the title as it was written, without what a file name cannot hold and without the leading dot that would
     * make it a hidden file.
     */
    private fun uniqueName(base: String, extension: String, usedNames: MutableSet<String>): String {
        val cleaned = base.replace(UNSAFE_CHARACTERS, " ").trim().trimStart('.').ifBlank { "untitled" }
        var name = cleaned + extension
        var number = 2
        while (!usedNames.add(name.lowercase())) name = "$cleaned ${number++}$extension"
        return name
    }

    private val JsonObject.capo get() = int("Capo")?.takeIf { it in Song.CAPO_RANGE }

    private val JsonObject.isDeleted get() = this["Deleted"].let { (it as? JsonPrimitive)?.content?.lowercase() in setOf("true", "1") }

    /** A value as text, the `None` Python writes for nothing included: SongbookPro has written both. */
    private fun JsonObject.text(key: String) = (this[key] as? JsonPrimitive)?.takeIf { it !is JsonNull }
        ?.content?.trim()?.takeIf { it.isNotEmpty() && it != "None" }

    private fun JsonObject.int(key: String) = text(key)?.toDoubleOrNull()?.takeIf { it % 1.0 == 0.0 }?.toInt()

    private fun JsonObject.array(key: String) = this[key] as? JsonArray

    /** An object, or the text of one: SongbookPro writes some of its nested values as JSON inside a string. */
    private fun JsonObject.objectOf(key: String) = (this[key] as? JsonObject) ?: text(key)?.let(::parseObject)

    /** A list of names or ids, as an array or as the text of one, its members plain values or objects with a name. */
    private fun JsonObject.textList(key: String): List<String> {
        val element: JsonElement = this[key] as? JsonArray
            ?: text(key)?.let { runCatching { Json.parseToJsonElement(it) }.getOrNull() }
            ?: return emptyList()
        return (element as? JsonArray).orEmpty().mapNotNull { member ->
            when (member) {
                is JsonObject -> member.text("Name") ?: member.text("name") ?: member.int("Id")?.toString()
                is JsonNull -> null
                is JsonPrimitive -> member.content.trim().takeIf { it.isNotEmpty() }
                else -> null
            }
        }
    }

    private class SongFile(val name: String, val capo: Int)

    /** The keys in the order SongbookPro numbers them, from A, spelled the way the charts of those keys usually are. */
    private val KEYS = listOf("A", "Bb", "B", "C", "Db", "D", "Eb", "E", "F", "F#", "G", "Ab")

    /** The name of every directive the text declares, `{title: …}` and `{t:…}` alike. */
    private val DIRECTIVE = Regex("""\{\s*([A-Za-z_]+)\s*[:}\s]""")

    private val VERSION_LINE = Regex("""\d+(\.\d+)+""")

    private val LINE_BREAK = Regex("[\\r\\n\\u2028\\u2029]+")

    private val WHITESPACE = Regex("""\s+""")

    private val UNSAFE_CHARACTERS = Regex("""[/\\:*?"<>|\u0000-\u001f]+""")

    private const val SONG_ITEM_TYPE = 1
    private const val ISO_DATE_LENGTH = 10
}
