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

import com.pandulapeter.campfire.presentation.resources.Res
import com.pandulapeter.campfire.presentation.resources.paste_refused_ios
import kotlinx.coroutines.suspendCancellableCoroutine
import platform.UIKit.UIPasteboard
import platform.UIKit.UIPasteboardDetectionPatternProbableWebURL
import kotlin.coroutines.resume

/**
 * The general pasteboard, asked through `detectPatterns`, which tells whether it seems to hold a web address without
 * handing over its contents and so without the Allow Paste prompt. The read asks for the address when the pasteboard
 * holds one (Copy Link) and for the text otherwise (the landing page's `navigator.clipboard.writeText`), never both, so
 * that a tap prompts at most once. Contents that were there and came back nil are the user's no.
 */
internal object IosClipboardLinkReader : ClipboardLinkReader {

    override suspend fun hasProbableLink() = suspendCancellableCoroutine { continuation ->
        UIPasteboard.generalPasteboard.detectPatternsForPatterns(setOf(UIPasteboardDetectionPatternProbableWebURL)) { patterns, _ ->
            if (continuation.isActive) continuation.resume(patterns?.contains(UIPasteboardDetectionPatternProbableWebURL) == true)
        }
    }

    override suspend fun read(): ClipboardText {
        val pasteboard = UIPasteboard.generalPasteboard
        val contents = when {
            pasteboard.hasURLs -> pasteboard.URL?.absoluteString
            pasteboard.hasStrings -> pasteboard.string
            else -> return ClipboardText.None
        }
        return contents?.let(::clipboardTextOf) ?: ClipboardText.Refused(Res.string.paste_refused_ios)
    }
}
