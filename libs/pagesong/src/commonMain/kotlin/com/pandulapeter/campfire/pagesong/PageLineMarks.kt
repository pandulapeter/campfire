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
 * What a web page's own structure says of one line of its text, which the text alone drops: the structure marks the
 * app's page read writes beside it (`PAGE_SNAPSHOT_SCRIPT`, `tools/page-model`'s `lines.py`). Only for a line the read
 * aligned with the page; a line it could not align, or one with nothing printed, has none.
 *
 * @param size the line's font size over the page body's; read up to 3.
 * @param isHeading whether the line is inside an `h1`–`h6`.
 * @param isLink whether every visible character of the line is inside a link, a button, or a form control.
 * @param isNewBlock whether the line's nearest card (an `article`, a `section`, a block with its own border or
 * background…) differs from the previous printed line's.
 * @param isLandmark whether the line is inside a `nav`, `header`, `footer`, `aside`, or `form` (or their ARIA roles).
 */
public data class PageLineMarks(
    val size: Float,
    val isBold: Boolean,
    val isItalic: Boolean,
    val isMonospace: Boolean,
    val isHeading: Boolean,
    val isLink: Boolean,
    val isNewBlock: Boolean,
    val isLandmark: Boolean,
)
