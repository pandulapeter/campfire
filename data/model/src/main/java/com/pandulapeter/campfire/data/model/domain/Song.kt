package com.pandulapeter.campfire.data.model.domain

data class Song(
    val id: String,
    val url: String,
    val languageId: String,
    val title: String,
    val artist: String,
    val key: String,
    val isExplicit: Boolean,
    val hasChords: Boolean,
    val isPublic: Boolean
)