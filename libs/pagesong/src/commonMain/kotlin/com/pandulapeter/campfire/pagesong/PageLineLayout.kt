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

/**
 * How a document set one line, measured the way the trainer's documents were (`tools/page-model`); values outside the
 * ranges below are clamped, as they were in training.
 *
 * @param page the line's page, from 0; read up to 20.
 * @param column the line's column, from 0, or null where the page has no columns; read up to 3.
 * @param size the line's text size over the document's body size; read up to 3.
 * @param indent the line's start over the width it could use, 0 to 1.
 * @param isHeading whether the document itself marks the line as a heading (a Word style, a PDF's outline).
 */
public data class PageLineLayout(
    val page: Int,
    val column: Int?,
    val size: Float,
    val isBold: Boolean,
    val isItalic: Boolean,
    val isMonospace: Boolean,
    val indent: Float,
    val isHeading: Boolean,
)
