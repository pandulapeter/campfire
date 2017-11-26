package com.pandulapeter.campfire.feature.home

import android.databinding.DataBindingUtil
import android.os.Bundle
import android.support.design.internal.BottomNavigationItemView
import android.support.design.internal.BottomNavigationMenuView
import android.support.design.widget.BottomNavigationView
import android.support.v4.view.GravityCompat
import android.support.v4.widget.DrawerLayout
import android.view.View
import com.pandulapeter.campfire.HomeBinding
import com.pandulapeter.campfire.R
import com.pandulapeter.campfire.data.repository.SongInfoRepository
import com.pandulapeter.campfire.data.storage.StorageManager
import com.pandulapeter.campfire.feature.home.cloud.CloudFragment
import com.pandulapeter.campfire.feature.home.downloads.DownloadsFragment
import com.pandulapeter.campfire.feature.home.favorites.FavoritesFragment
import com.pandulapeter.campfire.feature.home.settings.SettingsFragment
import com.pandulapeter.campfire.feature.home.shared.HomeFragment
import com.pandulapeter.campfire.util.consume
import com.pandulapeter.campfire.util.hideKeyboard
import com.pandulapeter.campfire.util.onPropertyChanged
import dagger.android.support.DaggerAppCompatActivity
import javax.inject.Inject

/**
 * Displays the home screen of the app which contains the three main pages the user can access
 * ([CloudFragment], [DownloadsFragment] and [FavoritesFragment]) and the bottom navigation view.
 * The last selected item is persisted.
 *
 * Controlled by [HomeViewModel].
 */
class HomeActivity : DaggerAppCompatActivity(), HomeFragment.HomeCallbacks {
    @Inject lateinit var storageManager: StorageManager
    @Inject lateinit var songInfoRepository: SongInfoRepository
    private lateinit var binding: HomeBinding
    private val viewModel by lazy { HomeViewModel(songInfoRepository) }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        // Inflate the layout and set up the bottom navigation listener.
        binding = DataBindingUtil.setContentView(this, R.layout.activity_home);
        binding.viewModel = viewModel
        disableBottomNavigationScaleAnimation(binding.bottomNavigation)
        binding.bottomNavigation.setOnNavigationItemSelectedListener { menuItem ->
            when (menuItem.itemId) {
                R.id.cloud -> consume { replaceActiveFragment(HomeViewModel.NavigationItem.CLOUD) }
                R.id.downloads -> consume { replaceActiveFragment(HomeViewModel.NavigationItem.DOWNLOADS) }
                R.id.favorites -> consume { replaceActiveFragment(HomeViewModel.NavigationItem.FAVORITES) }
                R.id.settings -> consume { replaceActiveFragment(HomeViewModel.NavigationItem.SETTINGS) }
                else -> false
            }
        }
        // Set up the side navigation bar.
        binding.drawerLayout.addDrawerListener(object : DrawerLayout.DrawerListener {
            override fun onDrawerStateChanged(newState: Int) {
                hideKeyboard(currentFocus)
            }

            override fun onDrawerSlide(drawerView: View, slideOffset: Float) = Unit

            override fun onDrawerClosed(drawerView: View) = Unit

            override fun onDrawerOpened(drawerView: View) = Unit
        })
        viewModel.selectedItem.onPropertyChanged {
            binding.drawerLayout.setDrawerLockMode(
                if (it == HomeViewModel.NavigationItem.SETTINGS) DrawerLayout.LOCK_MODE_LOCKED_CLOSED else DrawerLayout.LOCK_MODE_UNDEFINED)
        }
        // Restore the state if needed. After app start we need to manually set the selected item, otherwise
        // the View takes care of it and we only need to update the displayed Fragment.
        if (savedInstanceState == null) {
            binding.bottomNavigation.selectedItemId = when (storageManager.lastSelectedNavigationItem) {
                HomeViewModel.NavigationItem.CLOUD -> R.id.cloud
                HomeViewModel.NavigationItem.DOWNLOADS -> R.id.downloads
                HomeViewModel.NavigationItem.FAVORITES -> R.id.favorites
                HomeViewModel.NavigationItem.SETTINGS -> R.id.settings
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
                HomeViewModel.NavigationItem.SETTINGS -> SettingsFragment()
            }).commit()
        }
    }

    //TODO: Don't use reflection, come up with a better solution.
    private fun disableBottomNavigationScaleAnimation(view: BottomNavigationView) {
        val menuView = view.getChildAt(0) as BottomNavigationMenuView
        try {
            val shiftingMode = menuView.javaClass.getDeclaredField("mShiftingMode")
            shiftingMode.isAccessible = true
            shiftingMode.setBoolean(menuView, false)
            shiftingMode.isAccessible = false
            for (i in 0 until menuView.childCount) {
                val item = menuView.getChildAt(i) as BottomNavigationItemView
                item.setShiftingMode(false)
                item.setChecked(item.itemData.isChecked)
            }
        } catch (_: NoSuchFieldException) {
        } catch (_: IllegalAccessException) {
        }
    }

    private fun getCurrentFragment() = supportFragmentManager.findFragmentById(R.id.fragment_container) as? HomeFragment<*, *>
}