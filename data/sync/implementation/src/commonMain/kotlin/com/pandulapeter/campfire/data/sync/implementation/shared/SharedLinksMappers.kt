/*
 * This file is part of Campfire.
 * Copyright (c) Pandula Péter 2017-2026.
 * https://github.com/pandulapeter/campfire
 *
 * This Source Code Form is subject to the terms of the Mozilla Public License, v. 2.0.
 * If a copy of the MPL was not distributed with this file, You can obtain one at
 * https://mozilla.org/MPL/2.0/.
 */
package com.pandulapeter.campfire.data.sync.implementation.shared

import com.pandulapeter.campfire.data.model.share.Share
import com.pandulapeter.campfire.data.model.share.ShareLinks
import com.pandulapeter.campfire.data.model.share.ShareState
import com.pandulapeter.campfire.data.model.share.SharedLinkKind
import kotlinx.datetime.LocalDate

/**
 * The shares the screens show, most recently shared first. An entry whose parts make no readable link (hand-edited, or
 * of a kind a later version added) is left out of them, and kept in the document.
 */
internal fun SharedLinksDocument.toShares() = links
    .asReversed()
    .mapNotNull { it.toShare() }
    .sortedByDescending { it.sharedAt }

internal fun SharedLinksEntry.toShare(): Share? {
    val kind = SharedLinkKind.fromId(kind) ?: return null
    return Share(
        shareId = shareId,
        kind = kind,
        fileName = fileName,
        title = title,
        link = ShareLinks.create(kind = kind, shareId = shareId, fileId = fileId, rlkey = rlkey, title = title) ?: return null,
        sharedAt = sharedAt?.let { runCatching { LocalDate.parse(it) }.getOrNull() },
        state = when {
            copyMissing -> ShareState.COPY_MISSING
            state == SharedLinksEntry.STATE_ORPHANED -> ShareState.ORPHANED
            state == SharedLinksEntry.STATE_TOO_LARGE -> ShareState.TOO_LARGE
            else -> ShareState.OK
        },
    )
}
