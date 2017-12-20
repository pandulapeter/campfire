package com.pandulapeter.campfire.feature.home.managedownloads

import com.pandulapeter.campfire.ManageDownloadsBinding
import com.pandulapeter.campfire.R
import com.pandulapeter.campfire.feature.home.shared.homefragment.HomeFragment

/**
 * Allows the user to delete downloaded songs.
 *
 * Controlled by [ManageDownloadsViewModel].
 */
class ManageDownloadsFragment : HomeFragment<ManageDownloadsBinding, ManageDownloadsViewModel>(R.layout.fragment_manage_downloads) {

    override fun createViewModel() = ManageDownloadsViewModel(callbacks)
}