package com.pandulapeter.campfire.feature.home

import android.databinding.ObservableBoolean
import android.databinding.ObservableField

/**
 * Handles events and logic for [HomeActivity].
 */
class HomeViewModel {
    val selectedItem: ObservableField<NavigationItem> = ObservableField(NavigationItem.CLOUD)
    val isSortedByTitle = ObservableBoolean()
}