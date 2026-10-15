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
import org.jetbrains.compose.resources.StringResource

/** What the clipboard held when it was asked for text. */
internal sealed interface ClipboardText {

    /** The text, never blank. */
    data class Text(val value: String) : ClipboardText

    /** Nothing, or nothing that is text: an image, a file, or on Android a clip that is only an address of content. */
    data object None : ClipboardText

    /**
     * The platform held text and would not hand it over - iOS's Allow Paste answered no, a browser's permission or Paste
     * callout refused - and [message] says what the user can do about it there.
     */
    data class Refused(val message: StringResource) : ClipboardText
}

/** Reads the clipboard once, when asked: never on its own, since on iOS and the web reading it can ask the user first. */
internal fun interface ClipboardTextReader {
    suspend fun read(): ClipboardText
}

/**
 * The platform's clipboard as a [ClipboardTextReader]. Compose's own `Clipboard` hands over a `ClipEntry` that every
 * platform reads differently, and on the web swallows a refusal into an empty clipboard, so each platform reads it its own
 * way here.
 */
@Composable
internal expect fun rememberClipboardTextReader(): ClipboardTextReader

/** What any reader's answer is for [text] the platform handed over, which may be nothing. */
internal fun clipboardTextOf(text: String?) = text?.takeIf { it.isNotBlank() }?.let(ClipboardText::Text) ?: ClipboardText.None
