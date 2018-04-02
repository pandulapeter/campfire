package com.pandulapeter.campfire.feature.home.options.pages.preferences

import android.os.Bundle
import android.view.View
import com.pandulapeter.campfire.R
import com.pandulapeter.campfire.data.database.PreferenceDatabase
import com.pandulapeter.campfire.databinding.FragmentOptionsPreferencesBinding
import com.pandulapeter.campfire.feature.CampfireFragment
import com.pandulapeter.campfire.util.onPropertyChanged
import org.koin.android.ext.android.inject

class PreferencesFragment : CampfireFragment<FragmentOptionsPreferencesBinding, PreferencesViewModel>(R.layout.fragment_options_preferences) {

    private val preferenceDatabase by inject<PreferenceDatabase>()
    override val viewModel by lazy { PreferencesViewModel(preferenceDatabase) }

    override fun onViewCreated(view: View?, savedInstanceState: Bundle?) {
        viewModel.shouldUseDarkTheme.onPropertyChanged(this) { activity?.recreate() }
    }
}