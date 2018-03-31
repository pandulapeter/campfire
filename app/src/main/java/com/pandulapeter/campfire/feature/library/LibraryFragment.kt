package com.pandulapeter.campfire.feature.library

import android.content.Context
import android.os.Bundle
import android.support.design.widget.Snackbar
import android.view.View
import com.pandulapeter.campfire.R
import com.pandulapeter.campfire.databinding.FragmentLibraryBinding
import com.pandulapeter.campfire.feature.CampfireFragment
import com.pandulapeter.campfire.feature.shared.widget.AppBarButton
import com.pandulapeter.campfire.util.drawable

class LibraryFragment : CampfireFragment<FragmentLibraryBinding>(R.layout.fragment_library) {

    override fun onViewCreated(view: View?, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        updateToolbarTitle(R.string.home_library)
        binding.root.setOnClickListener { mainActivity?.navigateToSettings() }
    }

    override fun inflateToolbarButtons(context: Context) = listOf<View>(
        AppBarButton(context).apply {
            setImageDrawable(context.drawable(R.drawable.ic_search_24dp))
            setOnClickListener { Snackbar.make(binding.root, R.string.campfire, Snackbar.LENGTH_SHORT).show() }
        },
        AppBarButton(context).apply {
            setImageDrawable(context.drawable(R.drawable.ic_view_options_24dp))
            setOnClickListener {}
        }
    )
}