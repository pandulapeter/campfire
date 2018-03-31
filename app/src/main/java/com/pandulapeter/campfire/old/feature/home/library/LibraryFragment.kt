package com.pandulapeter.campfire.old.feature.home.library

import android.os.Bundle
import android.support.v4.view.GravityCompat
import android.support.v4.widget.DrawerLayout
import android.view.View
import android.view.inputmethod.EditorInfo
import android.widget.CompoundButton
import com.pandulapeter.campfire.LibraryBinding
import com.pandulapeter.campfire.R
import com.pandulapeter.campfire.old.data.model.Playlist
import com.pandulapeter.campfire.old.data.repository.LanguageRepository
import com.pandulapeter.campfire.old.feature.MainActivity
import com.pandulapeter.campfire.old.feature.MainViewModel
import com.pandulapeter.campfire.old.feature.home.shared.songInfoList.SongInfoListFragment
import com.pandulapeter.campfire.old.feature.shared.dialog.PlaylistChooserBottomSheetFragment
import com.pandulapeter.campfire.old.integration.AppShortcutManager
import com.pandulapeter.campfire.old.util.*
import org.koin.android.ext.android.inject

/**
 * Displays the list of all available songs from the backend. The list is searchable and filterable
 * and contains headers. The list is also cached locally and automatically updated after a period or
 * manually using the pull-to-refresh gesture. Songs that have already been downloaded are displayed
 * differently.
 *
 * Controlled by [LibraryViewModel].
 */
class LibraryFragment : SongInfoListFragment<LibraryBinding, LibraryViewModel>(R.layout.fragment_library) {
    private val languageRepository by inject<LanguageRepository>()
    private val appShortcutManager by inject<AppShortcutManager>()

    override fun createViewModel() = LibraryViewModel(
        context,
        analyticsManager,
        songInfoRepository,
        downloadedSongRepository,
        appShortcutManager,
        userPreferenceRepository,
        playlistRepository,
        languageRepository
    )

    override fun getAppBarLayout() = binding.appBarLayout

    override fun getRecyclerView() = binding.recyclerView

    override fun getCoordinatorLayout() = binding.coordinatorLayout

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        // Set up the side navigation drawer.
        binding.drawerLayout.addDrawerListener(onDrawerStateChanged = { hideKeyboard(activity?.currentFocus) })
        binding.query.setOnEditorActionListener { _, actionId, _ ->
            if (actionId == EditorInfo.IME_ACTION_SEARCH) {
                activity?.currentFocus?.let { hideKeyboard(it) }
            }
            false
        }
        context?.let { binding.drawerLayout.setStatusBarBackgroundColor(it.color(R.color.primary)) }
        binding.navigationView.disableScrollbars()
        binding.navigationView.setNavigationItemSelectedListener {
            when (it.itemId) {
                R.id.downloaded_only -> consume { viewModel.shouldShowDownloadedOnly.toggle() }
                R.id.show_work_in_progress -> consume { viewModel.shouldShowWorkInProgress.toggle() }
                R.id.show_explicit -> consume { viewModel.shouldShowExplicit.toggle() }
                R.id.sort_by_popularity -> consume { if (viewModel.sortingMode.get() != LibraryViewModel.SortingMode.POPULARITY) viewModel.sortingMode.set(LibraryViewModel.SortingMode.POPULARITY) }
                R.id.sort_by_title -> consume { if (viewModel.sortingMode.get() != LibraryViewModel.SortingMode.TITLE) viewModel.sortingMode.set(LibraryViewModel.SortingMode.TITLE) }
                R.id.sort_by_artist -> consume { if (viewModel.sortingMode.get() != LibraryViewModel.SortingMode.ARTIST) viewModel.sortingMode.set(LibraryViewModel.SortingMode.ARTIST) }
                else -> consume { viewModel.languageFilters.get()?.filterKeys { language -> language.nameResource == it.itemId }?.values?.first()?.toggle() }
            }
        }
        viewModel.sortingMode.onPropertyChanged { binding.recyclerView.invalidateItemDecorations() }
        viewModel.adapter.onListUpdatedCallback = { binding.recyclerView.run { postDelayed({ smoothScrollToPosition(0) }, 500) } }
        viewModel.languageFilters.onPropertyChanged(this) {
            if (isAdded) {
                binding.navigationView.menu.findItem(R.id.filter_by_language).subMenu.run {
                    clear()
                    it.keys.toList().sortedBy { it.nameResource }.forEachIndexed { index, language ->
                        add(R.id.language_container, language.nameResource, index, language.nameResource).apply {
                            setActionView(R.layout.widget_checkbox)
                            viewModel.languageFilters.get()?.get(language)?.let { (actionView as CompoundButton).setupWithBackingField(it) }
                        }
                    }
                }
            }
        }
        (binding.navigationView.menu.findItem(R.id.downloaded_only).actionView as CompoundButton).setupWithBackingField(viewModel.shouldShowDownloadedOnly)
        (binding.navigationView.menu.findItem(R.id.show_work_in_progress).actionView as CompoundButton).setupWithBackingField(viewModel.shouldShowWorkInProgress)
        (binding.navigationView.menu.findItem(R.id.show_explicit).actionView as CompoundButton).setupWithBackingField(viewModel.shouldShowExplicit)
        (binding.navigationView.menu.findItem(R.id.sort_by_popularity).actionView as CompoundButton).setupWithBackingField(
            viewModel.sortingMode,
            LibraryViewModel.SortingMode.POPULARITY
        )
        (binding.navigationView.menu.findItem(R.id.sort_by_title).actionView as CompoundButton).setupWithBackingField(viewModel.sortingMode, LibraryViewModel.SortingMode.TITLE)
        (binding.navigationView.menu.findItem(R.id.sort_by_artist).actionView as CompoundButton).setupWithBackingField(viewModel.sortingMode, LibraryViewModel.SortingMode.ARTIST)
        // Set up keyboard handling for the search view.
        viewModel.isSearchInputVisible.onPropertyChanged(this) {
            if (it) {
                binding.query.postDelayed({
                    binding.query.requestFocus()
                    showKeyboard(binding.query)
                }, 100)
            } else {
                hideKeyboard(activity?.currentFocus)
            }
        }
        // Initialize the pull-to-refresh functionality.
        binding.swipeRefreshLayout.run {
            setOnRefreshListener { viewModel.forceRefresh() }
            isRefreshing = viewModel.isLoading.get()
            viewModel.isLoading.onPropertyChanged(this@LibraryFragment) { if (isAdded) isRefreshing = it }
        }
        // Set up error handling.
        viewModel.shouldShowErrorSnackbar.onEventTriggered(this) {
            if (isAdded) binding.coordinatorLayout.showSnackbar(R.string.library_update_error, R.string.try_again, { viewModel.forceRefresh() })
        }
        context?.let { context ->
            binding.swipeRefreshLayout.setColorSchemeColors(context.color(R.color.accent))
            // Set up the item headers.
            binding.recyclerView.addItemDecoration(object : HeaderItemDecoration(context) {

                override fun isHeader(position: Int) = position >= 0 && viewModel.isHeader(position)

                override fun getHeaderTitle(position: Int) = if (position >= 0) viewModel.getHeaderTitle(position) else ""
            })
            // Set up list item click listeners.
            viewModel.adapter.itemClickListener = {
                (activity as? MainActivity)?.setNavigationItem(MainViewModel.MainNavigationItem.Detail(viewModel.adapter.items[it].songInfo.id))
            }
            viewModel.adapter.playlistActionClickListener = { position ->
                viewModel.adapter.items[position].let { songInfoViewModel ->
                    val songId = songInfoViewModel.songInfo.id
                    if (playlistRepository.getPlaylists().size == 1) {
                        if (playlistRepository.isSongInPlaylist(Playlist.FAVORITES_ID, songId)) {
                            playlistRepository.removeSongFromPlaylist(Playlist.FAVORITES_ID, songId)
                        } else {
                            playlistRepository.addSongToPlaylist(Playlist.FAVORITES_ID, songId)
                        }
                    } else {
                        PlaylistChooserBottomSheetFragment.show(childFragmentManager, songId)
                    }
                }
            }
            viewModel.adapter.downloadActionClickListener = { position -> viewModel.adapter.items[position].let { viewModel.downloadSong(it.songInfo) } }
        }
        // Set up view options toggle.
        viewModel.shouldShowViewOptions.onEventTriggered(this) {
            binding.drawerLayout.openDrawer(GravityCompat.END)
            hideKeyboard(activity?.currentFocus)
        }
        // Disable view options if the library is empty.
        updateDrawerLockMode(viewModel.isLibraryNotEmpty.get())
        viewModel.isLibraryNotEmpty.onPropertyChanged(this) { if (isAdded) updateDrawerLockMode(it) }
    }

    override fun onStart() {
        super.onStart()
        languageRepository.subscribe(viewModel)
    }

    override fun onStop() {
        super.onStop()
        languageRepository.unsubscribe(viewModel)
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

    private fun updateDrawerLockMode(shouldAllowViewOptions: Boolean) =
        binding.drawerLayout.setDrawerLockMode(if (shouldAllowViewOptions) DrawerLayout.LOCK_MODE_UNDEFINED else DrawerLayout.LOCK_MODE_LOCKED_CLOSED)
}