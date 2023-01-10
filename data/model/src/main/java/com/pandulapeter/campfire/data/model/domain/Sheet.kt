package com.pandulapeter.campfire.data.model.domain

data class Sheet(
    val url: String,
    val name: String,
    val isActive: Boolean,
    val priority: Int
)