package com.pandulapeter.campfire.data.source.remote.api.model

/**
 * The query parameters of a redirect the service sent the user back with: `code` and `state` when the user agreed,
 * `error` when they did not.
 *
 * Written out rather than taken from a URL library because the four platforms have four different ones, and this
 * is the only URL Campfire ever has to pick apart. Percent escapes are decoded, since an `error_description` is
 * prose and arrives encoded.
 */
fun redirectParameters(uri: String): Map<String, String> = uri.substringAfter('?', "")
    .substringBefore('#')
    .split('&')
    .mapNotNull { parameter ->
        val name = parameter.substringBefore('=', "")
        if (name.isEmpty()) null else name to parameter.substringAfter('=', "").urlDecode()
    }
    .toMap()

private fun String.urlDecode(): String {
    if (!contains('%') && !contains('+')) return this
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
            character == '+' -> {
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
