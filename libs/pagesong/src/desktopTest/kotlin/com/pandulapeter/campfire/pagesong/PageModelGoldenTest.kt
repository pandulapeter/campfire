/*
 * This file is part of Campfire.
 * Copyright (c) Pandula Péter 2017-2026.
 * https://github.com/pandulapeter/campfire
 *
 * This Source Code Form is subject to the terms of the Mozilla Public License, v. 2.0.
 * If a copy of the MPL was not distributed with this file, You can obtain one at
 * https://mozilla.org/MPL/2.0/.
 */
package com.pandulapeter.campfire.pagesong

import kotlin.math.abs
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * The library against what the trainer's PyTorch side computed for the same lines (`tools/page-model/export.py
 * --fixtures`): the hash, the features, and a tiny random model's logits.
 */
class PageModelGoldenTest {

    @Test
    fun `every line's characters hash to the trainer's buckets`() = forEachLine { example, index, line ->
        assertEquals(
            example.codes[index].toList(),
            hashedCodesOf(line.text, buckets = APP_BUCKETS, maxCharacters = MAX_CHARACTERS).toList(),
            line.text,
        )
    }

    @Test
    fun `every line's features are the trainer's`() = forEachLine { example, index, line ->
        val features = lineFeaturesOf(line, example.source)
        example.features[index].forEachIndexed { feature, expected ->
            assertTrue(abs(features[feature] - expected) < 1e-5, "feature $feature of ${line.text.debug()}: ${features[feature]}, not $expected")
        }
    }

    @Test
    fun `the tiny model's logits are PyTorch's for a page and a document`() {
        val pageSong = PageSong(resource("tiny.bin"))
        for (example in examples) {
            val logits = pageSong.logitsOf(example.lines, example.source)
            assertEquals(example.logits.size, logits.size)
            logits.forEachIndexed { index, row ->
                row.forEachIndexed { label, value ->
                    val expected = example.logits[index][label]
                    assertTrue(abs(value - expected) < 1e-3, "line $index (${example.lines[index].text.debug()}) label $label: $value, not $expected")
                }
            }
        }
    }

    private fun forEachLine(check: (Example, Int, PageLine) -> Unit) {
        for (example in examples) example.lines.forEachIndexed { index, line -> check(example, index, line) }
    }

    private fun String.debug() = map { if (it.code < 0x20 || it.code > 0x7E) "\\u%04x".format(it.code) else "$it" }.joinToString("")

    private class Example(
        val source: PageLineSource,
        val lines: List<PageLine>,
        val codes: List<IntArray>,
        val features: List<FloatArray>,
        val logits: List<FloatArray>,
    )

    private companion object {
        const val APP_BUCKETS = 4096
        const val MAX_CHARACTERS = 160

        fun resource(name: String) = requireNotNull(PageModelGoldenTest::class.java.getResource("/$name")) { name }.readBytes()

        @Suppress("UNCHECKED_CAST")
        val examples = ((GoldenJson.parse(resource("golden.json").decodeToString()) as Map<String, Any?>)["examples"] as List<Map<String, Any?>>)
            .map { example ->
                val source = if (example["source"] == "document") PageLineSource.DOCUMENT else PageLineSource.PAGE
                Example(
                    source = source,
                    lines = (example["lines"] as List<Map<String, Any?>>).map(::lineOf),
                    codes = (example["codes"] as List<List<Double>>).map { row -> IntArray(row.size) { row[it].toInt() } },
                    features = (example["features"] as List<List<Double>>).map(::floatsOf),
                    logits = (example["logits"] as List<List<Double>>).map(::floatsOf),
                )
            }

        fun floatsOf(row: List<Double>) = FloatArray(row.size) { row[it].toFloat() }

        fun lineOf(line: Map<String, Any?>) = PageLine(
            text = line["text"] as String,
            layout = (line["page"] as Double?)?.let { page ->
                PageLineLayout(
                    page = page.toInt(),
                    column = (line["column"] as Double).toInt().takeIf { it >= 0 },
                    size = (line["size"] as Double).toFloat(),
                    isBold = line["bold"] as Boolean,
                    isItalic = line["italic"] as Boolean,
                    isMonospace = line["monospace"] as Boolean,
                    indent = (line["indent"] as Double).toFloat(),
                    isHeading = line["heading"] as Boolean,
                )
            },
        )
    }
}
