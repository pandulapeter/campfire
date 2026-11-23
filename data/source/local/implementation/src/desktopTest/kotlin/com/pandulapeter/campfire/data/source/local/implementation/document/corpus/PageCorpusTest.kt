/*
 * This file is part of Campfire.
 * Copyright (c) Pandula Péter 2017-2026.
 * https://github.com/pandulapeter/campfire
 *
 * This Source Code Form is subject to the terms of the Mozilla Public License, v. 2.0.
 * If a copy of the MPL was not distributed with this file, You can obtain one at
 * https://mozilla.org/MPL/2.0/.
 */
package com.pandulapeter.campfire.data.source.local.implementation.document.corpus

import com.pandulapeter.campfire.pagesong.PageLine
import com.pandulapeter.campfire.pagesong.PageLineSource
import com.pandulapeter.campfire.pagesong.PageSong
import java.io.File
import kotlin.test.Test
import kotlin.test.assertTrue
import kotlin.test.fail
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.boolean
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive

/**
 * Scores the page model the app ships (`composeResources/files/page-model.bin`) on the labelled web pages of the private
 * corpus named by `campfire.pageCorpus` (`tools/page-model`): each page cropped as the import crops a paste
 * (`PageCropperImpl`: the kept lines with the title and artist lines, the whole page where the model sees no song)
 * against keeping every line, as `gate.py` scores a held-out fold. Its numbers are only held-out ones for pages added
 * after the weights were trained; on the rest they show what the shipped model does, not how it generalizes. It prints
 * and asserts only loose sanity bounds, which a broken model or a broken crop would miss by far. Skipped where the
 * property is blank or the weights are not there; never in CI.
 */
class PageCorpusTest {

    @Test
    fun `the page corpus is cropped and reported`() {
        val path = System.getProperty("campfire.pageCorpus").orEmpty()
        if (path.isBlank()) {
            println("Page corpus not configured (campfire.pageCorpus)")
            return
        }
        val weights = File(System.getProperty("pageModelWeights").orEmpty())
        if (!weights.isFile) {
            println("Page model weights not found at ${weights.path}")
            return
        }
        val corpus = File(path)
        if (!File(corpus, "labels").isDirectory) fail("The page corpus $path has no labels folder")
        val pageSong = PageSong(weights.readBytes())
        val scores = File(corpus, "labels").listFiles { file -> file.extension == "json" }.orEmpty().sortedBy { it.name }.mapNotNull { file ->
            val labelled = Json.parseToJsonElement(file.readText()).jsonObject
            val id = labelled.getValue("id").jsonPrimitive.content
            if (labelled["source"]?.jsonPrimitive?.content != "page" || id.startsWith("synthetic__")) return@mapNotNull null
            val page = File(corpus, "pages/$id.json").takeIf { it.isFile } ?: return@mapNotNull null
            val labels = labelled.getValue("labels").jsonArray.map { it.jsonPrimitive.content }
            val lines = Json.parseToJsonElement(page.readText()).jsonObject.getValue("lines").jsonArray.map { it.jsonPrimitive.content }.take(labels.size)
            score(id, lines, labels, labelled.isNoSong(), pageSong)
        }
        val songs = scores.filter { !it.isNoSong }
        val noSongs = scores.filter { it.isNoSong }
        val wins = songs.count { it.crop > it.baseline + 1e-9 }
        val ties = songs.count { kotlin.math.abs(it.crop - it.baseline) <= 1e-9 }
        val lostLines = songs.sumOf { it.lostSongLines }
        val songLines = songs.sumOf { it.songLines }
        println(
            """
            Page corpus: ${scores.size} labelled pages
            song pages: ${songs.size}; crop beats keeping everything on $wins, ties $ties, worse on ${songs.size - wins - ties}
            mean line F1: keep everything ${"%.4f".format(songs.map { it.baseline }.average())}, crop ${"%.4f".format(songs.map { it.crop }.average())}
            pages losing a song line: ${songs.count { it.lostSongLines > 0 }} ($lostLines of $songLines song lines lost)
            title kept on ${songs.count { it.titleKept == true }} of ${songs.count { it.titleKept != null }} song pages labelling one, artist on ${songs.count { it.artistKept == true }} of ${songs.count { it.artistKept != null }}
            pages with no song: ${noSongs.size}, told as none ${noSongs.count { !it.holdsSong }}
            most song lines lost: ${songs.filter { it.lostSongLines > 0 }.sortedByDescending { it.lostSongLines }.take(8).joinToString { "${it.id} (${it.lostSongLines}/${it.songLines})" }}
            """.trimIndent(),
        )
        assertTrue(songs.isNotEmpty(), "The page corpus $path holds no labelled song page")
        assertTrue(lostLines <= songLines / 10, "The crop lost $lostLines of $songLines song lines")
        assertTrue(wins + ties >= songs.size / 2, "The crop beats or ties keeping everything on only ${wins + ties} of ${songs.size} song pages")
    }

    private fun score(id: String, lines: List<String>, labels: List<String>, isNoSong: Boolean, pageSong: PageSong): PageScore {
        val crop = pageSong.crop(lines.map(::PageLine), PageLineSource.PAGE)
        val kept = if (crop.holdsSong) (crop.keptLines + listOfNotNull(crop.titleLine, crop.artistLine)).toSet() else lines.indices.toSet()
        // Lines with no text and lines the teacher's two runs disagreed on are left out, as the gate leaves them out.
        val printed = lines.indices.filter { lines[it].isNotBlank() && labels[it] != "IGNORE" }.toSet()
        val truth = printed.filter { labels[it] == "SONG" || labels[it] == "METADATA" }.toSet()
        val song = printed.filter { labels[it] == "SONG" }.toSet()

        /** Whether a line labelled [label] is kept, or null where the page labels none. */
        fun keptOf(label: String) = printed.filter { labels[it] == label }.takeIf { it.isNotEmpty() }?.any { it in kept }
        fun f1(chosen: Set<Int>): Double {
            if (truth.isEmpty()) return if (chosen.isEmpty()) 1.0 else 0.0
            val hits = (chosen intersect truth).size
            if (hits == 0) return 0.0
            val precision = hits.toDouble() / chosen.size
            val recall = hits.toDouble() / truth.size
            return 2 * precision * recall / (precision + recall)
        }
        return PageScore(
            id = id,
            isNoSong = isNoSong || truth.isEmpty(),
            holdsSong = crop.holdsSong,
            baseline = f1(printed),
            crop = f1(printed intersect kept),
            songLines = song.size,
            lostSongLines = (song - kept).size,
            titleKept = keptOf("TITLE"),
            artistKept = keptOf("ARTIST"),
        )
    }

    private fun JsonObject.isNoSong() = this["noSong"]?.jsonPrimitive?.boolean == true

    private class PageScore(
        val id: String,
        val isNoSong: Boolean,
        val holdsSong: Boolean,
        val baseline: Double,
        val crop: Double,
        val songLines: Int,
        val lostSongLines: Int,
        val titleKept: Boolean?,
        val artistKept: Boolean?,
    )
}
