package com.pandulapeter.campfire.feature.home.options.changelog

import android.os.Bundle
import android.view.View
import com.pandulapeter.campfire.R
import com.pandulapeter.campfire.databinding.FragmentOptionsChangelogBinding
import com.pandulapeter.campfire.feature.shared.CampfireFragment

class ChangelogFragment : CampfireFragment<FragmentOptionsChangelogBinding, ChangelogViewModel>(R.layout.fragment_options_changelog) {

    override val viewModel = ChangelogViewModel()

    override fun onViewCreated(view: View?, savedInstanceState: Bundle?) {
        binding.recyclerView.setHasFixedSize(true)
    }
}