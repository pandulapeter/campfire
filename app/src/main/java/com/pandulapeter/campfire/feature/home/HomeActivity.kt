package com.pandulapeter.campfire.feature.home

import android.databinding.DataBindingUtil
import android.os.Bundle
import android.support.v4.view.GravityCompat
import com.pandulapeter.campfire.HomeBinding
import com.pandulapeter.campfire.R
import com.pandulapeter.campfire.data.storage.StorageManager
import com.pandulapeter.campfire.feature.home.cloud.CloudFragment
import com.pandulapeter.campfire.feature.home.downloaded.DownloadedFragment
import com.pandulapeter.campfire.feature.home.favorites.FavoritesFragment
import com.pandulapeter.campfire.feature.home.shared.HomeFragment
import com.pandulapeter.campfire.util.consume
import dagger.android.support.DaggerAppCompatActivity
import javax.inject.Inject

/**
 * Displays the home screen of the app which contains the three main pages the user can access
 * ([CloudFragment], [DownloadedFragment] and [FavoritesFragment]) and the bottom navigation view.
 * The last selected item is persisted.
 *
 * Controlled by [HomeViewModel].
 */
class HomeActivity : DaggerAppCompatActivity(), HomeFragment.HomeCallbacks {
    @Inject lateinit var storageManager: StorageManager
    private lateinit var binding: HomeBinding
    private val viewModel = HomeViewModel()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        // Inflate the layout and set up the navigation listener.
        binding = DataBindingUtil.setContentView(this, R.layout.activity_home);
        binding.viewModel = viewModel
        binding.bottomNavigation.setOnNavigationItemSelectedListener { menuItem ->
            when (menuItem.itemId) {
                R.id.cloud -> consume { replaceActiveFragment(HomeViewModel.NavigationItem.CLOUD) }
                R.id.downloaded -> consume { replaceActiveFragment(HomeViewModel.NavigationItem.DOWNLOADED) }
                R.id.favorites -> consume { replaceActiveFragment(HomeViewModel.NavigationItem.FAVORITES) }
                else -> false
            }
        }
        // Restore the state if needed. After app start we need to manually set the selected item, otherwise
        // the View takes care of it and we only need to update the displayed Fragment.
        if (savedInstanceState == null) {
            binding.bottomNavigation.selectedItemId = when (storageManager.lastSelectedNavigationItem) {
                HomeViewModel.NavigationItem.CLOUD -> R.id.cloud
                HomeViewModel.NavigationItem.DOWNLOADED -> R.id.downloaded
                HomeViewModel.NavigationItem.FAVORITES -> R.id.favorites
            }
        } else {
            replaceActiveFragment(storageManager.lastSelectedNavigationItem)
        }
    }

    override fun showViewOptions() = binding.drawerLayout.openDrawer(GravityCompat.END)

    /**
     * Checks if the user actually changed the current selection and if so, persists it. Replaces the Fragment if
     * the selection changed or the container was empty.
     */
    private fun replaceActiveFragment(navigationItem: HomeViewModel.NavigationItem) {
        if (viewModel.selectedItem.get() != navigationItem || supportFragmentManager.findFragmentById(R.id.fragment_container) == null) {
            storageManager.lastSelectedNavigationItem = navigationItem
            viewModel.selectedItem.set(navigationItem)
            supportFragmentManager.beginTransaction().replace(R.id.fragment_container, when (navigationItem) {
                HomeViewModel.NavigationItem.CLOUD -> CloudFragment()
                HomeViewModel.NavigationItem.DOWNLOADED -> DownloadedFragment()
                HomeViewModel.NavigationItem.FAVORITES -> FavoritesFragment()
            }).commit()
        }
    }
}