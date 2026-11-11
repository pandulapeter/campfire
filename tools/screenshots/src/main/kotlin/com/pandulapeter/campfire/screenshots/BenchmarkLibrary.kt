/*
 * This file is part of Campfire.
 * Copyright (c) Pandula Péter 2017-2026.
 * https://github.com/pandulapeter/campfire
 *
 * This Source Code Form is subject to the terms of the Mozilla Public License, v. 2.0.
 * If a copy of the MPL was not distributed with this file, You can obtain one at
 * https://mozilla.org/MPL/2.0/.
 */
package com.pandulapeter.campfire.screenshots

import java.io.ByteArrayOutputStream
import java.io.File
import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream
import kotlin.random.Random

/**
 * The library [benchmark] measures, made up from [seed] so that every run reads the same files: [songCount] songs of a
 * few hundred artists, each a few verses and a chorus with chords, one of them the [LONG_SONG] of seventeen sections,
 * and a few setlists. Made rather than committed, since a library this size would be megabytes of fixture.
 */
internal class BenchmarkLibrary(songCount: Int, seed: Int) {
    private val random = Random(seed)

    /** Each song's file name with its text, in no order the app would list them in. */
    val songs: Map<String, String> = buildMap {
        put(LONG_SONG, longSong())
        while (size < songCount) {
            val artist = "${ARTIST_FIRST.random(random)} ${ARTIST_SECOND.random(random)}"
            val title = "${TITLE_FIRST.random(random)} ${TITLE_SECOND.random(random)}"
            val fileName = "${artist.fileNamePart()}-${title.fileNamePart()}.cho"
            if (fileName !in this) put(fileName, song(title = title, artist = artist, sections = random.nextInt(3, 7)))
        }
    }

    /** Each setlist's file name with its document: twenty songs each, a few named twice. */
    val setlists: Map<String, String> = (1..SETLIST_COUNT).associate { index ->
        val entries = List(SETLIST_LENGTH) { songs.keys.random(random) }.joinToString(",") { """{"file":"$it","transposition":${random.nextInt(-2, 3)}}""" }
        "benchmark-setlist_$index.setlist.json" to """{"title":"Setlist $index","date":"2026-10-07","isArchived":false,"songs":[$entries]}"""
    }

    /** Writes the songs and setlists into the `library` folder of [dataDirectory], as a desktop install keeps them. */
    fun writeInto(dataDirectory: File) {
        val songsFolder = File(dataDirectory, "library/songs").apply { mkdirs() }
        songs.forEach { (name, text) -> File(songsFolder, name).writeText(text) }
        val setlistsFolder = File(dataDirectory, "library/setlists").apply { mkdirs() }
        setlists.forEach { (name, text) -> File(setlistsFolder, name).writeText(text) }
    }

    /** The songs as one zip of ChordPro files, which is what a library moved from another device usually arrives as. */
    fun zip(): ByteArray = ByteArrayOutputStream().also { bytes ->
        ZipOutputStream(bytes).use { zip ->
            songs.forEach { (name, text) ->
                zip.putNextEntry(ZipEntry(name))
                zip.write(text.encodeToByteArray())
                zip.closeEntry()
            }
        }
    }.toByteArray()

    private fun song(title: String, artist: String, sections: Int) = buildString {
        appendLine("{title: $title}")
        appendLine("{artist: $artist}")
        val key = KEYS.random(random)
        appendLine("{key: $key}")
        appendLine("{tempo: ${random.nextInt(60, 180)}}")
        if (random.nextInt(4) == 0) appendLine("{tags: ${TAGS.random(random)}}")
        val chords = CHORDS.getValue(key)
        repeat(sections) { index ->
            val isChorus = index % 2 == 1
            appendLine()
            appendLine(if (isChorus) "{start_of_chorus}" else "{start_of_verse: Verse ${index / 2 + 1}}")
            repeat(4) { appendLine(lyricLine(chords)) }
            appendLine(if (isChorus) "{end_of_chorus}" else "{end_of_verse}")
        }
    }

    private fun longSong() = buildString {
        appendLine("{title: $LONG_SONG_TITLE}")
        appendLine("{artist: Benchmark}")
        appendLine("{key: G}")
        appendLine()
        // A line without chords is drawn as one text, which is how the benchmark sees that the song is on screen.
        appendLine(LONG_SONG_FIRST_LINE)
        append(song(title = "", artist = "", sections = 16).substringAfter("{tempo:").substringAfter('\n'))
    }

    private fun lyricLine(chords: List<String>) = List(random.nextInt(5, 9)) { WORDS.random(random) }.mapIndexed { index, word ->
        if (index % 3 == 0) "[${chords.random(random)}]$word" else word
    }.joinToString(" ")

    private fun String.fileNamePart() = lowercase().replace(' ', '_')

    companion object {
        const val LONG_SONG = "benchmark-the_long_road.cho"
        const val LONG_SONG_TITLE = "The Long Road"
        const val LONG_SONG_FIRST_LINE = "Down the long road the benchmark goes"
    }
}

private const val SETLIST_COUNT = 5
private const val SETLIST_LENGTH = 20
private val ARTIST_FIRST = listOf(
    "Amber", "Black", "Blue", "Broken", "Cold", "Crimson", "Dusty", "Electric", "Golden", "Hollow", "Iron", "Lonely", "Midnight",
    "Northern", "Paper", "Quiet", "Rolling", "Silver", "Southern", "Velvet",
)
private val ARTIST_SECOND = listOf(
    "Arrows", "Bells", "Brothers", "Canyons", "Crows", "Drifters", "Embers", "Foxes", "Harbors", "Lanterns", "Mountains", "Owls",
    "Pines", "Rivers", "Sparrows", "Stones", "Strangers", "Tides", "Wolves", "Willows",
)
private val TITLE_FIRST = listOf(
    "After", "Before", "Burning", "Carry", "Dancing", "Distant", "Fading", "Falling", "Hold", "Home", "Last", "Little", "Long",
    "Morning", "Old", "Open", "Running", "Shallow", "Summer", "Wild", "Winter", "Wandering", "Yellow", "Empty", "Gentle",
)
private val TITLE_SECOND = listOf(
    "Boat", "Bridge", "Candle", "Coast", "Daylight", "Field", "Fire", "Garden", "Heart", "Highway", "Horizon", "Letter", "Light",
    "Meadow", "Moon", "Night", "Ocean", "Rain", "Road", "Shadow", "Sky", "Song", "Storm", "Train", "Valley", "Water", "Wind",
)
private val WORDS = listOf(
    "and", "the", "over", "river", "light", "home", "we", "were", "singing", "under", "a", "silver", "moon", "all", "night",
    "long", "you", "said", "come", "back", "to", "me", "when", "morning", "breaks", "on", "open", "road", "fire", "burning",
)
private val TAGS = listOf("folk", "campfire", "ballad", "rock", "slow")
private val KEYS = listOf("C", "G", "D", "A", "E", "Am", "Em")
private val CHORDS = mapOf(
    "C" to listOf("C", "F", "G", "Am", "Dm", "G7"),
    "G" to listOf("G", "C", "D", "Em", "Am", "D7"),
    "D" to listOf("D", "G", "A", "Bm", "Em", "A7"),
    "A" to listOf("A", "D", "E", "F#m", "Bm", "E7"),
    "E" to listOf("E", "A", "B", "C#m", "F#m", "B7"),
    "Am" to listOf("Am", "Dm", "E", "C", "G", "F"),
    "Em" to listOf("Em", "Am", "B7", "G", "D", "C"),
)
