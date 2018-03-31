package com.pandulapeter.campfire.feature.library

import android.os.Bundle
import android.view.View
import com.pandulapeter.campfire.R
import com.pandulapeter.campfire.databinding.FragmentLibraryBinding
import com.pandulapeter.campfire.feature.CampfireFragment

class LibraryFragment : CampfireFragment<FragmentLibraryBinding>(R.layout.fragment_library) {

    override fun onViewCreated(view: View?, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        updateToolbarTitle(R.string.home_library,"test")
        binding.root.setOnClickListener { mainActivity?.navigateToSettings() }
    }
}