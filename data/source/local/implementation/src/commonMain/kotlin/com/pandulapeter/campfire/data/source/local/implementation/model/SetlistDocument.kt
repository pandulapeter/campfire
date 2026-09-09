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
    val songs: List<SetlistSongDocument> = emptyList()
)

@Serializable
internal data class SetlistSongDocument(
    val file: String = "",
    val transposition: Int = 0
)
