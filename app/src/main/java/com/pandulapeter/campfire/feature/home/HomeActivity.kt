package com.pandulapeter.campfire.feature.home

import android.os.Bundle
import android.support.design.widget.Snackbar
import android.support.v4.view.GravityCompat
import android.support.v4.widget.DrawerLayout
import android.view.Gravity
import android.view.View
import android.widget.TextView
import com.pandulapeter.campfire.BuildConfig
import com.pandulapeter.campfire.HomeBinding
import com.pandulapeter.campfire.R
import com.pandulapeter.campfire.data.repository.SongInfoRepository
import com.pandulapeter.campfire.data.storage.StorageManager
import com.pandulapeter.campfire.feature.home.cloud.CloudFragment
import com.pandulapeter.campfire.feature.home.downloads.DownloadsFragment
import com.pandulapeter.campfire.feature.home.favorites.FavoritesFragment
import com.pandulapeter.campfire.feature.home.shared.HomeFragment
import com.pandulapeter.campfire.feature.shared.CampfireActivity
import com.pandulapeter.campfire.util.consume
import com.pandulapeter.campfire.util.hideKeyboard
import javax.inject.Inject

/**
 * Displays the home screen of the app which contains the three main pages the user can access
 * ([CloudFragment], [DownloadsFragment] and [FavoritesFragment]) and the bottom navigation view.
 * The last selected item is persisted.
 *
 * Controlled by [HomeViewModel].
 */
class HomeActivity : CampfireActivity<HomeBinding, HomeViewModel>(R.layout.activity_home), HomeFragment.HomeCallbacks {
    @Inject lateinit var storageManager: StorageManager
    @Inject lateinit var songInfoRepository: SongInfoRepository
    override val viewModel by lazy { HomeViewModel(songInfoRepository) }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        // Set up the bottom navigation listener.
        binding.bottomNavigation.setOnNavigationItemSelectedListener { menuItem ->
            when (menuItem.itemId) {
                R.id.cloud -> consume { replaceActiveFragment(HomeViewModel.NavigationItem.CLOUD) }
                R.id.downloads -> consume { replaceActiveFragment(HomeViewModel.NavigationItem.DOWNLOADS) }
                R.id.favorites -> consume { replaceActiveFragment(HomeViewModel.NavigationItem.FAVORITES) }
                else -> false
            }
        }
        // Set up the side navigation drawers.
        binding.navigationView.getHeaderView(0).findViewById<TextView>(R.id.version)?.text = getString(R.string.main_version_pattern, BuildConfig.VERSION_NAME)
        binding.navigationView.setNavigationItemSelectedListener { menuItem ->
            //TODO: Implement a secondary layer of Fragment-based navigation.
            when (menuItem.itemId) {
                R.id.home -> consumeAndCloseDrawer { }
                R.id.edit_favorites -> consumeAndCloseDrawer { Snackbar.make(getCurrentFragment()!!.view!!, "Work in progress", Snackbar.LENGTH_SHORT).show() }
                R.id.settings -> consumeAndCloseDrawer { Snackbar.make(getCurrentFragment()!!.view!!, "Work in progress", Snackbar.LENGTH_SHORT).show() }
                else -> false
            }
        }
        binding.drawerLayout.addDrawerListener(object : DrawerLayout.DrawerListener {
            override fun onDrawerStateChanged(newState: Int) {
                hideKeyboard(currentFocus)
            }

            override fun onDrawerSlide(drawerView: View, slideOffset: Float) = Unit

            override fun onDrawerClosed(drawerView: View) = Unit

            override fun onDrawerOpened(drawerView: View) = Unit
        })
//TODO: Only enable view options for the home screen.
//        viewModel.selectedItem.onPropertyChanged {
//            binding.drawerLayout.setDrawerLockMode(
//                if (it == HomeViewModel.NavigationItem.SETTINGS) DrawerLayout.LOCK_MODE_LOCKED_CLOSED else DrawerLayout.LOCK_MODE_UNDEFINED,
//                Gravity.END)
//        }
        // Restore the state if needed. After app start we need to manually set the selected item, otherwise
        // the View takes care of it and we only need to update the displayed Fragment.
        if (savedInstanceState == null) {
            binding.bottomNavigation.selectedItemId = when (storageManager.lastSelectedNavigationItem) {
                HomeViewModel.NavigationItem.CLOUD -> R.id.cloud
                HomeViewModel.NavigationItem.DOWNLOADS -> R.id.downloads
                HomeViewModel.NavigationItem.FAVORITES -> R.id.favorites
            }
        } else {
            replaceActiveFragment(storageManager.lastSelectedNavigationItem)
        }
    }

    override fun onResume() {
        super.onResume()
        songInfoRepository.subscribe(viewModel)
    }

    override fun onPause() {
        super.onPause()
        songInfoRepository.unsubscribe(viewModel)
    }

    override fun onBackPressed() {
        val currentFragment = getCurrentFragment()
        if (currentFragment?.searchInputVisible() == true) {
            currentFragment.closeSearchInput()
        } else {
            super.onBackPressed()
        }
    }

    override fun showMenu() {
        binding.drawerLayout.openDrawer(GravityCompat.START)
        hideKeyboard(currentFocus)
    }

    override fun showViewOptions() {
        binding.drawerLayout.openDrawer(GravityCompat.END)
        hideKeyboard(currentFocus)
    }

    /**
     * Checks if the user actually changed the current selection and if so, persists it. Replaces the Fragment if
     * the selection changed or the container was empty.
     */
    private fun replaceActiveFragment(navigationItem: HomeViewModel.NavigationItem) {
        if (viewModel.selectedItem.get() != navigationItem || getCurrentFragment() == null) {
            storageManager.lastSelectedNavigationItem = navigationItem
            viewModel.selectedItem.set(navigationItem)
            supportFragmentManager.beginTransaction().replace(R.id.fragment_container, when (navigationItem) {
                HomeViewModel.NavigationItem.CLOUD -> CloudFragment()
                HomeViewModel.NavigationItem.DOWNLOADS -> DownloadsFragment()
                HomeViewModel.NavigationItem.FAVORITES -> FavoritesFragment()
            }).commit()
        }
    }

    private fun getCurrentFragment() = supportFragmentManager.findFragmentById(R.id.fragment_container) as? HomeFragment<*, *>

    private fun consumeAndCloseDrawer(action: () -> Unit): Boolean {
        action()
        binding.drawerLayout.closeDrawer(Gravity.START)
        return true
    }
}