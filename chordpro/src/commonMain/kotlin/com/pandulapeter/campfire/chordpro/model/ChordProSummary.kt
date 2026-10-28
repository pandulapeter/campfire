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
 * whether there is a chord anywhere in it, and whether it holds sheet music. The two travel together because they are read together — a library scan
 * walks each file once, and that walk is the slowest thing the app does at start.
 */
public data class ChordProSummary(
    val metadata: ChordProMetadata,
    val hasChords: Boolean,
    /** Whether a line stands in an `abc` environment, the block the app draws as staff notation. */
    val hasSheetMusic: Boolean = false,
)
