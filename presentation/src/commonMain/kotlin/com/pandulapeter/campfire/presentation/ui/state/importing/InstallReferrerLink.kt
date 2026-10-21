/*
 * This file is part of Campfire.
 * Copyright (c) Pandula Péter 2017-2026.
 * https://github.com/pandulapeter/campfire
 *
 * This Source Code Form is subject to the terms of the Mozilla Public License, v. 2.0.
 * If a copy of the MPL was not distributed with this file, You can obtain one at
 * https://mozilla.org/MPL/2.0/.
 */
package com.pandulapeter.campfire.presentation.ui.state.importing

import com.pandulapeter.campfire.data.model.share.SharedLink
import com.pandulapeter.campfire.data.model.share.ShareLinks

/**
 * The Campfire link an installation came from, read from the referrer the store hands the app on its first launch
 * (Play's install referrer), or null for every other referrer: a campaign's, or Play's own `utm_source=google-play`.
 *
 * The landing page sends the store `referrer=<encoded "campfire_link=<payload>">`, which the store decodes once, so the
 * payload arrives as the link carries it, its title still percent-encoded. The referrer is read as a query, so that
 * parameters the landing page or the store add beside it change nothing; only the payload's own grammar is trusted.
 */
internal fun installReferrerLink(referrer: String): SharedLink? = referrer.split('&').firstNotNullOfOrNull { parameter ->
    parameter.takeIf { it.startsWith(LINK_PARAMETER) }?.let { ShareLinks.parse(it.removePrefix(LINK_PARAMETER)) }
}

private const val LINK_PARAMETER = "campfire_link="
