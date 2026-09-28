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
import kotlinx.serialization.Transient
import kotlinx.serialization.json.JsonObject

/**
 * The on-disk shape of a `*.setlist.json` file, as far as this version knows it. Every field is defaulted so that a hand-edited or older document
 * still loads, and the songs carry their transposition so that it survives an export.
 */
@Serializable
internal data class SetlistDocument(
    val title: String = "",
    val description: String = "",
    /** An ISO date (`2026-09-28`), kept as text so that one written by hand that does not read as a date loses only itself. */
    val date: String? = null,
    /**
     * The place in the list older versions gave a setlist, which the date took over. Still declared, so that it is
     * read and dropped rather than carried along as a field this version does not know: two copies of one setlist
     * that differ only in it are the same setlist.
     */
    val priority: Int = 0,
    val isArchived: Boolean = false,
    val songs: List<SetlistSongDocument> = emptyList(),
    /** What the file held besides the fields above, filled and written by [SetlistDocumentFormat] rather than the serializer. */
    @Transient val unknownFields: JsonObject = JsonObject(emptyMap()),
)

@Serializable
internal data class SetlistSongDocument(
    val file: String = "",
    val transposition: Int = 0,
    /** The same as [SetlistDocument.unknownFields], for one entry. */
    @Transient val unknownFields: JsonObject = JsonObject(emptyMap()),
)
