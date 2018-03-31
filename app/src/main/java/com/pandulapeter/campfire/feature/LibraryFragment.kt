package com.pandulapeter.campfire.feature

import android.os.Bundle
import android.view.View

class LibraryFragment : CampfireFragment() {

    override fun onViewCreated(view: View?, savedInstanceState: Bundle?) {
        binding.textView.text = "Library"
    }
}