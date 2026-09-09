package com.pandulapeter.campfire.chordpro.model

/**
 * The metadata directives of a song. Everything is optional; fallbacks (such as the file name becoming the title) are
 * the caller's job, not the parser's.
 */
data class ChordProMetadata(
    val title: String? = null, // {title} / {t}
    val subtitle: String? = null, // {subtitle} / {st}
    val artist: String? = null, // {artist}
    val composer: String? = null,
    val lyricist: String? = null,
    val album: String? = null,
    val year: String? = null,
    val key: String? = null, // {key}, kept as written, e.g. "Am" or "Bb"
    val capo: Int? = null,
    val tempo: String? = null, // {tempo}
    val time: String? = null, // {time}, e.g. "3/4"
    val duration: String? = null,
    val transpose: Int = 0, // {transpose: N}, applied by the renderer on top of the user's transposition
    val custom: Map<String, List<String>> = emptyMap() // {meta: name value} and unknown x_* directives, in order
)
