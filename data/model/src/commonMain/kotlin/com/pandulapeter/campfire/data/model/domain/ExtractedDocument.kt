/*
 * This file is part of Campfire.
 * Copyright (c) Pandula Péter 2017-2026.
 * https://github.com/pandulapeter/campfire
 *
 * This Source Code Form is subject to the terms of the Mozilla Public License, v. 2.0.
 * If a copy of the MPL was not distributed with this file, You can obtain one at
 * https://mozilla.org/MPL/2.0/.
 */
package com.pandulapeter.campfire.data.model.domain

/** Text only: document bytes and document-specific structures never enter the library. */
data class ExtractedDocument(val pages: List<Page>) {
    data class Page(val lines: List<Line>)
    data class Line(val spans: List<Span>, val isHeading: Boolean = false)
    data class Span(
        val text: String,
        val start: Double,
        val end: Double,
        val size: Double,
        val isBold: Boolean = false,
        val isMonospace: Boolean = false,
        val isRaised: Boolean = false,
    )
}
