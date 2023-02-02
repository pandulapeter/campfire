package com.pandulapeter.campfire.data.model.domain

data class Database(
    val url: String,
    val name: String,
    val isEnabled: Boolean,
    val priority: Int,
    val isAddedByUser: Boolean
    // TODO: Last update timestamp
)