package com.pandulapeter.campfire.feature.home.managePlaylists

import android.os.Bundle
import android.support.v7.widget.LinearLayoutManager
import android.view.View
import com.pandulapeter.campfire.R
import com.pandulapeter.campfire.databinding.FragmentManagePlaylistsBinding
import com.pandulapeter.campfire.feature.shared.TopLevelFragment
import com.pandulapeter.campfire.feature.shared.dialog.AlertDialogFragment
import com.pandulapeter.campfire.feature.shared.dialog.NewPlaylistDialogFragment
import com.pandulapeter.campfire.util.drawable
import com.pandulapeter.campfire.util.onPropertyChanged
import com.pandulapeter.campfire.util.visibleOrGone

class ManagePlaylistsFragment : TopLevelFragment<FragmentManagePlaylistsBinding, ManagePlaylistsViewModel>(R.layout.fragment_manage_playlists),
    AlertDialogFragment.OnDialogItemsSelectedListener {

    companion object {
        private const val DIALOG_ID_DELETE_ALL_CONFIRMATION = 6
    }

    override val viewModel = ManagePlaylistsViewModel()
    private val deleteAllButton by lazy {
        mainActivity.toolbarContext.createToolbarButton(R.drawable.ic_delete_24dp) {
            AlertDialogFragment.show(
                DIALOG_ID_DELETE_ALL_CONFIRMATION,
                childFragmentManager,
                R.string.manage_playlists_delete_all_confirmation_title,
                R.string.manage_playlists_delete_all_confirmation_message,
                R.string.manage_playlists_delete_all_confirmation_clear,
                R.string.cancel
            )
        }.apply {
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

    override fun onFloatingActionButtonPressed() = NewPlaylistDialogFragment.show(childFragmentManager)

    override fun onPositiveButtonSelected(id: Int) {
        if (id == DIALOG_ID_DELETE_ALL_CONFIRMATION) {
            viewModel.deleteAllPlaylists()
            showSnackbar(R.string.manage_playlists_all_playlists_deleted)
        }
    }
}