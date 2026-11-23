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
import com.pandulapeter.campfire.chordpro.convert.ChordSheetPageLines
import com.pandulapeter.campfire.data.model.Logger
import com.pandulapeter.campfire.data.model.importing.ImportedFile
import com.pandulapeter.campfire.data.source.local.implementation.source.DocumentLocalSourceImpl
import com.pandulapeter.campfire.pagesong.PageLine
import java.io.File
import kotlin.test.Test
import kotlinx.coroutines.runBlocking
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive

/**
 * Writes the lines the app's own document reader finds in the page model's documents (`tools/page-model`): the import
 * corpus's PDFs and Word files, the web pages the collector printed to PDF, and the documents made up for training, each
 * line with the layout the model reads, through the import's own mapping (`ChordSheetPageLines`), so the training sees
 * exactly what the app hands the model. Skipped unless
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
            for (folder in listOf("printed", "synthetic")) {
                File(corpus, folder).listFiles { file -> file.extension == "pdf" }?.forEach { add("${folder}__" + it.nameWithoutExtension to it) }
            }
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

    private fun document(name: String, file: File, sheet: ChordSheet) = JsonObject(
        mapOf(
            "id" to JsonPrimitive(name),
            "source" to JsonPrimitive(file.absolutePath),
            "lines" to JsonArray(ChordSheetPageLines.of(sheet).map(::line)),
        ),
    )

    private fun line(line: PageLine): JsonObject {
        val layout = line.layout!!
        return JsonObject(
            mapOf(
                "text" to JsonPrimitive(line.text),
                "page" to JsonPrimitive(layout.page),
                "column" to JsonPrimitive(layout.column ?: -1),
                "size" to JsonPrimitive(layout.size),
                "bold" to JsonPrimitive(layout.isBold),
                "italic" to JsonPrimitive(layout.isItalic),
                "monospace" to JsonPrimitive(layout.isMonospace),
                "indent" to JsonPrimitive(layout.indent),
                "heading" to JsonPrimitive(layout.isHeading),
            ),
        )
    }

    private companion object {
        val EXTENSIONS = setOf("pdf", "docx")
    }
}
