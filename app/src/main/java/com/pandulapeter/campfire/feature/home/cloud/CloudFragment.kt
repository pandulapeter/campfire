package com.pandulapeter.campfire.feature.home.cloud

import android.content.Intent
import android.os.Bundle
import android.support.design.widget.Snackbar
import android.support.v7.app.AppCompatActivity
import android.support.v7.widget.LinearLayoutManager
import android.view.View
import com.pandulapeter.campfire.CloudBinding
import com.pandulapeter.campfire.R
import com.pandulapeter.campfire.feature.detail.DetailActivity
import com.pandulapeter.campfire.feature.home.shared.HomeFragment
import com.pandulapeter.campfire.feature.home.shared.SpacesItemDecoration
import com.pandulapeter.campfire.util.dimension
import com.pandulapeter.campfire.util.onEventTriggered
import com.pandulapeter.campfire.util.onPropertyChanged

/**
 * Displays the list of all available songs from the backend. The list is searchable and filterable
 * and contains headers. The list is also cached locally and automatically updated after a period or
 * manually using the pull-to-refresh gesture.
 *
 * Controlled by [CloudViewModel].
 */
class CloudFragment : HomeFragment<CloudBinding, CloudViewModel>(R.layout.fragment_cloud) {

    override val viewModel by lazy { CloudViewModel(callbacks, songInfoRepository) }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        (activity as? AppCompatActivity)?.setSupportActionBar(binding.toolbar)
        context?.let { context ->
            // Initialize the list and pull-to-refresh functionality.
            //TODO: Hide the keyboard on scroll events.
            binding.recyclerView.layoutManager = LinearLayoutManager(context)
            binding.recyclerView.addItemDecoration(SpacesItemDecoration(context.dimension(R.dimen.content_padding)))
            binding.swipeRefreshLayout.setOnRefreshListener { viewModel.update(true) }
            viewModel.isLoading.onPropertyChanged { binding.swipeRefreshLayout.isRefreshing = it }
            // Setup list item click listeners.
            viewModel.adapter.itemClickListener = { position ->
                viewModel.adapter.items[position].songInfo.let { songInfo ->
                    startActivityForResult(DetailActivity.getStartIntent(context, songInfo.id, songInfo.title, songInfo.artist), DETAIL_REQUEST)
                }
            }
            viewModel.adapter.itemActionClickListener = { position ->
                viewModel.adapter.items[position].let { viewModel.addOrRemoveSongFromDownloaded(it.songInfo.id) }
            }
            // Setup error handling.
            viewModel.shouldShowErrorSnackbar.onEventTriggered {
                Snackbar
                    .make(binding.root, R.string.something_went_wrong, Snackbar.LENGTH_LONG)
                    .setAction(R.string.try_again, { viewModel.update(true) })
                    .show()
            }
        }
    }

    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) = when (requestCode) {
        DETAIL_REQUEST -> viewModel.update(false)
        else -> super.onActivityResult(requestCode, resultCode, data)
    }

    override fun isSearchInputVisible() = binding.searchTitle.searchInputVisible

    override fun closeSearchInput() {
        binding.searchTitle.searchInputVisible = false
    }

    companion object {
        private const val DETAIL_REQUEST = 100
    }
}