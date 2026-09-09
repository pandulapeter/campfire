package com.pandulapeter.campfire.data.model.domain

/**
 * One `.cho` file in the library, as the song lists need it: the metadata read from its directives, without the text.
 * The text is loaded on demand, see `SongContentRepository`.
 */
data class Song(
    /** The file name inside the songs directory, extension included. Unique, and the identity of the song. */
    val fileName: String,
    /** `{title}`, falling back to the file name without its extension. */
    val title: String,
    /** `{artist}`, falling back to `{subtitle}`, then to an empty string. */
    val artist: String,
    /** `{key}` as written, null if the song does not declare one. */
    val key: String?,
    val hasChords: Boolean,
    val lastModified: Long
)
