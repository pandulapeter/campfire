package com.pandulapeter.campfire.data.source.local.implementation.model

import kotlinx.serialization.Serializable

@Serializable
internal data class SetlistEntity(
    val id: String,
    val title: String,
    val songIds: List<String>,
    val priority: Int
)
