/*
 * This file is part of Campfire.
 * Copyright (c) Pandula Péter 2017-2026.
 * https://github.com/pandulapeter/campfire
 *
 * This Source Code Form is subject to the terms of the Mozilla Public License, v. 2.0.
 * If a copy of the MPL was not distributed with this file, You can obtain one at
 * https://mozilla.org/MPL/2.0/.
 */
package com.pandulapeter.campfire.data.source.remote.implementation.dropbox

import com.pandulapeter.campfire.data.source.remote.api.sync.model.SharedLinkResult

/**
 * The two parts of a Dropbox file link that a download needs, from an address of the form
 * `https://www.dropbox.com/scl/fi/<fileId>/<name>?rlkey=<rlkey>&dl=0`, or null for any other form: an older `/s/` link
 * has no `rlkey`, and a link of a shape nobody has seen cannot be turned into a download address with any confidence.
 * The name and the other parameters (`dl`, `st`) are dropped, since a download goes by the two parts alone. Each part is
 * held to the alphabet Dropbox was seen to use, because both end up in a Campfire link whose grammar is strict.
 */
internal fun parseDropboxSharedLink(url: String): SharedLinkResult.Created? {
    val match = SHARED_LINK.matchEntire(url) ?: return null
    val rlkey = match.groupValues[2].split('&').firstOrNull { it.startsWith(RLKEY_PARAMETER) }?.removePrefix(RLKEY_PARAMETER)
    if (rlkey == null || !LINK_PART.matches(rlkey)) return null
    return SharedLinkResult.Created(fileId = match.groupValues[1], rlkey = rlkey)
}

private val LINK_PART = Regex("[A-Za-z0-9_-]{1,64}")
private val SHARED_LINK = Regex("""https://(?:www\.)?dropbox\.com/scl/fi/(${LINK_PART.pattern})(?:/[^?#]*)?\?([^#]*)""")
private const val RLKEY_PARAMETER = "rlkey="
