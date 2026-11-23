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

import com.pandulapeter.campfire.chordpro.convert.ChordSheet
import com.pandulapeter.campfire.chordpro.convert.ChordSheetConverter
import com.pandulapeter.campfire.chordpro.convert.ChordSheetHtml
import com.pandulapeter.campfire.chordpro.convert.ChordSheetPageLines
import com.pandulapeter.campfire.data.model.Logger
import com.pandulapeter.campfire.data.model.importing.ImportedFile
import com.pandulapeter.campfire.data.model.library.decodeLibraryText
import com.pandulapeter.campfire.data.model.library.normalizedToNfc
import com.pandulapeter.campfire.data.source.local.implementation.source.DocumentLocalSourceImpl
import com.pandulapeter.campfire.pagesong.PageLineSource
import com.pandulapeter.campfire.pagesong.PageSong
import java.io.File
import kotlin.test.Test
import kotlin.test.assertTrue
import kotlin.test.fail
import kotlinx.coroutines.runBlocking

/**
 * Scores the importer against the private corpus named by `campfire.importCorpus`: other programs' documents, each beside
 * the `.cho` a careful person would have typed from it. It measures and never fails on a score; it fails only when the
 * property names a folder with nothing to score, which would make every run look clean. See `convert/CLAUDE.md`.
 */
class ImportCorpusTest {

    @Test
    fun `the import corpus is scored and reported`() = runBlocking {
        val path = System.getProperty("campfire.importCorpus").orEmpty()
        if (path.isBlank()) {
            println("Import corpus not configured (campfire.importCorpus)")
            return@runBlocking
        }
        val corpus = File(path)
        if (!corpus.isDirectory) fail("The import corpus $path is not a folder")
        val runs = File(corpus, RUNS)
        val output = File(runs, "out").apply { deleteRecursively() }
        val scores = sortedMapOf<String, CorpusScore>()
        val unscored = mutableListOf<String>()
        val failures = mutableMapOf<String, String>()
        for (file in documents(corpus)) {
            val document = file.relativeTo(corpus).invariantSeparatorsPath.substringBeforeLast('.')
            val expected = File(file.parentFile, "${file.nameWithoutExtension}.cho").takeIf { it.isFile }?.readBytes()?.decodeLibraryText()
            val actual = try {
                converted(file) ?: error("Not readable as a document")
            } catch (exception: Exception) {
                failures[document] = exception.toString().lines().first()
                null
            }
            if (actual != null) File(output, "$document.cho").apply { parentFile.mkdirs() }.writeText(actual)
            when {
                expected == null -> unscored += document
                actual == null -> scores[document] = CorpusScore.FAILED
                else -> scores[document] = CorpusReport.rounded(CorpusScore.of(expected, actual))
            }
        }
        assertTrue(scores.isNotEmpty(), "The import corpus $path holds no document with an expected .cho beside it")
        val table = File(runs, "scores.tsv")
        val previousTable = File(runs, "scores.previous.tsv")
        val newTable = CorpusReport.table(scores)
        // The previous file is the last run that scored differently, so a rerun that changed nothing keeps the delta.
        if (table.isFile && table.readText() != newTable) table.copyTo(previousTable, overwrite = true)
        table.writeText(newTable)
        val previous = if (previousTable.isFile) CorpusReport.parse(previousTable.readText()) else emptyMap()
        val report = File(runs, "report.txt")
        report.writeText(CorpusReport.report(scores, previous, unscored, failures))
        println("Import corpus: scored ${scores.size} documents, report in ${report.absolutePath}")
    }

    private fun documents(corpus: File) = corpus.walkTopDown()
        .onEnter { it == corpus || !it.name.startsWith(".") }
        .filter { it.isFile && it.parentFile != corpus && it.extension.lowercase() in EXTENSIONS }
        .sortedBy { it.path }
        .toList()

    /**
     * Exactly the import's path: a document through the extraction, the shipped page model's scores of its lines (as
     * `PageCropperImpl` gives them; none where the weights are missing), and the app's mapping, a text as a pasted sheet,
     * which a file's text is, so it is never cropped. A text
     * with an `.html` of the same name beside it is one clipboard's two flavours, read as the app's paste reads them
     * (`pastedText`): the chord sheet the HTML holds, else the text.
     */
    private suspend fun converted(file: File): String? {
        val songs = if (file.extension.equals("txt", ignoreCase = true)) {
            val html = File(file.parentFile, "${file.nameWithoutExtension}.html").takeIf { it.isFile }?.readBytes()?.decodeLibraryText()
            val text = html?.let(ChordSheetHtml::chordSheetText) ?: file.readBytes().decodeLibraryText()
            ChordSheetConverter.convert(ChordSheet.ofPlainText(text), String::normalizedToNfc)
        } else {
            val document = DocumentLocalSourceImpl(Logger.Standard).extract(ImportedFile(file.name, file.readBytes())) ?: return null
            val pageScores = pageSong?.crop(ChordSheetPageLines.of(document), PageLineSource.DOCUMENT)?.scores
            ChordSheetConverter.convert(document, String::normalizedToNfc, pageScores = pageScores)
        }
        return songs.joinToString("{new_song}\n")
    }

    private val pageSong = File(System.getProperty("pageModelWeights").orEmpty()).takeIf { it.isFile }?.let { PageSong(it.readBytes()) }

    private companion object {
        const val RUNS = ".runs"
        val EXTENSIONS = setOf("pdf", "docx", "txt")
    }
}
