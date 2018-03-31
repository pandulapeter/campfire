package com.pandulapeter.campfire.feature.home.settings

import android.os.Bundle
import android.view.View
import com.pandulapeter.campfire.R
import com.pandulapeter.campfire.databinding.FragmentSettingsBinding
import com.pandulapeter.campfire.feature.CampfireFragment

class SettingsFragment : CampfireFragment<FragmentSettingsBinding>(R.layout.fragment_settings) {

    override fun onViewCreated(view: View?, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        defaultToolbar.updateToolbarTitle(R.string.home_settings)
    }
}