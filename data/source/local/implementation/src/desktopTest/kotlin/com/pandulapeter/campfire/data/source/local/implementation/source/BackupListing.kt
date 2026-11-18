/*
 * This file is part of Campfire.
 * Copyright (c) Pandula Péter 2017-2026.
 * https://github.com/pandulapeter/campfire
 *
 * This Source Code Form is subject to the terms of the Mozilla Public License, v. 2.0.
 * If a copy of the MPL was not distributed with this file, You can obtain one at
 * https://mozilla.org/MPL/2.0/.
 */
package com.pandulapeter.campfire.data.source.local.implementation.source

import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import java.io.ByteArrayInputStream
import java.util.zip.ZipInputStream

/**
 * The songs and sets another app's backup says it holds, read straight from its bytes with nothing of the importer's, so
 * that what the import plans can be held to it: SongbookPro's `dataFile.txt`, OpenSong's song and set documents (alone,
 * or in OpenSongApp's `.osb`, `.osbs`, and `.ossb` archives), and OpenSongApp's JustChords export. Null for a file that
 * lists nothing explicitly (a chord sheet, a text of titles).
 */
internal class BackupListing(val songs: List<ListedSong>, val sets: List<ListedSet>) {

    /** A song by its title, and the name of the entry or file it is in where it has one of its own. */
    class ListedSong(val title: String, val entryName: String?)

    /**
     * A set by its title where it has one, and how many different songs it names where that is known. One the import
     * translates must be planned ([mayBeReported] false); one of a format it does not take may be reported instead.
     */
    class ListedSet(val title: String?, val entryName: String?, val songCount: Int?, val mayBeReported: Boolean = true)

    companion object {

        fun of(name: String, bytes: ByteArray): BackupListing? {
            val entries = zipEntries(bytes)
            if (entries != null) {
                entries.entries.firstOrNull { it.key.substringAfterLast('/').equals("dataFile.txt", ignoreCase = true) }?.let { return songbookPro(it.value.decodeToString()) }
                val listings = entries.mapNotNull { (entryName, entry) -> openSong(entryName.substringAfterLast('/'), entry.decodeToString()) }
                return BackupListing(songs = listings.flatMap { it.songs }, sets = listings.flatMap { it.sets }).takeIf { it.songs.isNotEmpty() || it.sets.isNotEmpty() }
            }
            val text = bytes.decodeToString()
            return when {
                name.endsWith(".justchords", ignoreCase = true) -> justChords(text)
                else -> openSong(name, text)
            }
        }

        /** Every entry that holds anything, by its path, or null where [bytes] are no zip archive. */
        private fun zipEntries(bytes: ByteArray): Map<String, ByteArray>? {
            if (bytes.size < 4 || bytes[0] != 'P'.code.toByte() || bytes[1] != 'K'.code.toByte()) return null
            val entries = linkedMapOf<String, ByteArray>()
            ZipInputStream(ByteArrayInputStream(bytes)).use { zip ->
                generateSequence { zip.nextEntry }.filterNot { it.isDirectory }.forEach { entry ->
                    zip.readBytes().takeIf { it.isNotEmpty() }?.let { entries[entry.name] = it }
                }
            }
            return entries
        }

        private fun songbookPro(dataFile: String): BackupListing {
            val library = Json.parseToJsonElement(dataFile.trimStart().substringAfter('\n')) as JsonObject
            val songs = (library["songs"] as JsonArray).map { it as JsonObject }.filterNot { it.isDeleted }
            val songIds = songs.mapNotNull { it.text("Id") }.toSet()
            val sets = (library["sets"] as? JsonArray).orEmpty().map { it as JsonObject }.mapNotNull { set ->
                val details = (set["details"] as? JsonObject) ?: set.text("details")?.let { Json.parseToJsonElement(it) as JsonObject } ?: return@mapNotNull null
                if (details.isDeleted) return@mapNotNull null
                val items = (set["contents"] as? JsonArray).orEmpty().map { it as JsonObject }
                    .filter { !it.isDeleted && (it.text("ItemType") ?: "1") == "1" }
                    .mapNotNull { it.text("SongId")?.takeIf(songIds::contains) }
                ListedSet(title = details.text("name"), entryName = null, songCount = items.distinct().size, mayBeReported = false)
            }
            return BackupListing(songs = songs.map { ListedSong(title = it.text("name") ?: "untitled", entryName = null) }, sets = sets)
        }

        /** One OpenSong document: a song, or a set naming its songs by title and folder. */
        private fun openSong(entryName: String, text: String): BackupListing? {
            val body = text.trimStart().removePrefix("﻿").replace(XML_DECLARATION, "").trimStart()
            return when {
                body.startsWith("<song") -> BackupListing(
                    songs = listOf(ListedSong(title = SONG_TITLE.find(body)?.groupValues?.get(1)?.unescaped()?.trim()?.ifEmpty { null } ?: entryName.substringBeforeLast('.'), entryName = entryName)),
                    sets = emptyList(),
                )
                body.startsWith("<set") -> BackupListing(
                    songs = emptyList(),
                    sets = listOf(
                        ListedSet(
                            title = SET_NAME.find(body)?.groupValues?.get(1)?.unescaped(),
                            entryName = entryName,
                            // A song is its folder and name: two of one name in two folders are two songs.
                            songCount = SLIDE_GROUP.findAll(body).map { it.groupValues[1] }
                                .filter { attribute("type", it) == "song" }
                                .map { attribute("path", it).orEmpty() to attribute("name", it) }
                                .distinct().count(),
                            mayBeReported = false,
                        ),
                    ),
                )
                else -> null
            }
        }

        private fun justChords(text: String): BackupListing {
            val export = Json.parseToJsonElement(text) as JsonObject
            val songs = (export["songs"] as? JsonArray).orEmpty().map { it as JsonObject }.map { ListedSong(title = it.text("title") ?: "untitled", entryName = null) }
            val sets = (export["playlists"] as? JsonArray).orEmpty().map { it as JsonObject }.map { ListedSet(title = it.text("title") ?: it.text("name"), entryName = null, songCount = null) }
            return BackupListing(songs = songs, sets = sets)
        }

        private val JsonObject.isDeleted get() = text("Deleted")?.lowercase() in setOf("true", "1")

        private fun JsonObject.text(key: String) = (this[key] as? JsonPrimitive)?.takeIf { it !is JsonNull }?.content?.trim()?.removeSuffix(".0")?.ifEmpty { null }

        private fun String.unescaped() = replace("&lt;", "<").replace("&gt;", ">").replace("&quot;", "\"").replace("&apos;", "'").replace("&amp;", "&")

        private val XML_DECLARATION = Regex("""^<\?xml[^>]*\?>""")
        private val SONG_TITLE = Regex("""<title>([^<]*)</title>""")
        private val SET_NAME = Regex("""<set[^>]*\sname="([^"]*)"""")
        private val SLIDE_GROUP = Regex("""<slide_group\b([^>]*)>""")

        private fun attribute(name: String, attributes: String) = Regex("""\s$name="([^"]*)"""").find(attributes)?.groupValues?.get(1)?.unescaped()
    }
}
