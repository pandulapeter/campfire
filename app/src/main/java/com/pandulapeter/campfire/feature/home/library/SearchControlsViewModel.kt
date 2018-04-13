package com.pandulapeter.campfire.feature.home.library

import android.databinding.ObservableBoolean

class SearchControlsViewModel {

    val isVisible = ObservableBoolean()
    val searchInArtists = ObservableBoolean(true)
    val searchInTitles = ObservableBoolean(true)
}