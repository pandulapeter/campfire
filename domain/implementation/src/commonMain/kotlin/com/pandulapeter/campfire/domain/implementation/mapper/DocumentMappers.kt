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

import com.pandulapeter.campfire.chordpro.ChordSheet
import com.pandulapeter.campfire.data.model.domain.ExtractedDocument

internal fun ExtractedDocument.toChordSheet() = ChordSheet(pages.map { page ->
    ChordSheet.Page(page.lines.map { line ->
        ChordSheet.Line(line.spans.map { span ->
            ChordSheet.Span(span.text, span.start, span.end, span.size, span.isBold, span.isMonospace, span.isRaised)
        }, line.isHeading)
    })
})
