/*
 * This file is part of Campfire.
 * Copyright (c) Pandula Péter 2017-2026.
 * https://github.com/pandulapeter/campfire
 *
 * This Source Code Form is subject to the terms of the Mozilla Public License, v. 2.0.
 * If a copy of the MPL was not distributed with this file, You can obtain one at
 * https://mozilla.org/MPL/2.0/.
 */
package com.pandulapeter.campfire.presentation.ui.dialogs

import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.layout.Layout
import androidx.compose.ui.unit.Constraints
import androidx.compose.ui.unit.dp
import com.pandulapeter.campfire.chordpro.ChordProMetadataFields.Field

/**
 * The year and the duration, side by side where both labels fit on one line in either language, and one under the
 * other on a phone, where half a sheet cuts "Duration (optional)" in two. The two short values share a row where they
 * can, each with room for its clear button and what it holds: next to the album, a year was left two digits' room once
 * the button was there. Which of the two it is is decided while measuring rather than by composing a row or a column,
 * so that crossing the width (a rotation, a resized window) keeps the focused field, and the keyboard with it.
 */
@Composable
internal fun SongMetadataShortFields(field: @Composable (Modifier, Field) -> Unit) = Layout(
    content = {
        field(Modifier.fillMaxWidth(), Field.YEAR)
        field(Modifier.fillMaxWidth(), Field.DURATION)
    },
) { measurables, constraints ->
    val gap = SHORT_FIELDS_GAP.roundToPx()
    val isSideBySide = constraints.maxWidth >= MIN_SHORT_FIELDS_ROW_WIDTH.roundToPx()
    val width = if (isSideBySide) (constraints.maxWidth - gap) / 2 else constraints.maxWidth
    val (year, duration) = measurables.map { it.measure(Constraints.fixedWidth(width)) }
    val height = if (isSideBySide) maxOf(year.height, duration.height) else year.height + gap + duration.height
    layout(constraints.maxWidth, height) {
        year.placeRelative(0, 0)
        if (isSideBySide) duration.placeRelative(width + gap, 0) else duration.placeRelative(0, year.height + gap)
    }
}

/**
 * The narrowest form that puts the year and the duration side by side: each half has to hold the longer of the two
 * languages' "Duration (optional)" on one line, next to the clear button once the field holds a value.
 */
private val MIN_SHORT_FIELDS_ROW_WIDTH = 480.dp

/** The same gap the forms leave between their other fields. */
private val SHORT_FIELDS_GAP = 8.dp
