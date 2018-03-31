package com.pandulapeter.campfire.feature.settings

import android.os.Bundle
import android.view.View
import com.pandulapeter.campfire.R
import com.pandulapeter.campfire.SettingsBinding
import com.pandulapeter.campfire.feature.CampfireFragment

class SettingsFragment : CampfireFragment<SettingsBinding>(R.layout.fragment_settings) {

    override var onFloatingActionButtonClicked: (() -> Unit)? = { binding.root.makeSnackbar("Work in progress").show() }

    override fun onViewCreated(view: View?, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        defaultToolbar.updateToolbarTitle(R.string.home_settings)
    }
}