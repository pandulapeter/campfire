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

import com.pandulapeter.campfire.data.model.hashing.Sha256
import com.pandulapeter.campfire.data.model.importing.ImportLimits
import com.pandulapeter.campfire.data.model.library.LibraryFiles
import com.pandulapeter.campfire.data.model.share.SharedLinkKind
import java.nio.charset.Charset
import java.text.Normalizer
import kotlin.random.Random

/**
 * A library generated from [seed] for [SharedCopyParityTest], with what each of its shares must hold worked out from
 * the generator's own knowledge ([expectedCopy]) rather than by any code of the app.
 *
 * Songs show pictures and attach files (some of them past the attachments' budget where [hasLargeAttachments]), some
 * the library lacks; songs are UTF-8, UTF-8 behind a byte order mark, or Windows-1252, with `\n` or `\r\n` line ends.
 * Setlists name songs under the library's spelling, under another case or Unicode form, or songs the library lacks.
 * Beside what is shared: songs, files, and setlists nothing shared names, among them [unsharedSong], and per-device
 * overrides ([preferences]) of any song's transposition, capo, and tempo.
 */
internal class SharedCopyLibrary(val seed: Int) {

    private val random = Random(seed)

    /** The files the library holds, by name. */
    val files = mutableMapOf<String, ByteArray>()

    val hasLargeAttachments = seed % LARGE_ATTACHMENTS_EVERY == 0

    val songs: List<Song>

    val setlists: List<Setlist>

    /** The items shared, in the order they are shared. */
    val shares: List<Pair<SharedLinkKind, String>>

    /** A song no shared item names, which the test edits. */
    val unsharedSong: Song

    /** `preferences.json` with per-device overrides of songs, shared or not. */
    val preferences: ByteArray

    init {
        val pictures = List(random.nextInt(1, 6)) { picture() }
        val attachments = List(random.nextInt(1, 5)) { attachment() }
        val large = if (hasLargeAttachments) largeAttachments() else emptyList()
        // Named by songs but missing from the library, as after a picture that never arrived from another device.
        val missing = List(2) { index -> "%016x".format(random.nextLong()) + if (index == 0) ".png" else ".pdf" }
        // A song may show an attachment, which makes it a picture of the copy, but not a large one: tens of megabytes of
        // pictures are past what a copy holds, which refuses the share.
        val named = pictures + attachments
        songs = List(random.nextInt(2, 9)) { index ->
            val name = songName(index)
            val shown = (named + missing).filter { random.nextInt(4) == 0 }
            val attached = (attachments + pictures + missing).filter { random.nextInt(3) == 0 } + large.filter { random.nextBoolean() }
            song(name = name, shown = shown, attached = attached.shuffled(random))
        }
        // Its own picture and attachment, which nothing shared names, so that a copy carrying either is caught.
        unsharedSong = song(name = "unshared-song_$seed.cho", shown = listOf(picture()), attached = listOf(attachment()))
        setlists = List(random.nextInt(1, 4)) { index ->
            val references = songs.filter { random.nextBoolean() }.map { spellingOf(it.name) } +
                List(random.nextInt(0, 2)) { "missing_${index}_$it.cho" }
            Setlist(name = "set_$index.setlist.json", references = references.shuffled(random), random = random)
        }
        val items = songs.map { SharedLinkKind.SONG to it.name } + setlists.map { SharedLinkKind.SETLIST to it.name }
        // At least one item stays unshared, so that what nothing shared names is not only the dedicated song.
        shares = items.shuffled(random).take(random.nextInt(1, items.size))
        val overridden = (songs + unsharedSong).filter { random.nextBoolean() }.map { it.name }
        preferences = buildString {
            append("{\n")
            append("  \"transpositions\": {${overridden.joinToString { "\"$it\": ${random.nextInt(-5, 6)}" }}},\n")
            append("  \"capos\": {${overridden.joinToString { "\"$it\": ${random.nextInt(0, 8)}" }}},\n")
            append("  \"tempos\": {${overridden.joinToString { "\"$it\": ${random.nextInt(40, 200)}" }}}\n")
            append("}\n")
        }.encodeToByteArray()
    }

    /**
     * The entries the copy of the shared item [fileName] holds, by name: the item, the songs a setlist names that the
     * library holds (under the setlist's spelling), and under `images/` the library files they name, every picture they
     * show and the attachments that fit the budget in the order the songs name them. Each song is its library file's
     * text but for the lines of the attachments left out, in UTF-8 as the export writes every text.
     */
    fun expectedCopy(kind: SharedLinkKind, fileName: String): ExpectedCopy {
        val byIdentity = (songs + unsharedSong).associateBy { LibraryFiles.identityKey(it.name) }
        val setlist = setlists.firstOrNull { it.name == fileName }
        val copySongs = when (kind) {
            SharedLinkKind.SONG -> listOf(fileName to byIdentity.getValue(LibraryFiles.identityKey(fileName)))
            SharedLinkKind.SETLIST -> setlist!!.references.mapNotNull { reference ->
                byIdentity[LibraryFiles.identityKey(reference)]?.let { reference to it }
            }
        }
        val shown = copySongs.flatMapTo(mutableSetOf()) { (_, song) -> song.shown }.filterTo(mutableSetOf()) { it in files }
        var room = ImportLimits.MAX_SHARED_ATTACHMENTS_SIZE
        val leftOut = mutableSetOf<String>()
        val carried = copySongs.flatMap { (_, song) -> song.attached }.distinct().filter { it in files && it !in shown }.filter { name ->
            val size = files.getValue(name).size
            (size <= room).also { fits -> if (fits) room -= size else leftOut += name }
        }
        val entries = buildMap {
            if (setlist != null) put(setlist.name, setlist.bytes)
            copySongs.forEach { (name, song) -> put(name, song.copiedWithout(leftOut)) }
            (shown + carried).forEach { name -> put("images/$name", files.getValue(name)) }
        }
        return ExpectedCopy(
            entries = entries,
            leftOutAttachments = leftOut.size,
            respelledSongs = copySongs.count { (name, song) -> name != song.name },
        )
    }

    /** What a copy holds, and how often it meets the cases the test counts to show they were met. */
    class ExpectedCopy(val entries: Map<String, ByteArray>, val leftOutAttachments: Int, val respelledSongs: Int)

    private fun songName(index: Int) = when (random.nextInt(3)) {
        0 -> "artist_$index-title_$index.cho"
        // A name an older version, another app, or a hand put in the folder, with a letter Unicode composes.
        1 -> "café_$index-title_$index.cho"
        else -> "пісня_$index.cho"
    }

    /** [name] as a setlist may spell it: as the library does, in capitals, or decomposed. */
    private fun spellingOf(name: String) = when (random.nextInt(3)) {
        0 -> name.substringBeforeLast('.').uppercase() + ".cho"
        1 -> Normalizer.normalize(name, Normalizer.Form.NFD)
        else -> name
    }

    private fun song(name: String, shown: List<String>, attached: List<String>) = Song(
        name = name,
        shown = shown,
        attached = attached,
        encoding = Encoding.entries.random(random),
        separator = if (random.nextBoolean()) "\n" else "\r\n",
        verse = "Cœur brûlé, ${random.nextInt()}",
    )

    private fun picture() = TestPictures.png(seed = random.nextInt(), size = random.nextInt(100, 4_000)).let { bytes -> store(bytes, ".png") }

    private fun attachment() = store(random.nextBytes(random.nextInt(10, 3_000)), listOf(".pdf", ".mp3", ".jpg").random(random))

    private fun largeAttachments() = LARGE.shuffled(random).map { (bytes, name) -> name.also { files[it] = bytes } }

    private fun store(bytes: ByteArray, extension: String) =
        (Sha256.hashToHex(bytes).take(LibraryFiles.MIN_IMAGE_NAME_DIGITS) + extension).also { files[it] = bytes }

    enum class Encoding { UTF_8, UTF_8_WITH_BOM, WINDOWS_1252 }

    class Song(
        val name: String,
        val shown: List<String>,
        val attached: List<String>,
        private val encoding: Encoding,
        private val separator: String,
        private val verse: String,
    ) {

        /** The file in the library. */
        val bytes: ByteArray = text(leftOut = emptySet()).let { text ->
            when (encoding) {
                Encoding.UTF_8 -> text.encodeToByteArray()
                Encoding.UTF_8_WITH_BOM -> byteArrayOf(0xEF.toByte(), 0xBB.toByte(), 0xBF.toByte()) + text.encodeToByteArray()
                Encoding.WINDOWS_1252 -> text.toByteArray(WINDOWS_1252)
            }
        }

        /**
         * The song as a copy carries it: its text without the lines of the attachments [leftOut], in UTF-8 and without a
         * byte order mark, as the export writes every text it reads.
         */
        fun copiedWithout(leftOut: Set<String>) = text(leftOut).let { text ->
            // What of the text Windows-1252 can hold: the letters it cannot are stored as question marks.
            if (encoding == Encoding.WINDOWS_1252) String(text.toByteArray(WINDOWS_1252), WINDOWS_1252) else text
        }.encodeToByteArray()

        private fun text(leftOut: Set<String>) = (
            listOf("{title: ${name.substringBefore('.')}}", "{artist: Someone}") +
                attached.filter { it !in leftOut }.map { "{meta: attachment $it Recording of $it}" } +
                shown.map { "{image: src=$it}" } +
                listOf("[Am]$verse", "[C]La la", "")
            ).joinToString(separator)

        /** The song as edited by hand, which no shared copy may follow. */
        val edited get() = bytes + "[G]One more line$separator".encodeToByteArray()
    }

    class Setlist(val name: String, val references: List<String>, random: Random) {

        /** A setlist's own transpositions, capos, and tempos travel in its file, so some entries carry one. */
        val bytes = buildString {
            // Dated, since the app dates an undated setlist by writing it again.
            append("{\n  \"title\": \"${name.substringBefore('.')}\",\n  \"date\": \"2026-06-21\",\n  \"songs\": [\n")
            append(
                references.joinToString(",\n") { reference ->
                    if (random.nextBoolean()) {
                        "    {\"file\": \"$reference\"}"
                    } else {
                        "    {\"file\": \"$reference\", \"transposition\": ${random.nextInt(-3, 4)}}"
                    }
                },
            )
            append("\n  ]\n}\n")
        }.encodeToByteArray()
    }

    private companion object {

        val WINDOWS_1252: Charset = charset("windows-1252")

        /**
         * One seed in this many attaches two files that do not fit the attachments' budget together, so the later one
         * named is left out; few, since they are tens of megabytes.
         */
        const val LARGE_ATTACHMENTS_EVERY = 50

        /** Read once for every seed that has them: together past the budget, either alone within it. */
        val LARGE by lazy {
            listOf(26, 25).map { megabytes ->
                val bytes = Random(megabytes).nextBytes(megabytes shl 20)
                bytes to Sha256.hashToHex(bytes).take(LibraryFiles.MIN_IMAGE_NAME_DIGITS) + ".mp4"
            }
        }
    }
}
