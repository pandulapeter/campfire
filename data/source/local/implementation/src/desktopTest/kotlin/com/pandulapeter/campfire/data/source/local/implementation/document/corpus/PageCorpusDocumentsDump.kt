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
import com.pandulapeter.campfire.data.model.Logger
import com.pandulapeter.campfire.data.model.importing.ImportedFile
import com.pandulapeter.campfire.data.source.local.implementation.source.DocumentLocalSourceImpl
import java.io.File
import kotlin.test.Test
import kotlinx.coroutines.runBlocking
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive

/**
 * Writes the lines the app's own document reader finds in the page model's documents (`tools/page-model`): the import
 * corpus's PDFs and Word files, and the web pages the collector printed to PDF, each line with the layout the model reads
 * (its type's size against the document's body, bold, italic, monospace, its left edge, its column, its page). It is the
 * model's only view of a document, so the training sees exactly what the app would hand it. Skipped unless
 * `campfire.pageCorpus` names the corpus folder; never in CI.
 */
class PageCorpusDocumentsDump {

    @Test
    fun `the page corpus documents are dumped as lines`() = runBlocking {
        val path = System.getProperty("campfire.pageCorpus").orEmpty()
        if (path.isBlank()) {
            println("Page corpus not configured (campfire.pageCorpus)")
            return@runBlocking
        }
        val corpus = File(path)
        val output = File(corpus, "documents").apply { mkdirs() }
        val sources = buildList {
            System.getProperty("campfire.importCorpus").orEmpty().takeIf { it.isNotBlank() }?.let(::File)?.let { importCorpus ->
                importCorpus.walkTopDown().onEnter { it == importCorpus || !it.name.startsWith(".") }
                    .filter { it.isFile && it.extension.lowercase() in EXTENSIONS }
                    .forEach { add("import__" + it.relativeTo(importCorpus).invariantSeparatorsPath.substringBeforeLast('.').replace('/', '_') to it) }
            }
            File(corpus, "printed").listFiles { file -> file.extension == "pdf" }?.forEach { add("printed__" + it.nameWithoutExtension to it) }
        }
        var written = 0
        for ((name, file) in sources.sortedBy { it.first }) {
            val sheet = try {
                DocumentLocalSourceImpl(Logger.Standard).extract(ImportedFile(file.name, file.readBytes()))
            } catch (exception: Exception) {
                println("Not readable: ${file.path} (${exception.message})")
                null
            } ?: continue
            File(output, "$name.json").writeText(Json.encodeToString(JsonObject.serializer(), document(name, file, sheet)))
            written++
        }
        println("Page corpus: dumped $written documents into ${output.absolutePath}")
    }

    private fun document(name: String, file: File, sheet: ChordSheet): JsonObject {
        val sizes = sheet.pages.flatMap { page -> page.lines.flatMap { line -> line.spans.filter { it.text.isNotBlank() }.map { it.size } } }.sorted()
        val body = sizes.getOrNull(sizes.size / 2)?.takeIf { it > 0 } ?: 1.0
        val lines = sheet.pages.flatMapIndexed { pageIndex, page ->
            val left = page.lines.flatMap { it.spans }.minOfOrNull { it.start } ?: 0.0
            val right = page.lines.flatMap { it.spans }.maxOfOrNull { it.end } ?: 1.0
            page.lines.map { line -> line(line, pageIndex, body, left, (right - left).takeIf { it > 0 } ?: 1.0) }
        }
        return JsonObject(
            mapOf(
                "id" to JsonPrimitive(name),
                "source" to JsonPrimitive(file.absolutePath),
                "lines" to JsonArray(lines),
            ),
        )
    }

    /**
     * One line as the model reads it. Its text joins the spans with a space wherever a gap of about a character's width
     * stands between two, which is how a chord row keeps its chords apart; the layout is measured on the spans holding text.
     */
    private fun line(line: ChordSheet.Line, page: Int, body: Double, left: Double, width: Double): JsonObject {
        val spans = line.spans.filter { it.text.isNotEmpty() }.sortedBy { it.start }
        val text = buildString {
            spans.forEachIndexed { index, span ->
                val previous = spans.getOrNull(index - 1)
                if (previous != null && span.start - previous.end > span.size * GAP && !endsWith(' ') && !span.text.startsWith(' ')) append(' ')
                append(span.text)
            }
        }
        val printed = spans.filter { it.text.isNotBlank() }
        val size = printed.maxOfOrNull { it.size } ?: body
        return JsonObject(
            mapOf(
                "text" to JsonPrimitive(text),
                "page" to JsonPrimitive(page),
                "column" to JsonPrimitive(line.column ?: -1),
                "size" to JsonPrimitive(size / body),
                "bold" to JsonPrimitive(printed.isNotEmpty() && printed.all { it.isBold }),
                "italic" to JsonPrimitive(printed.isNotEmpty() && printed.all { it.isItalic }),
                "monospace" to JsonPrimitive(printed.isNotEmpty() && printed.all { it.isMonospace }),
                "indent" to JsonPrimitive(((printed.minOfOrNull { it.start } ?: left) - left) / width),
                "heading" to JsonPrimitive(line.isHeading),
            ),
        )
    }

    private companion object {
        val EXTENSIONS = setOf("pdf", "docx")
        const val GAP = 0.25
    }
}
