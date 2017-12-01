package com.pandulapeter.campfire.feature.home.library

import android.os.Bundle
import android.support.design.widget.Snackbar
import android.support.v4.view.GravityCompat
import android.view.View
import com.pandulapeter.campfire.LibraryBinding
import com.pandulapeter.campfire.R
import com.pandulapeter.campfire.feature.home.shared.songlistfragment.SongListFragment
import com.pandulapeter.campfire.util.addDrawerListener
import com.pandulapeter.campfire.util.hideKeyboard
import com.pandulapeter.campfire.util.onEventTriggered
import com.pandulapeter.campfire.util.onPropertyChanged

/**
 * Displays the list of all available songs from the backend. The list is searchable and filterable
 * and contains headers. The list is also cached locally and automatically updated after a period or
 * manually using the pull-to-refresh gesture. Songs that have already been downloaded are displayed
 * differently.
 *
 * Controlled by [LibraryViewModel].
 */
class LibraryFragment : SongListFragment<LibraryBinding, LibraryViewModel>(R.layout.fragment_library) {

    override fun createViewModel() = LibraryViewModel(callbacks, songInfoRepository, {
        binding.drawerLayout.openDrawer(GravityCompat.END)
        hideKeyboard(activity?.currentFocus)
    })

    override fun getRecyclerView() = binding.recyclerView

    //TODO: Add error state for incorrect downloads.
    //TODO: Add no-results state for the case when everything is filtered out.
    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        // Set up the side navigation drawer.
        binding.drawerLayout.addDrawerListener(onDrawerStateChanged = { hideKeyboard(activity?.currentFocus) })
        // Initialize the pull-to-refresh functionality.
        binding.swipeRefreshLayout.run {
            setOnRefreshListener { viewModel.forceRefresh() }
            isRefreshing = viewModel.isLoading.get()
            viewModel.isLoading.onPropertyChanged { isRefreshing = it }
        }
        // Set up error handling.
        viewModel.shouldShowErrorSnackbar.onEventTriggered {
            Snackbar
                .make(binding.root, R.string.something_went_wrong, Snackbar.LENGTH_LONG)
                .setAction(R.string.try_again, { viewModel.forceRefresh() })
                .show()
        }
        // Set up list item action listeners.
        viewModel.adapter.itemActionClickListener = { position ->
            viewModel.adapter.items[position].let { viewModel.addOrRemoveSongFromDownloads(it.songInfo) }
        }
    }

    override fun onBackPressed(): Boolean {
        if (binding.drawerLayout.isDrawerOpen(GravityCompat.END)) {
            binding.drawerLayout.closeDrawers()
            return true
        }
        if (viewModel.searchInputVisible.get()) {
            viewModel.searchInputVisible.set(false)
            return true
        }
        return false
    }
}