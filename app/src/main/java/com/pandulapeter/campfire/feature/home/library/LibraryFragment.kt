package com.pandulapeter.campfire.feature.home.library

import android.databinding.ObservableBoolean
import android.os.Bundle
import android.support.design.widget.Snackbar
import android.support.v4.view.GravityCompat
import android.support.v7.widget.SwitchCompat
import android.view.View
import android.widget.CompoundButton
import android.widget.RadioButton
import com.pandulapeter.campfire.LibraryBinding
import com.pandulapeter.campfire.R
import com.pandulapeter.campfire.data.model.SongInfo
import com.pandulapeter.campfire.feature.home.shared.songlistfragment.SongListFragment
import com.pandulapeter.campfire.util.*

/**
 * Displays the list of all available songs from the backend. The list is searchable and filterable
 * and contains headers. The list is also cached locally and automatically updated after a period or
 * manually using the pull-to-refresh gesture. Songs that have already been downloaded are displayed
 * differently.
 *
 * Controlled by [LibraryViewModel].
 */
class LibraryFragment : SongListFragment<LibraryBinding, LibraryViewModel>(R.layout.fragment_library), SongOptionsFragment.SongActionListener {

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
        binding.navigationView.setNavigationItemSelectedListener {
            when (it.itemId) {
                R.id.downloaded_only -> consume { viewModel.shouldShowDownloadedOnly.set(!viewModel.shouldShowDownloadedOnly.get()) }
                R.id.sort_by_title -> consume { if (!viewModel.isSortedByTitle.get()) viewModel.isSortedByTitle.set(true) }
                R.id.sort_by_artist -> consume { if (viewModel.isSortedByTitle.get()) viewModel.isSortedByTitle.set(false) }
                else -> false
            }
        }
        (binding.navigationView.menu.findItem(R.id.downloaded_only).actionView as SwitchCompat).setupWithBackingField(viewModel.shouldShowDownloadedOnly)
        (binding.navigationView.menu.findItem(R.id.sort_by_title).actionView as RadioButton).setupWithBackingField(viewModel.isSortedByTitle)
        // Set up keyboard handling for the search view.
        viewModel.isSearchInputVisible.onPropertyChanged {
            if (it) {
                binding.query.post {
                    binding.query.requestFocus()
                    showKeyboard(binding.query)
                }
            } else {
                hideKeyboard(activity?.currentFocus)
            }
        }
        (binding.navigationView.menu.findItem(R.id.sort_by_artist).actionView as RadioButton).setupWithBackingField(viewModel.isSortedByTitle, true)
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
            viewModel.adapter.items[position].songInfo.let { songInfo ->
                val fragment = SongOptionsFragment.newInstance(songInfo)
                fragment.show(childFragmentManager, fragment.tag)
            }
        }
    }

    override fun onBackPressed(): Boolean {
        if (binding.drawerLayout.isDrawerOpen(GravityCompat.END)) {
            binding.drawerLayout.closeDrawers()
            return true
        }
        if (viewModel.isSearchInputVisible.get()) {
            viewModel.isSearchInputVisible.set(false)
            return true
        }
        return false
    }

    override fun onSongAction(songInfo: SongInfo, songAction: SongOptionsFragment.SongAction) = when (songAction) {
        SongOptionsFragment.SongAction.RemoveFromDownloads -> viewModel.addOrRemoveSongFromDownloads(songInfo)
        SongOptionsFragment.SongAction.NewPlaylist -> {
            Snackbar.make(binding.root, R.string.work_in_progress, Snackbar.LENGTH_LONG).show()
        }
        is SongOptionsFragment.SongAction.AddToPlaylist -> {
            //TODO: Add / remove Playlist
        }
    }

    private fun CompoundButton.setupWithBackingField(backingField: ObservableBoolean, shouldNegate: Boolean = false) {
        //TODO: There is a shorter solution using logical operators, don't be lazy.
        if (shouldNegate) {
            isChecked = !backingField.get()
            setOnCheckedChangeListener { _, isChecked -> backingField.set(!isChecked) }
            backingField.onPropertyChanged { isChecked = !it }
        } else {
            isChecked = backingField.get()
            setOnCheckedChangeListener { _, isChecked -> backingField.set(isChecked) }
            backingField.onPropertyChanged { isChecked = it }
        }
    }
}