package com.pandulapeter.campfire.feature.home.managePlaylists

import android.os.Bundle
import android.support.v7.widget.LinearLayoutManager
import android.view.View
import com.pandulapeter.campfire.R
import com.pandulapeter.campfire.databinding.FragmentManagePlaylistsBinding
import com.pandulapeter.campfire.feature.shared.TopLevelFragment
import com.pandulapeter.campfire.util.drawable
import com.pandulapeter.campfire.util.onPropertyChanged
import com.pandulapeter.campfire.util.visibleOrGone

class ManagePlaylistsFragment : TopLevelFragment<FragmentManagePlaylistsBinding, ManagePlaylistsViewModel>(R.layout.fragment_manage_playlists) {

    override val viewModel = ManagePlaylistsViewModel()
    private val deleteAllButton by lazy {
        mainActivity.toolbarContext.createToolbarButton(R.drawable.ic_delete_24dp) { showSnackbar(R.string.work_in_progress) }.apply {
            visibleOrGone = false
        }
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        defaultToolbar.updateToolbarTitle(R.string.home_manage_playlists)
        mainActivity.updateToolbarButtons(
            listOf(deleteAllButton)
        )
        mainActivity.updateFloatingActionButtonDrawable(mainActivity.drawable(R.drawable.ic_add_24dp))
        mainActivity.enableFloatingActionButton()
        binding.recyclerView.run {
            setHasFixedSize(true)
            layoutManager = LinearLayoutManager(mainActivity)
        }
        viewModel.shouldShowDeleteAllButton.onPropertyChanged { deleteAllButton.visibleOrGone = it }
    }

    override fun onFloatingActionButtonPressed() = showSnackbar(R.string.work_in_progress)
}