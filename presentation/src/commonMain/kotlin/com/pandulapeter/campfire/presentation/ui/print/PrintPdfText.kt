/*
 * This file is part of Campfire.
 * Copyright (c) Pandula Péter 2017-2026.
 * https://github.com/pandulapeter/campfire
 *
 * This Source Code Form is subject to the terms of the Mozilla Public License, v. 2.0.
 * If a copy of the MPL was not distributed with this file, You can obtain one at
 * https://mozilla.org/MPL/2.0/.
 */
package com.pandulapeter.campfire.presentation.ui.print

/**
 * A shaped character or cluster's selection rectangle, in PDF points from the page's top left. [isRtl] marks a cluster
 * of a right-to-left bidi run, whose clusters follow one another leftwards.
 */
internal data class PrintPdfText(
    val text: String,
    val x: Float,
    val y: Float,
    val width: Float,
    val height: Float,
    val style: PrintStyle,
    val run: Int = 0,
    val isRtl: Boolean = false,
)

/**
 * The index ranges of the consecutive [glyphs] of one run that hold any right-to-left cluster. The whole run is taken
 * rather than its right-to-left clusters alone: a number or a Latin word inside Hebrew or Arabic resolves to a
 * left-to-right bidi run of its own, and a reader that orders the pieces by position would put them back in visual order.
 */
internal fun rtlRuns(glyphs: List<PrintPdfText>): List<IntRange> = buildList {
    var start = 0
    while (start < glyphs.size) {
        var end = start
        while (end + 1 < glyphs.size && glyphs[end + 1].run == glyphs[start].run) end++
        if ((start..end).any { glyphs[it].isRtl }) add(start..end)
        start = end + 1
    }
}

/** UTF-16BE without a BOM: PDF titles add one, whereas ToUnicode destinations must not. */
internal fun printUtf16Hex(text: String) = buildString {
    text.forEach { append(it.code.toString(16).padStart(4, '0')) }
}

/** PDF numbers cannot use the exponent notation Float.toString uses for very small advances. */
internal fun printPdfNumber(value: Float): String {
    require(value.isFinite() && kotlin.math.abs(value) < 1_000_000)
    val scaled = kotlin.math.round(value * 1000).toInt()
    val magnitude = kotlin.math.abs(scaled)
    return (if (scaled < 0) "-" else "") + (magnitude / 1000).toString() +
        if (magnitude % 1000 == 0) "" else "." + (magnitude % 1000).toString().padStart(3, '0').trimEnd('0')
}
