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

import android.content.ClipboardManager
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.platform.LocalContext

/**
 * The description is asked first, since it reads nothing and the system shows no "pasted from your clipboard" toast
 * for it: a clip with no text in it is answered without reading it. A clip that is only an address of content has no
 * text of its own and is nothing to paste - resolving it would read a file the user never picked. Android only ever
 * refuses an app in the background, and this is a tap.
 */
@Composable
internal actual fun rememberClipboardTextReader(): ClipboardTextReader {
    val context = LocalContext.current
    return remember(context) {
        val clipboard = context.getSystemService(ClipboardManager::class.java)
        ClipboardTextReader {
            if (clipboard?.primaryClipDescription?.hasMimeType("text/*") != true) {
                ClipboardText.None
            } else {
                clipboardTextOf(clipboard.primaryClip?.takeIf { it.itemCount > 0 }?.getItemAt(0)?.text?.toString())
            }
        }
    }
}
