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

/** How many numbers [lineFeaturesOf] gives a line: `FEATURES` in `tools/page-model/lines.py`. */
internal const val FEATURE_COUNT = 22

/**
 * The line's numbers beside its characters, in `lines.py`'s `features` order and with its clamps: what the line reads as,
 * its length, and how it is set: a document line's layout, or a page line's [PageLineMarks]. Computed in doubles, as
 * Python does, before the model's floats.
 */
internal fun lineFeaturesOf(line: PageLine, source: PageLineSource): FloatArray {
    val text = visibleTextOf(line.text)
    val codePoints = text.codePoints
    var start = 0
    while (start < codePoints.size && isSpace(codePoints[start])) start++
    var end = codePoints.size
    while (end > start && isSpace(codePoints[end - 1])) end--
    val stripped = LineText(codePoints.copyOfRange(start, end))
    val length = stripped.codePoints.size
    val letters = stripped.codePoints.count(::isLetter)
    val values = doubleArrayOf(
        flag(source == PageLineSource.DOCUMENT),
        flag(length == 0),
        flag(LineRules.isChordRow(stripped.text)),
        flag(LineRules.isTab(stripped.text)),
        flag(LineRules.isHeading(stripped)),
        flag(LineRules.isLabelled(stripped.text)),
        minOf(length, 200) / 200.0,
        letters.toDouble() / maxOf(length, 1),
        start.toDouble() / maxOf(codePoints.size, 1),
        flag(LineRules.hasAddress(stripped.text)),
        flag(LineRules.hasDigit(stripped.text)),
    ) + if (source == PageLineSource.DOCUMENT) layoutFeaturesOf(line.layout ?: BODY_TEXT) else marksFeaturesOf(line.marks)
    return FloatArray(values.size) { values[it].toFloat() }
}

/** A document line's layout, then three zeros: a document has no links, cards, or landmarks to mark. */
private fun layoutFeaturesOf(layout: PageLineLayout) = doubleArrayOf(
    minOf(layout.size.toDouble(), 3.0) / 3.0,
    flag(layout.isBold),
    flag(layout.isItalic),
    flag(layout.isMonospace),
    layout.indent.toDouble().coerceIn(0.0, 1.0),
    ((layout.column ?: -1) + 1) / 4.0,
    minOf(layout.page, 20) / 20.0,
    flag(layout.isHeading),
    0.0,
    0.0,
    0.0,
)

/**
 * A page line's marks in the layout inputs that say the same of a document (never a page, column, or indent), then the
 * three only a page has. A line without marks has every one zero, as training saw each marked page a second time without
 * them, and the source flag tells pages and documents apart.
 */
private fun marksFeaturesOf(marks: PageLineMarks?) = if (marks == null) {
    DoubleArray(11)
} else {
    doubleArrayOf(
        minOf(marks.size.toDouble(), 3.0) / 3.0,
        flag(marks.isBold),
        flag(marks.isItalic),
        flag(marks.isMonospace),
        0.0,
        0.0,
        0.0,
        flag(marks.isHeading),
        flag(marks.isLink),
        flag(marks.isNewBlock),
        flag(marks.isLandmark),
    )
}

/** What a document line with no layout is read as: `features`'s defaults. */
private val BODY_TEXT = PageLineLayout(
    page = 0,
    column = null,
    size = 1f,
    isBold = false,
    isItalic = false,
    isMonospace = false,
    indent = 0f,
    isHeading = false,
)

private fun flag(value: Boolean) = if (value) 1.0 else 0.0
