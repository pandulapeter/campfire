package com.pandulapeter.campfire.feature.home

import android.os.Bundle
import android.support.v4.view.GravityCompat
import android.view.SubMenu
import android.view.View
import android.view.animation.AnimationUtils
import android.widget.TextView
import com.pandulapeter.campfire.BuildConfig
import com.pandulapeter.campfire.HomeBinding
import com.pandulapeter.campfire.R
import com.pandulapeter.campfire.data.repository.DownloadedSongRepository
import com.pandulapeter.campfire.data.repository.PlaylistRepository
import com.pandulapeter.campfire.data.repository.SongInfoRepository
import com.pandulapeter.campfire.data.repository.UserPreferenceRepository
import com.pandulapeter.campfire.feature.home.collections.CollectionsFragment
import com.pandulapeter.campfire.feature.home.history.HistoryFragment
import com.pandulapeter.campfire.feature.home.library.LibraryFragment
import com.pandulapeter.campfire.feature.home.managedownloads.ManageDownloadsFragment
import com.pandulapeter.campfire.feature.home.manageplaylists.ManagePlaylistsFragment
import com.pandulapeter.campfire.feature.home.playlist.PlaylistFragment
import com.pandulapeter.campfire.feature.home.settings.SettingsFragment
import com.pandulapeter.campfire.feature.home.shared.homefragment.HomeChildFragment
import com.pandulapeter.campfire.feature.shared.CampfireFragment
import com.pandulapeter.campfire.feature.shared.NewPlaylistDialogFragment
import com.pandulapeter.campfire.integration.AppShortcutManager
import com.pandulapeter.campfire.util.BundleArgumentDelegate
import com.pandulapeter.campfire.util.addDrawerListener
import com.pandulapeter.campfire.util.consume
import com.pandulapeter.campfire.util.disableScrollbars
import com.pandulapeter.campfire.util.hideKeyboard
import com.pandulapeter.campfire.util.onPropertyChanged
import com.pandulapeter.campfire.util.setArguments
import kotlinx.coroutines.experimental.CommonPool
import kotlinx.coroutines.experimental.android.UI
import kotlinx.coroutines.experimental.async
import kotlinx.coroutines.experimental.cancel
import javax.inject.Inject
import kotlin.coroutines.experimental.CoroutineContext

/**
 * Displays the home screen of the app which contains the side navigation. The last selected item is persisted.
 *
 * Controlled by [HomeViewModel].
 */
class HomeFragment : CampfireFragment<HomeBinding, HomeViewModel>(R.layout.fragment_home), HomeChildFragment.HomeCallbacks {
    @Inject lateinit var songInfoRepository: SongInfoRepository
    @Inject lateinit var userPreferenceRepository: UserPreferenceRepository
    @Inject lateinit var downloadedSongRepository: DownloadedSongRepository
    @Inject lateinit var playlistRepository: PlaylistRepository
    @Inject lateinit var appShortcutManager: AppShortcutManager
    override val viewModel by lazy { HomeViewModel(downloadedSongRepository, userPreferenceRepository, appShortcutManager) }
    private var coroutine: CoroutineContext? = null
    private val playlistsContainerItem by lazy { binding.navigationView.menu.findItem(R.id.playlists).subMenu }
    private val collectionsItem by lazy { binding.navigationView.menu.findItem(R.id.collections) }
    private val historyItem by lazy { binding.navigationView.menu.findItem(R.id.history) }
    private val managePlaylistsItem by lazy { binding.navigationView.menu.findItem(R.id.manage_playlists) }
    private val manageDownloadsItem by lazy { binding.navigationView.menu.findItem(R.id.manage_downloads) }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        // Handle deep link.
        arguments.homeNavigationItem.let {
            viewModel.homeNavigationItem = HomeViewModel.HomeNavigationItem.fromStringValue(it)
        }
        // Set up the side navigation drawer.
        binding.navigationView.disableScrollbars()
        (binding.navigationView.getHeaderView(0).findViewById<View>(R.id.version) as? TextView)?.text = getString(R.string.home_version_pattern, BuildConfig.VERSION_NAME)
        binding.navigationView.setNavigationItemSelectedListener { menuItem ->
            when (menuItem.itemId) {
                R.id.library -> consumeAndCloseDrawer { replaceActiveFragment(HomeViewModel.HomeNavigationItem.Library) }
                R.id.collections -> consumeAndCloseDrawer { replaceActiveFragment(HomeViewModel.HomeNavigationItem.Collections) }
                R.id.history -> consumeAndCloseDrawer { replaceActiveFragment(HomeViewModel.HomeNavigationItem.History) }
                R.id.settings -> consumeAndCloseDrawer { replaceActiveFragment(HomeViewModel.HomeNavigationItem.Settings) }
                R.id.playlists -> {
                    NewPlaylistDialogFragment.show(childFragmentManager)
                    false
                }
                R.id.manage_playlists -> consumeAndCloseDrawer { replaceActiveFragment(HomeViewModel.HomeNavigationItem.ManagePlaylists) }
                R.id.manage_downloads -> consumeAndCloseDrawer { replaceActiveFragment(HomeViewModel.HomeNavigationItem.ManageDownloads) }
                else -> consumeAndCloseDrawer {
                    binding.navigationView.setCheckedItem(menuItem.itemId)
                    replaceActiveFragment(HomeViewModel.HomeNavigationItem.Playlist(menuItem.itemId))
                }
            }
        }
        binding.drawerLayout.addDrawerListener(onDrawerStateChanged = {
            activity?.currentFocus?.clearFocus()
            hideKeyboard(activity?.currentFocus)
        })
        setCheckedItem(viewModel.homeNavigationItem)
        viewModel.playlists.onPropertyChanged {
            playlistsContainerItem.run {
                clear()
                it.sortedBy { it.id }.forEachIndexed { index, playlist -> addPlaylistItem(playlist.id, index, playlist.title ?: getString(R.string.home_favorites)) }
                addPlaylistItem(R.id.playlists, it.size, getString(R.string.home_new_playlist), true)
                setGroupCheckable(R.id.playlist_container, true, true)
                updateCheckedItem()
            }
            managePlaylistsItem.isVisible = it.size > 1
        }
        viewModel.isLibraryReady.onPropertyChanged { isLibraryReady ->
            collectionsItem.isEnabled = isLibraryReady
            historyItem.isEnabled = isLibraryReady
            playlistsContainerItem.run {
                viewModel.playlists.get()?.forEach {
                    findItem(it.id)?.isEnabled = isLibraryReady
                }
                findItem(R.id.playlists)?.isEnabled = isLibraryReady
            }
        }
        viewModel.hasDownloads.onPropertyChanged { manageDownloadsItem.isVisible = it }
    }


    override fun onStart() {
        super.onStart()
        songInfoRepository.subscribe(viewModel)
        playlistRepository.subscribe(viewModel)
        downloadedSongRepository.subscribe(viewModel)
    }

    override fun onStop() {
        super.onStop()
        songInfoRepository.unsubscribe(viewModel)
        playlistRepository.unsubscribe(viewModel)
        downloadedSongRepository.unsubscribe(viewModel)
    }

    //TODO: Handle back navigation.
//    override fun onBackPressed() {
//        if (binding.drawerLayout.isDrawerOpen(GravityCompat.START)) {
//            binding.drawerLayout.closeDrawers()
//        } else {
//            if (getCurrentFragment()?.onBackPressed() != true) {
//                super.onBackPressed()
//            }
//        }
//    }

    override fun showMenu() {
        binding.drawerLayout.openDrawer(GravityCompat.START)
        hideKeyboard(activity?.currentFocus)
    }

    fun setCheckedItem(homeNavigationItem: HomeViewModel.HomeNavigationItem) {
        replaceActiveFragment(homeNavigationItem)
        updateCheckedItem()
    }

    private fun updateCheckedItem() = viewModel.homeNavigationItem.let {
        binding.navigationView.setCheckedItem(when (it) {
            HomeViewModel.HomeNavigationItem.Library -> R.id.library
            HomeViewModel.HomeNavigationItem.Collections -> R.id.collections
            HomeViewModel.HomeNavigationItem.History -> R.id.history
            HomeViewModel.HomeNavigationItem.Settings -> R.id.settings
            is HomeViewModel.HomeNavigationItem.Playlist -> it.id
            HomeViewModel.HomeNavigationItem.ManagePlaylists -> R.id.manage_playlists
            HomeViewModel.HomeNavigationItem.ManageDownloads -> R.id.manage_downloads
        })
    }

    /**
     * Checks if the user actually changed the current selection and if so, persists it. Replaces the Fragment if
     * the selection changed or the container was empty.
     */
    private fun replaceActiveFragment(homeNavigationItem: HomeViewModel.HomeNavigationItem) {
        val currentFragment = getCurrentFragment()
        context?.let { context ->
            if (viewModel.homeNavigationItem != homeNavigationItem || currentFragment == null) {
                viewModel.homeNavigationItem = homeNavigationItem
                coroutine?.cancel()
                coroutine = async(UI) {
                    val nextFragment = async(CommonPool) {
                        when (homeNavigationItem) {
                            HomeViewModel.HomeNavigationItem.Library -> LibraryFragment()
                            HomeViewModel.HomeNavigationItem.Collections -> CollectionsFragment()
                            HomeViewModel.HomeNavigationItem.History -> HistoryFragment()
                            HomeViewModel.HomeNavigationItem.Settings -> SettingsFragment()
                            is HomeViewModel.HomeNavigationItem.Playlist -> PlaylistFragment.newInstance(homeNavigationItem.id)
                            HomeViewModel.HomeNavigationItem.ManagePlaylists -> ManagePlaylistsFragment()
                            HomeViewModel.HomeNavigationItem.ManageDownloads -> ManageDownloadsFragment()
                        }
                    }.await()
                    currentFragment?.let {
                        it.outAnimation = AnimationUtils.loadAnimation(context, android.R.anim.fade_out)
                        (nextFragment as CampfireFragment<*, *>).inAnimation = AnimationUtils.loadAnimation(context, android.R.anim.fade_in)
                    }
                    childFragmentManager.beginTransaction().replace(R.id.fragment_container, nextFragment).commit()
                }
            }
        }
    }

    private fun getCurrentFragment() = childFragmentManager.findFragmentById(R.id.fragment_container) as? HomeChildFragment<*, *>

    private fun consumeAndCloseDrawer(action: () -> Unit) = consume {
        action()
        binding.drawerLayout.closeDrawer(GravityCompat.START)
    }

    private fun SubMenu.addPlaylistItem(id: Int, index: Int, title: String, shouldUseAddIcon: Boolean = false) = add(com.pandulapeter.campfire.R.id.playlist_container, id, index, title).run {
        setIcon(if (shouldUseAddIcon) R.drawable.ic_new_playlist_24dp else R.drawable.ic_playlist_24dp)
        isEnabled = viewModel.isLibraryReady.get()
    }

    companion object {
        private var Bundle?.homeNavigationItem by BundleArgumentDelegate.String("home_navigation_item")

        fun newInstance(homeNavigationItem: HomeViewModel.HomeNavigationItem?) = HomeFragment().setArguments { it.homeNavigationItem = homeNavigationItem?.stringValue ?: "" }
    }
}