package com.pandulapeter.campfire.feature.home

import android.os.Bundle
import android.support.v4.view.GravityCompat
import android.view.View
import android.widget.TextView
import com.pandulapeter.campfire.BuildConfig
import com.pandulapeter.campfire.HomeBinding
import com.pandulapeter.campfire.R
import com.pandulapeter.campfire.data.model.Playlist
import com.pandulapeter.campfire.data.repository.PlaylistRepository
import com.pandulapeter.campfire.data.repository.UserPreferenceRepository
import com.pandulapeter.campfire.feature.home.history.HistoryFragment
import com.pandulapeter.campfire.feature.home.library.LibraryFragment
import com.pandulapeter.campfire.feature.home.playlist.PlaylistFragment
import com.pandulapeter.campfire.feature.home.settings.SettingsFragment
import com.pandulapeter.campfire.feature.home.shared.homefragment.HomeFragment
import com.pandulapeter.campfire.feature.shared.CampfireActivity
import com.pandulapeter.campfire.feature.shared.NewPlaylistDialogFragment
import com.pandulapeter.campfire.util.addDrawerListener
import com.pandulapeter.campfire.util.consume
import com.pandulapeter.campfire.util.hideKeyboard
import com.pandulapeter.campfire.util.onPropertyChanged
import javax.inject.Inject

/**
 * Displays the home screen of the app which contains the side navigation. The last selected item is persisted.
 *
 * Controlled by [HomeViewModel].
 */
class HomeActivity : CampfireActivity<HomeBinding, HomeViewModel>(R.layout.activity_home), HomeFragment.HomeCallbacks {
    @Inject lateinit var userPreferenceRepository: UserPreferenceRepository
    @Inject lateinit var playlistRepository: PlaylistRepository
    override val viewModel by lazy { HomeViewModel(userPreferenceRepository, playlistRepository) }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        // Set up the side navigation drawer.
        (binding.navigationView.getHeaderView(0).findViewById<View>(R.id.version) as? TextView)?.text = getString(R.string.home_version_pattern, BuildConfig.VERSION_NAME)
        binding.navigationView.setNavigationItemSelectedListener { menuItem ->
            when (menuItem.itemId) {
                R.id.library -> consumeAndCloseDrawer { replaceActiveFragment(HomeViewModel.NavigationItem.Library) }
                R.id.history -> consumeAndCloseDrawer { replaceActiveFragment(HomeViewModel.NavigationItem.History) }
                R.id.settings -> consumeAndCloseDrawer { replaceActiveFragment(HomeViewModel.NavigationItem.Settings) }
                R.id.playlists -> {
                    NewPlaylistDialogFragment.show(supportFragmentManager)
                    false
                }
                else -> consumeAndCloseDrawer {
                    binding.navigationView.setCheckedItem(menuItem.itemId)
                    replaceActiveFragment(HomeViewModel.NavigationItem.Playlist(menuItem.itemId))
                }
            }
        }
        binding.drawerLayout.addDrawerListener(onDrawerStateChanged = {
            currentFocus?.clearFocus()
            hideKeyboard(currentFocus)
        })
        setCheckedItem(viewModel.navigationItem)
        viewModel.playlists.onPropertyChanged {
            binding.navigationView.menu.findItem(R.id.playlists).subMenu.run {
                clear()
                it.sortedBy { it.id }.forEachIndexed { index, playlist ->
                    add(
                        R.id.playlist_container,
                        playlist.id,
                        index,
                        (playlist as? Playlist.Custom)?.title ?: getString(R.string.home_favorites)).apply {
                        setIcon(R.drawable.ic_playlist_24dp)
                    }
                }
                add(
                    R.id.playlist_container,
                    R.id.playlists,
                    it.size,
                    R.string.home_new_playlist).apply {
                    setIcon(R.drawable.ic_new_playlist_24dp)
                }
                setGroupCheckable(R.id.playlist_container, true, true)
                updateCheckedItem()
            }
        }
    }

    override fun onStart() {
        super.onStart()
        playlistRepository.subscribe(viewModel)
    }

    override fun onStop() {
        super.onStop()
        playlistRepository.unsubscribe(viewModel)
    }

    override fun onBackPressed() {
        if (binding.drawerLayout.isDrawerOpen(GravityCompat.START)) {
            binding.drawerLayout.closeDrawers()
        } else {
            if (getCurrentFragment()?.onBackPressed() != true) {
                super.onBackPressed()
            }
        }
    }

    override fun showMenu() {
        binding.drawerLayout.openDrawer(GravityCompat.START)
        hideKeyboard(currentFocus)
    }

    fun setCheckedItem(navigationItem: HomeViewModel.NavigationItem) {
        updateCheckedItem()
        replaceActiveFragment(navigationItem)
    }

    private fun updateCheckedItem() = viewModel.navigationItem.let {
        binding.navigationView.setCheckedItem(when (it) {
            HomeViewModel.NavigationItem.Library -> R.id.library
            HomeViewModel.NavigationItem.History -> R.id.history
            HomeViewModel.NavigationItem.Settings -> R.id.settings
            is HomeViewModel.NavigationItem.Playlist -> it.id
        })
    }

    /**
     * Checks if the user actually changed the current selection and if so, persists it. Replaces the Fragment if
     * the selection changed or the container was empty.
     */
    private fun replaceActiveFragment(navigationItem: HomeViewModel.NavigationItem) {
        if (viewModel.navigationItem != navigationItem || getCurrentFragment() == null) {
            viewModel.navigationItem = navigationItem
            supportFragmentManager.beginTransaction().replace(R.id.fragment_container, when (navigationItem) {
                HomeViewModel.NavigationItem.Library -> LibraryFragment()
                HomeViewModel.NavigationItem.History -> HistoryFragment()
                HomeViewModel.NavigationItem.Settings -> SettingsFragment()
                is HomeViewModel.NavigationItem.Playlist -> PlaylistFragment.newInstance(navigationItem.id)
            }).commit()
        }
    }

    private fun getCurrentFragment() = supportFragmentManager.findFragmentById(R.id.fragment_container) as? HomeFragment<*, *>

    private fun consumeAndCloseDrawer(action: () -> Unit) = consume {
        action()
        binding.drawerLayout.closeDrawer(GravityCompat.START)
    }
}