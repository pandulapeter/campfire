package com.pandulapeter.campfire.feature.detail.page

import android.databinding.ObservableField
import com.pandulapeter.campfire.feature.shared.CampfireViewModel

/**
 * Handles events and logic for [SongPageFragment].
 */
class SongPageViewModel(id: String) : CampfireViewModel() {
    val text = ObservableField(id)
}