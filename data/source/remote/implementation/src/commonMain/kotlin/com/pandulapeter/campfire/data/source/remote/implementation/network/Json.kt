package com.pandulapeter.campfire.data.source.remote.implementation.network

/**
 * A JSON string literal that is safe to put in an HTTP header.
 *
 * Dropbox takes the arguments of its upload and download calls in a `Dropbox-API-Arg` header, and a header may only
 * carry ASCII - so every character above it becomes a `\uXXXX` escape rather than travelling as UTF-8. Song files
 * are named after their titles, so this is the ordinary case here, not an edge one.
 */
internal fun String.toAsciiJsonString() = buildString(length + 2) {
    append('"')
    this@toAsciiJsonString.forEach { character ->
        when {
            character == '"' -> append("\\\"")
            character == '\\' -> append("\\\\")
            character == '\n' -> append("\\n")
            character == '\r' -> append("\\r")
            character == '\t' -> append("\\t")
            character.code < 0x20 || character.code > 0x7E -> append("\\u").append(character.code.toHexPadded())
            else -> append(character)
        }
    }
    append('"')
}

/** Percent encoding for a query parameter value, over the UTF-8 bytes of the string. */
internal fun String.urlEncode() = buildString {
    encodeToByteArray().forEach { byte ->
        val value = byte.toInt() and 0xFF
        val character = value.toChar()
        if (character.isLetterOrDigit() && value < 0x80 || character in UNRESERVED_CHARACTERS) {
            append(character)
        } else {
            append('%').append(value.toHexPadded(length = 2).uppercase())
        }
    }
}

private fun Int.toHexPadded(length: Int = 4) = toString(radix = 16).padStart(length, '0')

private const val UNRESERVED_CHARACTERS = "-_.~"
