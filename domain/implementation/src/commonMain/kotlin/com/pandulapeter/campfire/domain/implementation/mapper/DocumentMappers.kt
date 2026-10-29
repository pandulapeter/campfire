/*
 * This file is part of Campfire.
 * Copyright (c) Pandula Péter 2017-2026.
 * https://github.com/pandulapeter/campfire
 *
 * This Source Code Form is subject to the terms of the Mozilla Public License, v. 2.0.
 * If a copy of the MPL was not distributed with this file, You can obtain one at
 * https://mozilla.org/MPL/2.0/.
 */
package com.pandulapeter.campfire.domain.implementation.mapper

import com.pandulapeter.campfire.chordpro.convert.ChordSheet
import com.pandulapeter.campfire.data.model.importing.ExtractedDocument

internal fun ExtractedDocument.toChordSheet() = ChordSheet(
    pages = pages.map { page ->
        ChordSheet.Page(page.lines.map { line ->
            ChordSheet.Line(line.spans.map { it.toChordSheetSpan() }, line.isHeading, line.baseline)
        })
    },
    isCampfireExport = isCampfireExport,
)

private fun ExtractedDocument.Span.toChordSheetSpan(): ChordSheet.Span =
    ChordSheet.Span(text, start, end, size, isBold, isMonospace, isRaised, isItalic, pieces.map { it.toChordSheetSpan() })
