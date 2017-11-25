package com.pandulapeter.campfire.feature.home

import android.databinding.DataBindingUtil
import android.os.Bundle
import android.os.PersistableBundle
import com.pandulapeter.campfire.HomeBinding
import com.pandulapeter.campfire.R
import com.pandulapeter.campfire.data.network.NetworkManager
import com.pandulapeter.campfire.feature.home.downloaded.DownloadedFragment
import com.pandulapeter.campfire.feature.home.favorites.FavoritesFragment
import com.pandulapeter.campfire.feature.home.library.LibraryFragment
import dagger.android.support.DaggerAppCompatActivity
import javax.inject.Inject

/**
 * Displays the main screen of the app which contains the app bar, the three possible Fragments that
 * can be selected and the bottom navigation.
 *
 * Controlled by [HomeViewModel].
 */
class HomeActivity : DaggerAppCompatActivity() {

    @Inject lateinit var networkManager: NetworkManager

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        DataBindingUtil.setContentView<HomeBinding>(this, R.layout.activity_home).apply {
            viewModel = HomeViewModel(networkManager)
            bottomNavigation.setOnNavigationItemReselectedListener { menuItem ->
                when (menuItem.itemId) {
                    R.id.library -> NavigationItem.LIBRARY
                    R.id.downloaded -> NavigationItem.DOWNLOADED
                    R.id.favorites -> NavigationItem.FAVORITES
                    else -> null
                }?.let { replaceActiveFragment(it) }
            }
        }
        replaceActiveFragment(savedInstanceState?.getSerializable(NAVIGATION_ITEM) as? NavigationItem ?: NavigationItem.LIBRARY)
    }

    override fun onSaveInstanceState(outState: Bundle?, outPersistentState: PersistableBundle?) {
        super.onSaveInstanceState(outState, outPersistentState)
        outState?.putSerializable(NAVIGATION_ITEM, NavigationItem.LIBRARY)
    }

    private fun replaceActiveFragment(navigationItem: NavigationItem) {
        supportFragmentManager.beginTransaction().replace(R.id.fragment_container, when (navigationItem) {
            HomeActivity.NavigationItem.LIBRARY -> LibraryFragment()
            HomeActivity.NavigationItem.DOWNLOADED -> DownloadedFragment()
            HomeActivity.NavigationItem.FAVORITES -> FavoritesFragment()
        }).commit()
    }

    private enum class NavigationItem {
        LIBRARY, DOWNLOADED, FAVORITES
    }

    companion object {
        private const val NAVIGATION_ITEM = "navigation_item"
    }
}