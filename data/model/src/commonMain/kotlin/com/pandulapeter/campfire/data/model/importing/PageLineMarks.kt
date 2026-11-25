/*
 * This file is part of Campfire.
 * Copyright (c) Pandula Péter 2017-2026.
 * https://github.com/pandulapeter/campfire
 *
 * This Source Code Form is subject to the terms of the Mozilla Public License, v. 2.0.
 * If a copy of the MPL was not distributed with this file, You can obtain one at
 * https://mozilla.org/MPL/2.0/.
 */
package com.pandulapeter.campfire.data.model.importing

/**
 * What a web page's own structure says of one line of the text read from it, which the text drops: the marks the app's
 * page read (and the iOS share extension's) writes beside each line of `text.split('\n')`, for the page model
 * (`:libs:pagesong`'s `PageLineMarks`). A line the read could not align with the page, or one with nothing printed, has
 * none (null in the list).
 *
 * @param size the line's font size over the page body's.
 * @param isLink whether every visible character of the line is inside a link, a button, or a form control.
 * @param isNewBlock whether the line's nearest card differs from the previous printed line's.
 * @param isLandmark whether the line is inside a `nav`, `header`, `footer`, `aside`, or `form`.
 * @param isHeading whether the line is inside an `h1`–`h6`.
 */
data class PageLineMarks(
    val size: Float,
    val isBold: Boolean,
    val isItalic: Boolean,
    val isMonospace: Boolean,
    val isHeading: Boolean,
    val isLink: Boolean,
    val isNewBlock: Boolean,
    val isLandmark: Boolean,
)
