package com.pandulapeter.campfire.feature.home.downloaded

import android.databinding.DataBindingUtil
import android.os.Bundle
import android.support.v4.app.Fragment
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import com.pandulapeter.campfire.DownloadedBinding
import com.pandulapeter.campfire.R

/**
 * Displays the list of all downloaded songs. The list is searchable and filterable and contains
 * headers. The items are automatically updated after a period or manually using the pull-to-refresh
 * gesture. Items can be removed using the swipe-to-dismiss gesture.
 *
 * Controlled by [DownloadedViewModel].
 */
class DownloadedFragment : Fragment() {

    private lateinit var binding: DownloadedBinding

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View? {
        binding = DataBindingUtil.inflate(inflater, R.layout.fragment_downloaded, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        binding.viewModel = DownloadedViewModel()
    }
}