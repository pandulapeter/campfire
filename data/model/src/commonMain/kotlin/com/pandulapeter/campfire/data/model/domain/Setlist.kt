package com.pandulapeter.campfire.data.model.domain

data class Setlist(
    val id: String,
    val title: String,
    val songIds: List<String>,
    val priority: Int
)