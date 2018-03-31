package com.pandulapeter.campfire.feature

import android.os.Bundle
import android.view.View

class SettingsFragment : CampfireFragment() {

    override fun onViewCreated(view: View?, savedInstanceState: Bundle?) {
        binding.textView.text = "Settings"
    }
}