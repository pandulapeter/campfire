/*
 * This file is part of Campfire.
 * Copyright (c) Pandula Péter 2017-2026.
 * https://github.com/pandulapeter/campfire
 *
 * This Source Code Form is subject to the terms of the Mozilla Public License, v. 2.0.
 * If a copy of the MPL was not distributed with this file, You can obtain one at
 * https://mozilla.org/MPL/2.0/.
 */
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
    /**
     * The labels the song was filed under, one per `{tag}` directive (or per `{meta: tag ...}`, which ChordPro
     * documents as the same thing). They are kept in the order the file lists them, without the duplicates a hand
     * written file may hold: two spellings of the same word are the same tag, and the first one wins.
     */
    val tags: List<String> = emptyList(),
    val custom: Map<String, List<String>> = emptyMap(), // {meta: name value} and unknown x_* directives, in order
)
