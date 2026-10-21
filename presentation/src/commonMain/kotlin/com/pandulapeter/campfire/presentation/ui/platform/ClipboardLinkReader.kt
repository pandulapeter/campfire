/*
 * This file is part of Campfire.
 * Copyright (c) Pandula Péter 2017-2026.
 * https://github.com/pandulapeter/campfire
 *
 * This Source Code Form is subject to the terms of the Mozilla Public License, v. 2.0.
 * If a copy of the MPL was not distributed with this file, You can obtain one at
 * https://mozilla.org/MPL/2.0/.
 */
package com.pandulapeter.campfire.presentation.ui.platform

/**
 * A clipboard that can tell whether it seems to hold a web address without reading it, which only iOS offers (its
 * pattern detection): the first run asks it so that the onboarding can offer the link the landing page copied before
 * the App Store install, see `FirstRunController.offerClipboardLink`.
 */
internal interface ClipboardLinkReader {

    /** Whether the clipboard seems to hold a web address. Never prompts, so it may be asked without a tap. */
    suspend fun hasProbableLink(): Boolean

    /** Reads the address or the text the clipboard holds, which may prompt (Allow Paste), so only ever on a tap. */
    suspend fun read(): ClipboardText
}
