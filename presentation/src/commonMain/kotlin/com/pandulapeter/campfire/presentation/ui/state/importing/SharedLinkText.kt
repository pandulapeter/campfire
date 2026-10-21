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
 * The Campfire link a pasted, dropped, or shared text is, or null where it is anything else: a link beside other words
 * is text, as any other address is. A bare payload of the current version is strict enough to be taken as one, but a
 * later version's is told apart only by its leading number, so a text such as "2.5" is a newer link only in a link's
 * address, never on its own.
 */
internal fun sharedLinkIn(text: String): SharedLink? = when (val link = ShareLinks.parse(text)) {
    // Parsed, so a text that does not start with the version is one of the address forms.
    is SharedLink.FromNewerVersion -> link.takeUnless { text.trim().first().isDigit() }
    else -> link
}
