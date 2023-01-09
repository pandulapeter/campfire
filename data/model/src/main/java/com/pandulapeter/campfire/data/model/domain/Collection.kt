package com.pandulapeter.campfire.data.model.domain

data class Collection(
    val id: String,
    val title: String,
    val description: String,
    val thumbnailUrl: String,
    val songIds: List<String>,
    val isPublic: Boolean
)