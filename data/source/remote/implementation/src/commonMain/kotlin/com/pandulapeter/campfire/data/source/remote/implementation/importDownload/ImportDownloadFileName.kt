/*
 * This file is part of Campfire.
 * Copyright (c) Pandula Péter 2017-2026.
 * https://github.com/pandulapeter/campfire
 *
 * This Source Code Form is subject to the terms of the Mozilla Public License, v. 2.0.
 * If a copy of the MPL was not distributed with this file, You can obtain one at
 * https://mozilla.org/MPL/2.0/.
 */
package com.pandulapeter.campfire.data.source.remote.implementation.importDownload

import io.ktor.http.decodeURLPart
import io.ktor.http.parseUrl

/**
 * The name a downloaded file is imported under: the one its `Content-Disposition` header gives (`filename*`, which may
 * hold any script, before the plain `filename`), else the last segment of the address's path, else [FALLBACK_NAME].
 * Only the last segment of whatever names it is kept, since a name is never a path in the library.
 */
internal fun importDownloadFileName(contentDisposition: String?, url: String): String {
    val parameters = contentDisposition?.let(::dispositionParameters).orEmpty()
    val extended = parameters["filename*"]?.let(::decodeExtendedValue)
    val plain = parameters["filename"]
    val fromPath = parseUrl(url)?.encodedPath?.substringAfterLast('/')?.let(::decodeOrNull)
    return sequenceOf(extended, plain, fromPath)
        .map { it?.substringAfterLast('/')?.substringAfterLast('\\')?.trim() }
        .firstOrNull { !it.isNullOrEmpty() && it != "." && it != ".." }
        ?: FALLBACK_NAME
}

private const val FALLBACK_NAME = "download"

/** The parameters of a header value such as `attachment; filename="a b.zip"`, by their lowercase names. */
private fun dispositionParameters(header: String): Map<String, String> {
    val parameters = mutableMapOf<String, String>()
    var index = header.indexOf(';').takeIf { it >= 0 } ?: return parameters
    while (index < header.length) {
        index++
        val equals = header.indexOf('=', index).takeIf { it >= 0 } ?: break
        val name = header.substring(index, equals).trim().lowercase()
        var position = equals + 1
        while (position < header.length && header[position] == ' ') position++
        val value = StringBuilder()
        if (position < header.length && header[position] == '"') {
            position++
            while (position < header.length && header[position] != '"') {
                if (header[position] == '\\' && position + 1 < header.length) position++
                value.append(header[position])
                position++
            }
            position = header.indexOf(';', position).takeIf { it >= 0 } ?: header.length
        } else {
            val end = header.indexOf(';', position).takeIf { it >= 0 } ?: header.length
            value.append(header.substring(position, end).trim())
            position = end
        }
        parameters.getOrPut(name) { value.toString() }
        index = position
    }
    return parameters
}

/** An RFC 8187 value, `UTF-8''n%C3%A9v.zip`: only UTF-8 is decoded, which is the one charset every client must send. */
private fun decodeExtendedValue(value: String): String? {
    val parts = value.split('\'', limit = 3)
    if (parts.size != 3 || !parts[0].equals("UTF-8", ignoreCase = true)) return null
    return decodeOrNull(parts[2])
}

private fun decodeOrNull(encoded: String) = try {
    encoded.decodeURLPart()
} catch (exception: Exception) {
    null
}
