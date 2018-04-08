package com.pandulapeter.campfire.feature.detail

import android.databinding.ObservableField
import com.pandulapeter.campfire.feature.shared.CampfireViewModel

class DetailViewModel(id: String) : CampfireViewModel() {

    val songId = ObservableField(id)
}