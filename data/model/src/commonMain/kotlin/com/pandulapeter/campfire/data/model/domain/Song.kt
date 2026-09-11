/*
 * This file is part of Campfire.
 * Copyright (c) Pandula Péter 2017-2026.
 * https://github.com/pandulapeter/campfire
 *
 * This Source Code Form is subject to the terms of the Mozilla Public License, v. 2.0.
 * If a copy of the MPL was not distributed with this file, You can obtain one at
 * https://mozilla.org/MPL/2.0/.
 */
package com.pandulapeter.campfire.data.model.domain

/**
 * One `.cho` file in the library, as the song lists need it: the metadata read from its directives, without the text.
 * The text is loaded on demand, see `SongContentRepository`.
 */
data class Song(
    /** The file name inside the songs directory, extension included. Unique, and the identity of the song. */
    val fileName: String,
    /**
     * `{title}` (falling back to the file name without its extension) with `{subtitle}` after it in parentheses,
     * which is how a song is named everywhere in the app, see `ChordProMetadata.displayTitle`.
     */
    val title: String,
    /** `{artist}`, or an empty string. */
    val artist: String,
    /** `{key}` as written, null if the song does not declare one. */
    val key: String?,
    /** The labels the file is tagged with, in the order it lists them, see `ChordProMetadata.tags`. */
    val tags: List<String>,
    /**
     * The languages the song is sung in as lowercase ISO codes, in the order the file lists them, see
     * `ChordProMetadata.languages`. Empty for a file that says nothing about its language.
     */
    val languages: List<String>,
    val hasChords: Boolean,
    val lastModified: Long,
)
