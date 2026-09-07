package com.pandulapeter.campfire.data.source.local.implementation.model

import kotlinx.serialization.Serializable

@Serializable
internal data class DatabaseEntity(
    val url: String,
    val name: String,
    val isEnabled: Boolean,
    val priority: Int,
    val isAddedByUser: Boolean
)
