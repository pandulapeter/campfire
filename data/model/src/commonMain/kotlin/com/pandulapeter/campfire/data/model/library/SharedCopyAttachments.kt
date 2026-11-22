/*
 * This file is part of Campfire.
 * Copyright (c) Pandula Péter 2017-2026.
 * https://github.com/pandulapeter/campfire
 *
 * This Source Code Form is subject to the terms of the Mozilla Public License, v. 2.0.
 * If a copy of the MPL was not distributed with this file, You can obtain one at
 * https://mozilla.org/MPL/2.0/.
 */
package com.pandulapeter.campfire.data.model.library

import com.pandulapeter.campfire.data.model.importing.ImportLimits

/**
 * Which attachments a shared link's copy carries ([carried]) and which it leaves out for its size ([leftOut]), see
 * [sharedCopyAttachments].
 */
class SharedCopyAttachments(
    val carried: Set<String>,
    val leftOut: Set<String>,
)

/**
 * The attachments a shared link's copy carries out of [names], the library files its songs attach and show as no picture,
 * in the order the songs name them: each in turn while it fits in what is left of [budget], one that does not left out
 * and the next tried, so a large recording does not keep the small sheet named after it out of the copy. [sizeOf] answers
 * a file's size, null for one the library does not hold, which is neither carried nor left out; [onLeftOut] hears of each
 * one left out as soon as it is, so that a caller that read it to learn its size can let it go.
 *
 * The shared copy's rule (`SharedCopyBuilder`, which builds the share's first copy and every one a sync run builds again),
 * kept here beside the limits it budgets with.
 */
inline fun sharedCopyAttachments(
    names: List<String>,
    budget: Long = ImportLimits.MAX_SHARED_ATTACHMENTS_SIZE,
    onLeftOut: (String) -> Unit = {},
    sizeOf: (String) -> Long?,
): SharedCopyAttachments {
    var remaining = budget
    val carried = linkedSetOf<String>()
    val leftOut = linkedSetOf<String>()
    names.distinct().forEach { name ->
        val size = sizeOf(name) ?: return@forEach
        if (size <= remaining) {
            carried += name
            remaining -= size
        } else {
            leftOut += name
            onLeftOut(name)
        }
    }
    return SharedCopyAttachments(carried = carried, leftOut = leftOut)
}
