package com.pandulapeter.campfire.feature.home.options.pages

import android.os.Bundle
import android.view.View
import com.pandulapeter.campfire.R
import com.pandulapeter.campfire.data.database.PreferenceDatabase
import com.pandulapeter.campfire.databinding.FragmentSettingsPreferencesBinding
import com.pandulapeter.campfire.feature.home.options.OptionsPageFragment
import com.pandulapeter.campfire.util.onPropertyChanged
import org.koin.android.ext.android.inject

class PreferencesFragment : OptionsPageFragment<FragmentSettingsPreferencesBinding>(R.layout.fragment_settings_preferences) {

    private val preferenceDatabase by inject<PreferenceDatabase>()
    private val viewModel by lazy { PreferencesViewModel(preferenceDatabase) }

    override fun onViewCreated(view: View?, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        viewModel.shouldUseDarkTheme.onPropertyChanged(this) { activity?.recreate() }
        binding.viewModel = viewModel
    }
}