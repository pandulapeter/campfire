/*
 * This file is part of Campfire.
 * Copyright (c) Pandula Péter 2017-2026.
 * https://github.com/pandulapeter/campfire
 *
 * This Source Code Form is subject to the terms of the Mozilla Public License, v. 2.0.
 * If a copy of the MPL was not distributed with this file, You can obtain one at
 * https://mozilla.org/MPL/2.0/.
 */
package com.pandulapeter.campfire.data.source.remote.api.model

/**
 * The query parameters of a redirect the service sent the user back with: `code` and `state` when the user agreed,
 * `error` when they did not.
 *
 * Written out rather than taken from a URL library because the four platforms have four different ones, and this
 * is the only URL Campfire ever has to pick apart. Percent escapes are decoded, since an `error_description` is
 * prose and arrives encoded. A `+` is a space only in `error` and `error_description`: OAuth percent-encodes its
 * redirect parameters, but Dropbox form-encodes those two, and a `+` inside a `code` or a `state` is a character of
 * the value that the exchange would fail without.
 */
fun redirectParameters(uri: String): Map<String, String> = uri.substringAfter('?', "")
    .substringBefore('#')
    .split('&')
    .mapNotNull { parameter ->
        val name = parameter.substringBefore('=', "")
        val value = parameter.substringAfter('=', "")
        if (name.isEmpty()) null else name to value.urlDecode(isPlusSpace = name in FORM_ENCODED_PARAMETERS)
    }
    .toMap()

private val FORM_ENCODED_PARAMETERS = setOf("error", "error_description")

private fun String.urlDecode(isPlusSpace: Boolean): String {
    if (!contains('%') && !(isPlusSpace && contains('+'))) return this
    val bytes = ArrayList<Byte>(length)
    var index = 0
    while (index < length) {
        val character = this[index]
        when {
            character == '%' && index + 2 < length ->
                substring(index + 1, index + 3).toIntOrNull(radix = 16)
                    ?.also { bytes.add(it.toByte()) }
                    ?.let { index += 3 }
                    // Not an escape after all, so the percent sign is just a character.
                    ?: run {
                        bytes.add(character.code.toByte())
                        index++
                    }

            // A form encoded query writes a space this way, and Dropbox's error descriptions are form encoded.
            isPlusSpace && character == '+' -> {
                bytes.add(' '.code.toByte())
                index++
            }

            else -> {
                character.toString().encodeToByteArray().forEach(bytes::add)
                index++
            }
        }
    }
    return bytes.toByteArray().decodeToString()
}
