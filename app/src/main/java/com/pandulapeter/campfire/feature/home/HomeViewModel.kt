package com.pandulapeter.campfire.feature.home

import android.databinding.ObservableField
import com.pandulapeter.campfire.data.networking.NetworkingManager
import com.pandulapeter.campfire.util.enqueue

/**
 * Handles events and logic for [HomeActivity].
 */
class HomeViewModel(networkingManager: NetworkingManager) {

    val text = ObservableField("Loading...")

    init {
        networkingManager.getService().getLibrary().enqueue(
            onSuccess = { text.set("Success") },
            onFailure = { text.set("Error") })
    }
}