package com.pandulapeter.campfire.feature.home.manageDownloads

import android.os.Bundle
import android.view.View
import com.pandulapeter.campfire.R
import com.pandulapeter.campfire.feature.home.shared.SongListFragment
import com.pandulapeter.campfire.feature.shared.dialog.AlertDialogFragment

class ManageDownloadsFragment : SongListFragment<ManageDownloadsViewModel>(), AlertDialogFragment.OnDialogItemsSelectedListener {

    companion object {
        private const val DIALOG_ID_DELETE_ALL_CONFIRMATION = 4
    }

    override val viewModel = ManageDownloadsViewModel()

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        defaultToolbar.updateToolbarTitle(R.string.home_manage_downloads)
        mainActivity.toolbarContext.let { context ->
            mainActivity.updateToolbarButtons(listOf(context.createToolbarButton(R.drawable.ic_delete_24dp) {
                AlertDialogFragment.show(
                    DIALOG_ID_DELETE_ALL_CONFIRMATION,
                    childFragmentManager,
                    R.string.manage_downloads_delete_all_confirmation_title,
                    R.string.manage_downloads_delete_all_confirmation_message,
                    R.string.manage_downloads_delete_all_confirmation_clear,
                    R.string.cancel
                )
            }))
        }
    }

    override fun onPositiveButtonSelected(id: Int) {
        if (id == DIALOG_ID_DELETE_ALL_CONFIRMATION) {
            viewModel.deleteAllSongs()
        }
    }
}