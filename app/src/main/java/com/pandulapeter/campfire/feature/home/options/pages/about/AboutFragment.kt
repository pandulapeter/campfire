package com.pandulapeter.campfire.feature.home.options.pages.about

import com.pandulapeter.campfire.R
import com.pandulapeter.campfire.databinding.FragmentOptionsAboutBinding
import com.pandulapeter.campfire.feature.shared.CampfireFragment

class AboutFragment : CampfireFragment<FragmentOptionsAboutBinding, AboutViewModel>(R.layout.fragment_options_about) {

    override val viewModel = AboutViewModel()
}