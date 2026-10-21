/*
 * This file is part of Campfire.
 * Copyright (c) Pandula Péter 2017-2026.
 * https://github.com/pandulapeter/campfire
 *
 * This Source Code Form is subject to the terms of the Mozilla Public License, v. 2.0.
 * If a copy of the MPL was not distributed with this file, You can obtain one at
 * https://mozilla.org/MPL/2.0/.
 */
package com.pandulapeter.campfire.data.model.share

/**
 * The grammar of shared links, the one place that reads and writes them:
 *
 * ```
 * https://campfire-songbook.com/s/#1.<kind>.<shareId>.<fileId>.<rlkey>.<title>
 * https://campfire-songbook.com/app/#<payload>        (the web app)
 * campfire://s/<payload>                              (the landing page's buttons)
 * <payload>                                           (the web shell, the Android install referrer)
 * ```
 *
 * The ids ride in the fragment so that no server ever logs them, and in a path in the scheme form because Android's
 * `intent://` syntax claims the `#`. The grammar is strict, since the parts end up in a download address: anything it
 * does not match is not a link. The website's landing page reads the same grammar with a hand-copied regex.
 */
object ShareLinks {

    /** The format this version writes and reads; a link of a higher version is [SharedLink.FromNewerVersion]. */
    const val VERSION = 1

    /** The landing page a shared link opens, with the payload in its fragment. */
    const val LANDING_PAGE = "https://campfire-songbook.com/s/"

    /** The most UTF-8 bytes of the title a link carries; a longer title is cut short on a character boundary. */
    const val MAX_TITLE_BYTES = 120

    /**
     * Anything longer is not a link, whatever it holds: pasted text is offered to [parse] whole, and this keeps the
     * regexes away from a page of it.
     */
    private const val MAX_ADDRESS_LENGTH = 4096

    /**
     * The forms a payload arrives in. The https forms take a query before the fragment, which the payload never uses,
     * because messengers append their own (`?fbclid=…`) to the links people send through them. The host is matched
     * whole, so a look-alike such as `campfire-songbook.com.example` is not taken for it.
     */
    private val address = Regex("""(?:https://campfire-songbook\.com/(?:s|app)/(?:\?[^#\s]*)?#|campfire://s/)?(\S+)""")

    /**
     * A version 1 payload. The title is percent-encoded UTF-8 with only the unreserved characters left bare, the `.`
     * excepted, since it separates the parts.
     */
    private val versionOnePayload = Regex(
        """1\.(song|setlist)\.([a-z2-7]{16})\.([A-Za-z0-9_-]{1,64})\.([A-Za-z0-9_-]{1,64})\.((?:[A-Za-z0-9_~-]|%[0-9A-Fa-f]{2})*)""",
    )

    /**
     * Any later version's payload, whose parts this version cannot know: only enough of it is checked to tell a link
     * from a newer Campfire from text that merely starts with a number.
     */
    private val laterVersionPayload = Regex("""([1-9][0-9]{0,5})\.[A-Za-z0-9._~%-]+""")

    /** Reads a shared link in any of its forms, or returns `null` for anything that is not one. */
    fun parse(address: String): SharedLink? {
        val trimmed = address.trim()
        if (trimmed.length > MAX_ADDRESS_LENGTH) return null
        val payload = this.address.matchEntire(trimmed)?.groupValues?.get(1) ?: return null
        versionOnePayload.matchEntire(payload)?.let { match ->
            val (kind, shareId, fileId, rlkey, title) = match.destructured
            return SharedLink.Readable(
                kind = SharedLinkKind.fromId(kind) ?: return null,
                shareId = shareId,
                fileId = fileId,
                rlkey = rlkey,
                title = decodeTitle(title) ?: return null,
            )
        }
        val version = laterVersionPayload.matchEntire(payload)?.groupValues?.get(1)?.toInt() ?: return null
        return if (version > VERSION) SharedLink.FromNewerVersion(version) else null
    }

    /**
     * The link to a shared item, with [title] cut to [MAX_TITLE_BYTES], or `null` if the Dropbox parts do not fit the
     * grammar: a link the app makes is always one it can read back.
     */
    fun create(
        kind: SharedLinkKind,
        shareId: String,
        fileId: String,
        rlkey: String,
        title: String,
    ) = parse(payload(kind, shareId, fileId, rlkey, title)) as? SharedLink.Readable

    internal fun payload(link: SharedLink.Readable) = payload(link.kind, link.shareId, link.fileId, link.rlkey, link.title)

    private fun payload(
        kind: SharedLinkKind,
        shareId: String,
        fileId: String,
        rlkey: String,
        title: String,
    ) = listOf(VERSION.toString(), kind.id, shareId, fileId, rlkey, encodeTitle(title)).joinToString(".")

    private fun encodeTitle(title: String): String {
        val bytes = title.encodeToByteArray()
        var end = minOf(bytes.size, MAX_TITLE_BYTES)
        // Backing off over continuation bytes cuts before the character the limit fell inside, never through it.
        if (end < bytes.size) {
            while (end > 0 && bytes[end].toInt() and 0xC0 == 0x80) end--
        }
        return buildString {
            for (index in 0 until end) {
                val byte = bytes[index].toInt() and 0xFF
                val char = byte.toChar()
                if (char.isUnreservedInTitle()) {
                    append(char)
                } else {
                    append('%')
                    append(HEX_DIGITS[byte shr 4])
                    append(HEX_DIGITS[byte and 0xF])
                }
            }
        }
    }

    private fun decodeTitle(encoded: String): String? {
        val bytes = ArrayList<Byte>(encoded.length)
        var index = 0
        while (index < encoded.length) {
            if (encoded[index] == '%') {
                bytes += encoded.substring(index + 1, index + 3).toInt(16).toByte()
                index += 3
            } else {
                bytes += encoded[index].code.toByte()
                index++
            }
        }
        if (bytes.size > MAX_TITLE_BYTES) return null
        return try {
            bytes.toByteArray().decodeToString(throwOnInvalidSequence = true).withoutInvisibleControls()
        } catch (_: CharacterCodingException) {
            null
        }
    }

    /**
     * The title as a screen may show it: anyone can write a link, and a line break or a right-to-left override in its
     * title would break the line it is shown on or turn the text after it around. A control character becomes a space,
     * so that the words it parted stay apart; a format character goes, except the joiners that emoji sequences and some
     * scripts are written with.
     */
    private fun String.withoutInvisibleControls() = buildString {
        for (char in this@withoutInvisibleControls) {
            when {
                char.category == CharCategory.CONTROL -> append(' ')
                char.category == CharCategory.FORMAT && char != ZERO_WIDTH_NON_JOINER && char != ZERO_WIDTH_JOINER -> Unit
                else -> append(char)
            }
        }
    }

    private fun Char.isUnreservedInTitle() =
        this in 'A'..'Z' || this in 'a'..'z' || this in '0'..'9' || this == '-' || this == '_' || this == '~'

    private const val HEX_DIGITS = "0123456789ABCDEF"
    private const val ZERO_WIDTH_NON_JOINER = '\u200C'
    private const val ZERO_WIDTH_JOINER = '\u200D'
}
