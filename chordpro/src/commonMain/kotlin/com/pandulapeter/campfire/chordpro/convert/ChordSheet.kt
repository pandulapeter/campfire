/*
 * This file is part of Campfire.
 * Copyright (c) Pandula Péter 2017-2026.
 * https://github.com/pandulapeter/campfire
 *
 * This Source Code Form is subject to the terms of the Mozilla Public License, v. 2.0.
 * If a copy of the MPL was not distributed with this file, You can obtain one at
 * https://mozilla.org/MPL/2.0/.
 */
package com.pandulapeter.campfire.chordpro.convert

/** Positioned text in arbitrary, consistent units. No document format crosses this boundary. */
public data class ChordSheet(val pages: List<Page>, val originalText: String? = null) {
    public data class Page(val lines: List<Line>)
    public data class Line(val spans: List<Span>, val isHeading: Boolean = false)
    public data class Span(
        val text: String,
        val start: Double,
        val end: Double,
        val size: Double = 1.0,
        val isBold: Boolean = false,
        val isMonospace: Boolean = false,
        val isRaised: Boolean = false,
    )

    public companion object {
        public fun ofPlainText(text: String): ChordSheet = ChordSheet(
            pages = listOf(Page(text.replace("\r\n", "\n").replace('\r', '\n').split('\n').map { line ->
                var column = 0
                val spans = mutableListOf<Span>()
                var run = StringBuilder()
                var start = 0
                fun flush() {
                    if (run.isNotEmpty()) spans += Span(run.toString(), start.toDouble(), column.toDouble(), isMonospace = true)
                    run = StringBuilder()
                }
                for (character in line) {
                    if (character.isWhitespace() || character == '\u00a0') {
                        flush()
                        column = if (character == '\t') (column / 8 + 1) * 8 else column + 1
                    } else {
                        if (run.isEmpty()) start = column
                        run.append(character)
                        column++
                    }
                }
                flush()
                Line(spans)
            })),
            originalText = text,
        )
    }
}
