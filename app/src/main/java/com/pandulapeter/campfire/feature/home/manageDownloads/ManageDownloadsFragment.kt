package com.pandulapeter.campfire.feature.home.manageDownloads

import android.os.Bundle
import android.view.View
import com.pandulapeter.campfire.R
import com.pandulapeter.campfire.databinding.FragmentManageDownloadsBinding
import com.pandulapeter.campfire.feature.shared.TopLevelFragment

class ManageDownloadsFragment : TopLevelFragment<FragmentManageDownloadsBinding, ManageDownloadsViewModel>(R.layout.fragment_manage_downloads) {

    override val viewModel = ManageDownloadsViewModel()

    override fun onViewCreated(view: View?, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        defaultToolbar.updateToolbarTitle(R.string.home_manage_downloads)
        mainActivity.toolbarContext.let { context ->
            mainActivity.updateToolbarButtons(
                listOf(
                    context.createToolbarButton(R.drawable.ic_delete_24dp) { showSnackbar(R.string.work_in_progress) })
            )
        }
    }
}