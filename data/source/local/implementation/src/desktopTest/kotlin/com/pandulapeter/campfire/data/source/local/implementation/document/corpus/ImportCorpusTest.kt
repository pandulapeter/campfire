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
import com.pandulapeter.campfire.data.model.Logger
import com.pandulapeter.campfire.data.model.importing.ImportedFile
import com.pandulapeter.campfire.data.model.library.decodeLibraryText
import com.pandulapeter.campfire.data.model.library.normalizedToNfc
import com.pandulapeter.campfire.data.source.local.implementation.document.sheet
import com.pandulapeter.campfire.data.source.local.implementation.source.DocumentLocalSourceImpl
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

    /** Exactly the import's path: a document through the extraction and the app's mapping, a text as a pasted sheet. */
    private suspend fun converted(file: File): String? {
        val songs = if (file.extension.equals("txt", ignoreCase = true)) {
            ChordSheetConverter.convert(ChordSheet.ofPlainText(file.readBytes().decodeLibraryText()), String::normalizedToNfc)
        } else {
            val document = DocumentLocalSourceImpl(Logger.Standard).extract(ImportedFile(file.name, file.readBytes())) ?: return null
            ChordSheetConverter.convert(document.sheet(), String::normalizedToNfc)
        }
        return songs.joinToString("{new_song}\n")
    }

    private companion object {
        const val RUNS = ".runs"
        val EXTENSIONS = setOf("pdf", "docx", "txt")
    }
}
