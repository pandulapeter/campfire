package com.pandulapeter.campfire.feature.home.managePlaylists

import android.os.Bundle
import android.view.View
import com.pandulapeter.campfire.R
import com.pandulapeter.campfire.databinding.FragmentManagePlaylistsBinding
import com.pandulapeter.campfire.feature.CampfireFragment
import com.pandulapeter.campfire.util.drawable

class ManagePlaylistsFragment : CampfireFragment<FragmentManagePlaylistsBinding>(R.layout.fragment_manage_playlists) {

    override var onFloatingActionButtonClicked: (() -> Unit)? = { showSnackbar("Work in progress") }

    override fun onViewCreated(view: View?, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        defaultToolbar.updateToolbarTitle(R.string.home_manage_playlists)
        mainActivity.floatingActionButton.run {
            setImageDrawable(context.drawable(R.drawable.ic_add_24dp))
            show()
        }
    }
}