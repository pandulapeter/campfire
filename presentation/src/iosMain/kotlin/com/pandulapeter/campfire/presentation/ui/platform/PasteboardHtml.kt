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

import kotlinx.cinterop.BetaInteropApi
import kotlinx.cinterop.ExperimentalForeignApi
import platform.Foundation.NSData
import platform.Foundation.NSPropertyListImmutable
import platform.Foundation.NSPropertyListSerialization
import platform.Foundation.NSString
import platform.Foundation.NSStringEncoding
import platform.Foundation.NSUTF16StringEncoding
import platform.Foundation.NSUTF8StringEncoding
import platform.Foundation.create
import platform.UIKit.UIPasteboard

/**
 * The HTML a browser's copy puts on the pasteboard beside its text, read for the chords a page draws with CSS
 * ([pastedText]): `public.html`, or the web archive Safari may put there instead, whose main resource is the HTML.
 */
internal object PasteboardHtml {

    /** Whether there is HTML to read, asked without reading it, which never brings up Allow Paste. */
    fun isOn(pasteboard: UIPasteboard) = pasteboard.containsPasteboardTypes(listOf(HTML) + WEB_ARCHIVES)

    /**
     * The first item's text and HTML as [clipboardTextOf] answers them, read in one access (`items`), since each access
     * may ask Allow Paste; nothing back is the user's no, and null.
     */
    fun read(pasteboard: UIPasteboard): ClipboardText? {
        val item = pasteboard.items.firstOrNull() as? Map<*, *> ?: return null
        val html = item[HTML]?.let(::textOf) ?: WEB_ARCHIVES.firstNotNullOfOrNull { type -> (item[type] as? NSData)?.let(::webArchiveHtml) }
        val text = TEXTS.firstNotNullOfOrNull { type -> item[type]?.let(::textOf) }
            ?: UTF16_TEXTS.firstNotNullOfOrNull { type -> item[type]?.let { textOf(it, NSUTF16StringEncoding) } }
        return clipboardTextOf(text = text, html = html)
    }

    @OptIn(BetaInteropApi::class)
    private fun textOf(value: Any, encoding: NSStringEncoding = NSUTF8StringEncoding): String? = when (value) {
        is String -> value
        is NSData -> NSString.create(data = value, encoding = encoding)?.toString()
        else -> null
    }

    @OptIn(ExperimentalForeignApi::class)
    private fun webArchiveHtml(data: NSData): String? {
        val archive = NSPropertyListSerialization.propertyListWithData(data, NSPropertyListImmutable, null, null) as? Map<*, *>
        val resource = archive?.get("WebMainResource") as? Map<*, *>
        return (resource?.get("WebResourceData") as? NSData)?.let(::textOf)
    }

    private const val HTML = "public.html"
    private val WEB_ARCHIVES = listOf("com.apple.webarchive", "Apple Web Archive pasteboard type")
    private val TEXTS = listOf("public.utf8-plain-text", "public.plain-text", "public.text")

    /** The text some apps put on the pasteboard as UTF-16 alone, which `UIPasteboard.string` read as well as the rest. */
    private val UTF16_TEXTS = listOf("public.utf16-plain-text", "public.utf16-external-plain-text")
}
