/*
 * This file is part of Campfire.
 * Copyright (c) Pandula Péter 2017-2026.
 * https://github.com/pandulapeter/campfire
 *
 * This Source Code Form is subject to the terms of the Mozilla Public License, v. 2.0.
 * If a copy of the MPL was not distributed with this file, You can obtain one at
 * https://mozilla.org/MPL/2.0/.
 */
package com.pandulapeter.campfire.domain.api.importing

import com.pandulapeter.campfire.data.model.importing.PageLineMarks
import com.pandulapeter.campfire.pagesong.PageLine
import com.pandulapeter.campfire.pagesong.PageLineMarks as PageModelLineMarks

/**
 * A page's [text] as the lines [PageCropper.songLinesOf] reads, split as the import splits it (`lines()`), each with its
 * [marks] only where they are the same lines' as the page read counted them: one for each line of the text split at
 * `\n`, and no other line break (a lone `\r`, which `lines()` also splits at) in it. Anything else (a paste, which has
 * none, or a text changed on its way, a source link taken off its end) is read from the text alone, which the model was
 * trained on too; a mark never lands on another line than its own.
 */
fun pageTextLinesOf(text: String, marks: List<PageLineMarks?>?): List<PageLine> {
    val lines = text.lines()
    val aligned = marks?.takeIf { it.size == lines.size && it.size == text.split('\n').size }
    return lines.mapIndexed { index, line -> PageLine(text = line, marks = aligned?.get(index)?.toPageModelLineMarks()) }
}

private fun PageLineMarks.toPageModelLineMarks() = PageModelLineMarks(
    size = size,
    isBold = isBold,
    isItalic = isItalic,
    isMonospace = isMonospace,
    isHeading = isHeading,
    isLink = isLink,
    isNewBlock = isNewBlock,
    isLandmark = isLandmark,
)
