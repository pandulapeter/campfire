/*
 * This file is part of Campfire.
 * Copyright (c) Pandula Péter 2017-2026.
 * https://github.com/pandulapeter/campfire
 *
 * This Source Code Form is subject to the terms of the Mozilla Public License, v. 2.0.
 * If a copy of the MPL was not distributed with this file, You can obtain one at
 * https://mozilla.org/MPL/2.0/.
 */
package com.pandulapeter.campfire.data.source.local.implementation.model

import kotlinx.serialization.Serializable

/**
 * The on-disk shape of a `*.setlist.json` file. Every field is defaulted so that a hand-edited or older document
 * still loads, and the songs carry their transposition so that it survives an export.
 */
@Serializable
internal data class SetlistDocument(
    val title: String = "",
    val priority: Int = 0,
    val isArchived: Boolean = false,
    val songs: List<SetlistSongDocument> = emptyList(),
)

@Serializable
internal data class SetlistSongDocument(
    val file: String = "",
    val transposition: Int = 0,
)
