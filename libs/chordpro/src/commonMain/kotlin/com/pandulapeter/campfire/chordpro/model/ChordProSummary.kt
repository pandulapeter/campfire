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
 * Everything a song list needs to know about a file without keeping its text: the directives that describe it,
 * whether there is a chord anywhere in it, whether it holds sheet music, and the library files it names. They travel together because they are read together — a library scan
 * walks each file once, and that walk is the slowest thing the app does at start.
 */
public data class ChordProSummary(
    val metadata: ChordProMetadata,
    val hasChords: Boolean,
    /** Whether an `abc` environment holds a tune (an `X:` line), which the app draws as staff notation. */
    val hasSheetMusic: Boolean = false,
    /**
     * Every file an image directive names, read generously: the drawn ones, asset definitions, ones with a selector, ones
     * in an environment handed to another program, and ones on a `#` line. The same set as
     * `ChordProImages.referencedSources`, since it decides which of the library's pictures a song keeps.
     */
    val imageSources: Set<String> = emptySet(),
    /**
     * Every file an attachment directive names, read as generously as [imageSources]: the ones in [ChordProMetadata.attachments],
     * and those on a `#` line or in an environment handed to another program.
     */
    val attachmentSources: Set<String> = emptySet(),
) {
    /** Every library file the song keeps, the same set as `libraryFilesNamedBy` of its text. */
    val librarySources: Set<String> get() = imageSources + attachmentSources
}
