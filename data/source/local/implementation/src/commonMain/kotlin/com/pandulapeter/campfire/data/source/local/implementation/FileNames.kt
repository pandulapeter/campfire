package com.pandulapeter.campfire.data.source.local.implementation

import com.pandulapeter.campfire.data.model.domain.LibraryFiles
import com.pandulapeter.campfire.data.source.local.implementation.storage.file.FileStorage
import com.pandulapeter.campfire.data.source.local.implementation.storage.file.StorageDirectory

internal const val SONG_EXTENSION = LibraryFiles.SONG_EXTENSION
internal const val SETLIST_EXTENSION = LibraryFiles.SETLIST_EXTENSION

/**
 * Turns arbitrary user text into something every platform accepts as a file name: the characters Windows and the
 * separators every file system reject become spaces, runs of whitespace collapse, and the result is trimmed and
 * capped. A name that ends up empty would be unopenable, so it falls back to "Untitled".
 */
internal fun sanitizeFileName(raw: String): String {
    val replaced = raw.map { if (it in FORBIDDEN_CHARACTERS || it.isISOControl()) ' ' else it }.joinToString("")
    // Trailing dots are legal on POSIX but are silently dropped by Windows, which would change the name behind the
    // user's back and break the "the file name is the identity" rule.
    val collapsed = replaced.split(' ', '\t', '\n').filter { it.isNotEmpty() }.joinToString(" ").trimEnd('.').trim()
    return if (collapsed.isEmpty()) "Untitled" else collapsed.take(MAX_BASE_NAME_LENGTH)
}

internal fun songFileName(title: String, artist: String): String {
    val base = if (artist.isBlank()) title else "$artist - $title"
    return sanitizeFileName(base) + SONG_EXTENSION
}

internal fun setlistFileName(title: String) = sanitizeFileName(title) + SETLIST_EXTENSION

/**
 * The first free variant of [desired], suffixed " (2)", " (3)"… before the extension. Nothing in the library is ever
 * overwritten implicitly, so imports and new songs land next to a name they collide with rather than replacing it.
 */
internal suspend fun FileStorage.uniqueName(directory: StorageDirectory, desired: String): String {
    if (!exists(directory, desired)) return desired
    val extension = desired.knownExtension()
    val base = desired.removeSuffix(extension)
    var index = 2
    while (true) {
        val candidate = "$base ($index)$extension"
        if (!exists(directory, candidate)) return candidate
        index++
    }
}

/**
 * ".setlist.json" has to win over ".json", so the compound extensions are matched first and only then the part after
 * the last dot.
 */
internal fun String.knownExtension() = when {
    endsWith(SETLIST_EXTENSION, ignoreCase = true) -> takeLast(SETLIST_EXTENSION.length)
    contains('.') -> substring(lastIndexOf('.'))
    else -> ""
}

internal fun String.withoutExtension() = removeSuffix(knownExtension())

private val FORBIDDEN_CHARACTERS = charArrayOf('/', '\\', ':', '*', '?', '"', '<', '>', '|')
private const val MAX_BASE_NAME_LENGTH = 120
