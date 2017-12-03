package com.pandulapeter.campfire.feature.home

import android.os.Bundle
import android.support.design.widget.Snackbar
import android.support.v4.view.GravityCompat
import android.widget.TextView
import com.pandulapeter.campfire.BuildConfig
import com.pandulapeter.campfire.HomeBinding
import com.pandulapeter.campfire.R
import com.pandulapeter.campfire.data.storage.PreferenceStorageManager
import com.pandulapeter.campfire.feature.home.library.LibraryFragment
import com.pandulapeter.campfire.feature.home.playlist.PlaylistFragment
import com.pandulapeter.campfire.feature.home.settings.SettingsFragment
import com.pandulapeter.campfire.feature.home.shared.homefragment.HomeFragment
import com.pandulapeter.campfire.feature.shared.CampfireActivity
import com.pandulapeter.campfire.util.addDrawerListener
import com.pandulapeter.campfire.util.consume
import com.pandulapeter.campfire.util.hideKeyboard
import javax.inject.Inject

/**
 * Displays the home screen of the app which contains the side navigation. The last selected item is persisted.
 *
 * Controlled by [HomeViewModel].
 */
class HomeActivity : CampfireActivity<HomeBinding, HomeViewModel>(R.layout.activity_home), HomeFragment.HomeCallbacks {
    @Inject lateinit var preferenceStorageManager: PreferenceStorageManager
    override val viewModel by lazy { HomeViewModel(preferenceStorageManager) }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        // Set up the side navigation drawer.
        binding.navigationView.getHeaderView(0).findViewById<TextView>(R.id.version)?.text = getString(R.string.home_version_pattern, BuildConfig.VERSION_NAME)
        binding.navigationView.setNavigationItemSelectedListener { menuItem ->
            when (menuItem.itemId) {
                R.id.library -> consumeAndCloseDrawer { replaceActiveFragment(HomeViewModel.NavigationItem.Library) }
                R.id.settings -> consumeAndCloseDrawer { replaceActiveFragment(HomeViewModel.NavigationItem.Settings) }
                R.id.playlist -> consumeAndCloseDrawer {
                    //TODO: Add option to select more playlists.
                    replaceActiveFragment(HomeViewModel.NavigationItem.Playlist("favorites"))
                }
                R.id.new_playlist -> {
                    getCurrentFragment()?.view?.let { Snackbar.make(it, R.string.work_in_progress, Snackbar.LENGTH_SHORT).show() }
                    binding.drawerLayout.closeDrawer(GravityCompat.START)
                    false
                }
                else -> false
            }
        }
        binding.drawerLayout.addDrawerListener(onDrawerStateChanged = { hideKeyboard(currentFocus) })
        replaceActiveFragment(viewModel.navigationItem)
        binding.navigationView.setCheckedItem(when (viewModel.navigationItem) {
            HomeViewModel.NavigationItem.Library -> R.id.library
            HomeViewModel.NavigationItem.Settings -> R.id.settings
            is HomeViewModel.NavigationItem.Playlist -> R.id.playlist //TODO: Add option to select more playlists.
        })
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

    /**
     * Checks if the user actually changed the current selection and if so, persists it. Replaces the Fragment if
     * the selection changed or the container was empty.
     */
    private fun replaceActiveFragment(navigationItem: HomeViewModel.NavigationItem) {
        if (viewModel.navigationItem != navigationItem || getCurrentFragment() == null) {
            viewModel.navigationItem = navigationItem
            supportFragmentManager.beginTransaction().replace(R.id.fragment_container, when (navigationItem) {
                HomeViewModel.NavigationItem.Library -> LibraryFragment()
                HomeViewModel.NavigationItem.Settings -> SettingsFragment()
                is HomeViewModel.NavigationItem.Playlist -> PlaylistFragment()
            }).commit()
        }
    }

    private fun getCurrentFragment() = supportFragmentManager.findFragmentById(R.id.fragment_container) as? HomeFragment<*, *>

    private fun consumeAndCloseDrawer(action: () -> Unit) = consume {
        action()
        binding.drawerLayout.closeDrawer(GravityCompat.START)
    }
}