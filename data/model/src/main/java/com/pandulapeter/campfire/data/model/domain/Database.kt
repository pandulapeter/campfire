package com.pandulapeter.campfire.data.model.domain

data class Database(
    val url: String,
    val name: String,
    val isActive: Boolean,
    val priority: Int,
    val isAddedByUser: Boolean
)