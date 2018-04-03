package com.pandulapeter.campfire.feature.home.managePlaylists

import android.content.Context
import android.os.Bundle
import android.view.View
import com.pandulapeter.campfire.R
import com.pandulapeter.campfire.databinding.FragmentManagePlaylistsBinding
import com.pandulapeter.campfire.feature.shared.TopLevelFragment
import com.pandulapeter.campfire.util.drawable

class ManagePlaylistsFragment : TopLevelFragment<FragmentManagePlaylistsBinding, ManagePlaylistsViewModel>(R.layout.fragment_manage_playlists) {

    override val viewModel = ManagePlaylistsViewModel()

    override fun onViewCreated(view: View?, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        defaultToolbar.updateToolbarTitle(R.string.home_manage_playlists)
        mainActivity.updateFloatingActionButtonDrawable(context.drawable(R.drawable.ic_add_24dp))
        mainActivity.enableFloatingActionButton()
    }

    override fun inflateToolbarButtons(context: Context) = listOf(
        context.createToolbarButton(R.drawable.ic_delete_24dp) { showSnackbar(R.string.work_in_progress) }
    )

    override fun onFloatingActionButtonPressed() = showSnackbar(R.string.work_in_progress)
}