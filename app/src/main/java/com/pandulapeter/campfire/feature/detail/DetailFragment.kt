package com.pandulapeter.campfire.feature.detail

import android.content.Context
import android.os.Bundle
import android.transition.Slide
import android.view.Gravity
import android.view.View
import com.pandulapeter.campfire.R
import com.pandulapeter.campfire.databinding.FragmentDetailBinding
import com.pandulapeter.campfire.feature.CampfireFragment

class DetailFragment : CampfireFragment<FragmentDetailBinding>(R.layout.fragment_detail) {
    override var onFloatingActionButtonClicked: (() -> Unit)? = { showSnackbar("Work in progress") }
    override val navigationMenu = R.menu.detail

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enterTransition = Slide(Gravity.BOTTOM)
    }

    override fun onViewCreated(view: View?, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        defaultToolbar.updateToolbarTitle("Detail")
        mainActivity.transformMainToolbarButton(true)
    }

    override fun inflateToolbarButtons(context: Context) = listOf<View>(
        context.createToolbarButton(R.drawable.ic_song_options_24dp) { mainActivity.openSecondaryNavigationDrawer() }
    )
}