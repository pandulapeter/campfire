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

import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import com.pandulapeter.campfire.presentation.resources.Res
import com.pandulapeter.campfire.presentation.resources.paste_refused_ios
import platform.UIKit.UIPasteboard

/**
 * `hasStrings` is asked first, since it never prompts; `string` then asks Allow Paste on iOS 16 and later, unless Paste
 * from Other Apps in the app's settings says otherwise. Text that was there and came back nil is the user's no.
 */
@Composable
internal actual fun rememberClipboardTextReader(): ClipboardTextReader = remember {
    ClipboardTextReader {
        val pasteboard = UIPasteboard.generalPasteboard
        when {
            !pasteboard.hasStrings -> ClipboardText.None
            else -> pasteboard.string?.let(::clipboardTextOf) ?: ClipboardText.Refused(Res.string.paste_refused_ios)
        }
    }
}
