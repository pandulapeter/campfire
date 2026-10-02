/*
 * This file is part of Campfire.
 * Copyright (c) Pandula Péter 2017-2026.
 * https://github.com/pandulapeter/campfire
 *
 * This Source Code Form is subject to the terms of the Mozilla Public License, v. 2.0.
 * If a copy of the MPL was not distributed with this file, You can obtain one at
 * https://mozilla.org/MPL/2.0/.
 */
package com.pandulapeter.campfire.data.source.local.implementation.document

/**
 * A document-wide budget ran out, as opposed to one object being malformed. It is an [IllegalArgumentException] so that
 * every caller that reports a damaged document reports this one too, while a caller that can skip a malformed part
 * still knows that the next part would run into the same budget.
 */
internal class PdfLimitException(message: String) : IllegalArgumentException(message)

/** Like `require`, for a budget rather than for the format. */
internal inline fun requireWithinLimit(condition: Boolean, message: () -> String) {
    if (!condition) throw PdfLimitException(message())
}
