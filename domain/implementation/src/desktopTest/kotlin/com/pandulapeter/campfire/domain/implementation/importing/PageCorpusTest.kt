/*
 * This file is part of Campfire.
 * Copyright (c) Pandula Péter 2017-2026.
 * https://github.com/pandulapeter/campfire
 *
 * This Source Code Form is subject to the terms of the Mozilla Public License, v. 2.0.
 * If a copy of the MPL was not distributed with this file, You can obtain one at
 * https://mozilla.org/MPL/2.0/.
 */
package com.pandulapeter.campfire.domain.implementation.importing

import com.pandulapeter.campfire.chordpro.convert.PageTitle
import com.pandulapeter.campfire.data.model.Logger
import com.pandulapeter.campfire.data.model.importing.PageLineMarks
import com.pandulapeter.campfire.domain.api.importing.PageSongLines
import com.pandulapeter.campfire.domain.api.importing.pageTextLinesOf
import com.pandulapeter.campfire.pagesong.PageLineSource
import com.pandulapeter.campfire.pagesong.PageSong
import java.io.File
import kotlinx.coroutines.runBlocking
import kotlin.test.Test
import kotlin.test.assertTrue
import kotlin.test.fail
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.boolean
import kotlinx.serialization.json.doubleOrNull
import kotlinx.serialization.json.intOrNull
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive

/**
 * Scores the page models the app ships (`composeResources/files/page-model.bin` and `page-model-marked.bin`) on the
 * labelled web pages of the private corpus named by `campfire.pageCorpus` (`tools/page-model`): each page cropped by the
 * import's own crop ([PageTextCrop] over [PageSongLines.of], as `PageCropperImpl` answers it: the kept lines with the
 * title and artist lines, less those heading the song where the converter reads no title from them, which title the song
 * instead; the whole page where the model sees no song) against keeping every line, as `gate.py` scores a held-out fold.
 * A page read with structure marks (`pages/<id>.json`'s `marks`) is cropped with them by the marked model, as a page the
 * app read itself is, and any other by the plain one; every page is then cropped again by the plain model as a paste of
 * its text, which is printed beside it. Its numbers are only held-out ones for pages added after the weights were
 * trained; on the rest they show what the shipped models do, not how they generalize. It prints and asserts only loose
 * sanity bounds on the app's crop, which a broken model or a broken crop would miss by far. A page whose lines the
 * import's split of its text would number differently (a lone carriage return in a line) is skipped, so no label shifts.
 * Skipped where the property is blank or either weights file is not there; never in CI.
 */
class PageCorpusTest {

    @Test
    fun `the page corpus is cropped and reported`() {
        val path = System.getProperty("campfire.pageCorpus").orEmpty()
        if (path.isBlank()) {
            println("Page corpus not configured (campfire.pageCorpus)")
            return
        }
        val plain = pageSongOf("pageModelWeights") ?: return
        val marked = pageSongOf("pageModelMarkedWeights") ?: return
        val corpus = File(path)
        if (!File(corpus, "labels").isDirectory) fail("The page corpus $path has no labels folder")
        var skipped = 0
        val pages = File(corpus, "labels").listFiles { file -> file.extension == "json" }.orEmpty().sortedBy { it.name }.mapNotNull { file ->
            val labelled = Json.parseToJsonElement(file.readText()).jsonObject
            val id = labelled.getValue("id").jsonPrimitive.content
            if (labelled["source"]?.jsonPrimitive?.content != "page" || id.startsWith("synthetic__")) return@mapNotNull null
            val page = File(corpus, "pages/$id.json").takeIf { it.isFile } ?: return@mapNotNull null
            val labels = labelled.getValue("labels").jsonArray.map { it.jsonPrimitive.content }
            val read = Json.parseToJsonElement(page.readText()).jsonObject
            val allLines = read.getValue("lines").jsonArray.map { it.jsonPrimitive.content }
            val lines = allLines.take(labels.size)
            if (lines.joinToString("\n").lines().size != lines.size) {
                skipped++
                return@mapNotNull null
            }
            val marks = read["marks"]?.jsonArray?.takeIf { it.size == allLines.size }?.take(lines.size)?.map(::marksOf)
            val isMarked = pageTextLinesOf(lines.joinToString("\n"), marks).any { it.marks != null }
            PageScores(
                app = score(id, lines, marks, labels, labelled.isNoSong(), if (isMarked) marked else plain),
                paste = score(id, lines, null, labels, labelled.isNoSong(), plain),
                isMarked = isMarked,
            )
        }
        println("Page corpus: ${pages.size} labelled pages ($skipped skipped for splitting into other lines)")
        val app = pages.map { it.app }
        println("As the app crops them, ${pages.count { it.isMarked }} by the marked model and the rest by the plain one:\n${reportOf(app)}")
        println("Every page by the plain model, as a paste:\n${reportOf(pages.map { it.paste })}")
        val songs = app.filter { !it.isNoSong }
        val wins = songs.count { it.crop > it.baseline + 1e-9 }
        val ties = songs.count { kotlin.math.abs(it.crop - it.baseline) <= 1e-9 }
        val lostLines = songs.sumOf { it.lostSongLines }
        val songLines = songs.sumOf { it.songLines }
        assertTrue(songs.isNotEmpty(), "The page corpus $path holds no labelled song page")
        assertTrue(lostLines <= songLines / 10, "The crop lost $lostLines of $songLines song lines")
        assertTrue(wins + ties >= songs.size / 2, "The crop beats or ties keeping everything on only ${wins + ties} of ${songs.size} song pages")
    }

    /** The model whose weights the system property [name] points at, or null, said, where they are not there. */
    private fun pageSongOf(name: String): PageSong? {
        val weights = File(System.getProperty(name).orEmpty())
        if (!weights.isFile) println("Page model weights not found at ${weights.path} ($name)")
        return weights.takeIf { it.isFile }?.let { PageSong(it.readBytes()) }
    }

    private fun reportOf(scores: List<PageScore>): String {
        val songs = scores.filter { !it.isNoSong }
        val noSongs = scores.filter { it.isNoSong }
        val wins = songs.count { it.crop > it.baseline + 1e-9 }
        val ties = songs.count { kotlin.math.abs(it.crop - it.baseline) <= 1e-9 }
        return """
            song pages: ${songs.size}; crop beats keeping everything on $wins, ties $ties, worse on ${songs.size - wins - ties}
            mean line F1: keep everything ${"%.4f".format(songs.map { it.baseline }.average())}, crop ${"%.4f".format(songs.map { it.crop }.average())}
            pages losing a song line: ${songs.count { it.lostSongLines > 0 }} (${songs.sumOf { it.lostSongLines }} of ${songs.sumOf { it.songLines }} song lines lost)
            title kept or read on ${songs.count { it.titleKept == true }} of ${songs.count { it.titleKept != null }} song pages labelling one, artist on ${songs.count { it.artistKept == true }} of ${songs.count { it.artistKept != null }}
            header lines read as the title and artist on ${songs.count { it.header != null }} song pages; read by the converter instead on ${songs.count { it.isHeaderConverted }}
            some of them: ${songs.mapNotNull { it.header }.take(12).joinToString(" | ") { "${it.title} / ${it.artist ?: "-"}" }}
            pages with no song: ${noSongs.size}, told as none ${noSongs.count { !it.holdsSong }}
            most song lines lost: ${songs.filter { it.lostSongLines > 0 }.sortedByDescending { it.lostSongLines }.take(8).joinToString { "${it.id} (${it.lostSongLines}/${it.songLines})" }}
        """.trimIndent()
    }

    private fun score(id: String, lines: List<String>, marks: List<PageLineMarks?>?, labels: List<String>, isNoSong: Boolean, pageSong: PageSong): PageScore {
        val crop = pageSong.crop(pageTextLinesOf(lines.joinToString("\n"), marks), PageLineSource.PAGE)
        val found = PageSongLines.of(crop)
        val cropped = found?.let { PageTextCrop.croppedLines(lines, it) }
        val withHeader = cropped?.withHeader?.toSet() ?: lines.indices.toSet()
        val heading = cropped?.heading.orEmpty()
        val converted = runBlocking {
            PageTextCrop.cropped(lines.joinToString("\n"), marks, FakePageCropper(songLinesOf = { found }), Logger { })?.let(PageTextCrop::converted)
        }
        // The header lines leave the song only where the converter reads no title from the crop with them, as the import does.
        val isHeaderRead = heading.isNotEmpty() && converted != null && (converted.title != null || converted.artist != null)
        val isHeaderConverted = heading.isNotEmpty() && !isHeaderRead
        val kept = if (isHeaderRead) cropped?.body?.toSet().orEmpty() else withHeader
        val header = if (!isHeaderRead) null else PageTitle(title = converted.title.orEmpty(), artist = converted.artist)
        // Lines with no text and lines the teacher's two runs disagreed on are left out, as the gate leaves them out.
        val printed = lines.indices.filter { lines[it].isNotBlank() && labels[it] != "IGNORE" }.toSet()
        val truth = printed.filter { labels[it] == "SONG" || labels[it] == "METADATA" }.toSet()
        val song = printed.filter { labels[it] == "SONG" }.toSet()

        /** Whether a line labelled [label] is kept, or null where the page labels none. */
        fun keptOf(label: String) = printed.filter { labels[it] == label }.takeIf { it.isNotEmpty() }?.any { it in kept || it in heading && isHeaderRead }
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
            header = header,
            isHeaderConverted = isHeaderConverted,
        )
    }

    private fun JsonObject.isNoSong() = this["noSong"]?.jsonPrimitive?.boolean == true

    /** A line's marks as the app's page read writes them and `PageSnapshot` decodes them: none for `{}` or an unaligned line. */
    private fun marksOf(element: JsonElement): PageLineMarks? {
        val marks = element.jsonObject
        fun flag(name: String) = marks[name]?.jsonPrimitive?.intOrNull == 1
        val size = marks["size"]?.jsonPrimitive?.doubleOrNull
        if (size == null || flag("unaligned")) return null
        return PageLineMarks(
            size = size.toFloat(),
            isBold = flag("bold"),
            isItalic = flag("italic"),
            isMonospace = flag("monospace"),
            isHeading = flag("heading"),
            isLink = flag("link"),
            isNewBlock = flag("newBlock"),
            isLandmark = flag("landmark"),
        )
    }

    /** A page cropped as the app crops it, [isMarked] where that was by the marked model, and as a paste. */
    private class PageScores(val app: PageScore, val paste: PageScore, val isMarked: Boolean)

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
        val header: PageTitle?,
        val isHeaderConverted: Boolean,
    )
}
