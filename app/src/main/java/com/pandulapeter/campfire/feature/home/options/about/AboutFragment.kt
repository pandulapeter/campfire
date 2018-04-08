package com.pandulapeter.campfire.feature.home.options.about

import android.os.Bundle
import android.view.View
import com.pandulapeter.campfire.R
import com.pandulapeter.campfire.databinding.FragmentOptionsAboutBinding
import com.pandulapeter.campfire.feature.shared.CampfireFragment
import com.pandulapeter.campfire.old.util.onEventTriggered

class AboutFragment : CampfireFragment<FragmentOptionsAboutBinding, AboutViewModel>(R.layout.fragment_options_about) {

    override val viewModel = AboutViewModel()

    override fun onViewCreated(view: View?, savedInstanceState: Bundle?) {
        viewModel.shouldErrorShowSnackbar.onEventTriggered { showSnackbar(R.string.options_about_error) }
    }
}