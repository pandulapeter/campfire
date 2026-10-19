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

import com.pandulapeter.campfire.chordpro.ChordProTime
import com.pandulapeter.campfire.chordpro.convert.ChordSheet
import com.pandulapeter.campfire.chordpro.convert.ChordSheetConverter
import com.pandulapeter.campfire.chordpro.convert.ChordSheetStructure
import com.pandulapeter.campfire.data.model.importing.ImportLimits
import com.pandulapeter.campfire.data.model.importing.ImportedFile
import com.pandulapeter.campfire.data.model.library.LibraryFiles
import com.pandulapeter.campfire.data.model.library.normalizedToNfc
import com.pandulapeter.campfire.data.model.preferences.MetronomeSettings
import com.pandulapeter.campfire.data.model.song.Song
import kotlinx.serialization.SerializationException
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive

/**
 * The songs of a JSON document written by an app this has no reader of its own for: any object, at any depth, that
 * holds both a title and a chord sheet. A chord sheet is a multi-line text with the structure of one
 * ([ChordSheetStructure]), or one of at least four lines under a key named `lyrics`: a notes app writes every note as a
 * `title` and a `content`, and none of them is a song. What else such an object holds that a header can carry - the
 * artist, the key, the capo, the tempo, the time signature, the copyright, the tags - is written into it.
 *
 * Bounded twice: the document is at most [ImportLimits.MAX_TEXT_FILE_SIZE], and the walk stops after [MAX_NODES] values
 * or [MAX_DEPTH] levels with what it has found, JSON inside a string included. No setlists: what a set is in another
 * app's document is a matter of its format.
 */
internal object JsonSongs {

    /** The songs of [text] as ChordPro files under names unique within the batch, empty where there are none. */
    fun read(text: String): List<ImportedFile> {
        if (text.length > ImportLimits.MAX_TEXT_FILE_SIZE) return emptyList()
        val root = parse(text) ?: return emptyList()
        return Walk().apply { visit(root, depth = 0, nesting = 0) }.files
    }

    private fun parse(text: String) = try {
        Json.parseToJsonElement(text)
    } catch (_: SerializationException) {
        null
    }

    private class Walk {
        val files = mutableListOf<ImportedFile>()
        private val usedNames = mutableSetOf<String>()
        private var nodes = 0

        fun visit(element: JsonElement, depth: Int, nesting: Int) {
            if (++nodes > MAX_NODES || depth > MAX_DEPTH) return
            when (element) {
                is JsonArray -> element.forEach { visit(it, depth + 1, nesting) }
                // An object that is a song is not looked inside: its versions or its history would be read twice.
                is JsonObject -> {
                    val song = song(element)
                    if (song != null) files += song else element.values.forEach { visit(it, depth + 1, nesting) }
                }
                is JsonPrimitive -> {
                    // SongbookPro and others write some nested values as JSON inside a string.
                    val content = element.takeIf { it.isString }?.content?.trimStart() ?: return
                    if (nesting < MAX_NESTING && (content.startsWith("{") || content.startsWith("["))) {
                        parse(content)?.let { visit(it, depth + 1, nesting + 1) }
                    }
                }
            }
        }

        private fun song(song: JsonObject): ImportedFile? {
            val title = song.firstText(TITLE_KEYS)?.takeIf { '\n' !in it && it.length <= MAX_TITLE_LENGTH } ?: return null
            val content = song.content() ?: return null
            val converted = ChordSheetConverter.convert(ChordSheet.ofPlainText(content), String::normalizedToNfc).firstOrNull() ?: return null
            val text = ForeignSongHeader.songText(converted) {
                directive("title", title, "t")
                directive("artist", song.firstText(ARTIST_KEYS))
                directive("key", song.text("key"))
                directive("capo", song.firstInt(listOf("capo"))?.takeIf { it > 0 && it in Song.CAPO_RANGE }?.toString())
                directive("tempo", song.firstInt(listOf("tempo", "bpm"))?.takeIf { it in MetronomeSettings.TEMPO_RANGE }?.toString())
                directive("time", song.firstText(listOf("time", "timeSignature"))?.takeIf { ChordProTime.parse(it) != null })
                directive("copyright", song.text("copyright"))
                tags(listOf("tags", "folders").flatMap { song.textList(it) })
            }
            return ImportedFile(
                name = ForeignSongHeader.uniqueName(base = title, extension = LibraryFiles.SONG_EXTENSION, usedNames = usedNames),
                bytes = text.encodeToByteArray(),
            )
        }

        /** The first multi-line text that is a chord sheet, under the keys a song's text is usually kept under first. */
        private fun JsonObject.content(): String? {
            val candidates = CONTENT_KEYS.mapNotNull { key -> entries.firstOrNull { it.key.equals(key, ignoreCase = true) } } +
                entries.filter { entry -> CONTENT_KEYS.none { it.equals(entry.key, ignoreCase = true) } }
            return candidates.firstNotNullOfOrNull { (key, value) ->
                val text = (value as? JsonPrimitive)?.takeIf { it.isString }?.content?.takeIf { '\n' in it } ?: return@firstNotNullOfOrNull null
                text.takeIf {
                    ChordSheetStructure.hasSongStructure(it) ||
                        key.equals(LYRICS_KEY, ignoreCase = true) && it.lines().count(String::isNotBlank) >= MIN_LYRICS_LINES
                }
            }
        }
    }

    /** A value as text, numbers included, under [key] in any letter case. */
    private fun JsonObject.text(key: String) = entries.firstOrNull { it.key.equals(key, ignoreCase = true) }?.value
        ?.let { it as? JsonPrimitive }?.takeIf { it !is JsonNull }?.content?.trim()?.takeIf { it.isNotEmpty() }

    private fun JsonObject.firstText(keys: List<String>) = keys.firstNotNullOfOrNull { text(it) }

    /** A whole number, written as a number or as the text of one. */
    private fun JsonObject.firstInt(keys: List<String>) = firstText(keys)?.toDoubleOrNull()?.takeIf { it % 1.0 == 0.0 }?.toInt()

    /** Names as an array of them or as one text. */
    private fun JsonObject.textList(key: String): List<String> {
        val value = entries.firstOrNull { it.key.equals(key, ignoreCase = true) }?.value ?: return emptyList()
        return when (value) {
            is JsonArray -> value.mapNotNull { (it as? JsonPrimitive)?.takeIf { member -> member !is JsonNull }?.content?.trim()?.takeIf(String::isNotEmpty) }
            is JsonPrimitive -> listOfNotNull(text(key))
            else -> emptyList()
        }
    }

    private val CONTENT_KEYS = listOf("content", "lyrics", "text", "body", "chordpro", "chords", "sheet")
    private val TITLE_KEYS = listOf("title", "name", "song", "songTitle", "songName")
    private val ARTIST_KEYS = listOf("artist", "author", "composer", "byline")
    private const val LYRICS_KEY = "lyrics"
    private const val MIN_LYRICS_LINES = 4
    private const val MAX_TITLE_LENGTH = 200
    private const val MAX_DEPTH = 32
    private const val MAX_NODES = 200_000

    /** How many levels of JSON inside a string are parsed: SongbookPro writes two. */
    private const val MAX_NESTING = 2
}
