package com.pandulapeter.campfire.feature.home.options.pages.preferences

import android.os.Bundle
import android.view.View
import com.pandulapeter.campfire.R
import com.pandulapeter.campfire.databinding.FragmentOptionsPreferencesBinding
import com.pandulapeter.campfire.feature.shared.CampfireFragment
import com.pandulapeter.campfire.util.onPropertyChanged

class PreferencesFragment : CampfireFragment<FragmentOptionsPreferencesBinding, PreferencesViewModel>(R.layout.fragment_options_preferences) {

    override val viewModel = PreferencesViewModel()

    override fun onViewCreated(view: View?, savedInstanceState: Bundle?) {
        viewModel.shouldUseDarkTheme.onPropertyChanged(this) { activity?.recreate() }
    }
}