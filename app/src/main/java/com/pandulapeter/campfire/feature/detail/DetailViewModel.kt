package com.pandulapeter.campfire.feature.detail

import android.databinding.ObservableField

/**
 * Handles events and logic for [DetailActivity].
 */
class DetailViewModel(title: String, artist: String) {

    val title = ObservableField(title)
    val artist = ObservableField(artist)
}