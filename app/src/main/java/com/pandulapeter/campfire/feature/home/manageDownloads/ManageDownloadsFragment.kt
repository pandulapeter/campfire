package com.pandulapeter.campfire.feature.home.manageDownloads

import android.content.Context
import android.os.Bundle
import android.view.View
import com.pandulapeter.campfire.R
import com.pandulapeter.campfire.databinding.FragmentManageDownloadsBinding
import com.pandulapeter.campfire.feature.CampfireFragment

class ManageDownloadsFragment : CampfireFragment<FragmentManageDownloadsBinding>(R.layout.fragment_manage_downloads) {

    override fun onViewCreated(view: View?, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        defaultToolbar.updateToolbarTitle(R.string.home_manage_downloads)
    }

    override fun inflateToolbarButtons(context: Context) = listOf<View>(
        context.createToolbarButton(R.drawable.ic_delete_24dp) { showSnackbar("Work in progress") }
    )
}