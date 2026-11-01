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

import kotlin.math.abs

/**
 * How a lyric line is set: its type and where its text starts. A sheet sets a chorus apart from the verses by either,
 * bold or italic lyrics or an inset, so where a section set apart returns to the verses' look it has ended, whatever
 * heading comes next.
 */
internal data class ChordSheetLook(val isBold: Boolean, val isItalic: Boolean, val left: Double, val size: Double) {
    /** Whether this look differs from [other] in its type. */
    fun differsInType(other: ChordSheetLook) = isBold != other.isBold || isItalic != other.isItalic

    /**
     * Whether this look differs from [other] in its type or by an inset of more than a character and a half, which
     * plain text's two spaces exceed and a lyric's own unevenness in a PDF does not.
     */
    fun isSetApartFrom(other: ChordSheetLook) = differsInType(other) || abs(left - other.left) > maxOf(size, other.size) * 1.5

    companion object {
        /** The look of [line] whose text starts at [left]; null for a line with nothing printed. */
        fun of(line: ChordSheet.Line, left: Double?): ChordSheetLook? {
            val spans = line.spans.filter { it.text.isNotBlank() }
            if (spans.isEmpty() || left == null) return null
            return ChordSheetLook(spans.all { it.isBold }, spans.all { it.isItalic }, left, spans.maxOf { it.size })
        }
    }
}
