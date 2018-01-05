package com.pandulapeter.campfire.feature

import android.databinding.ObservableField
import com.pandulapeter.campfire.data.repository.UserPreferenceRepository
import com.pandulapeter.campfire.feature.detail.DetailFragment
import com.pandulapeter.campfire.feature.home.HomeFragment
import com.pandulapeter.campfire.feature.home.HomeViewModel
import com.pandulapeter.campfire.feature.shared.CampfireFragment

/**
 * Handles events and logic for [MainActivity].
 */
class MainViewModel(userPreferenceRepository: UserPreferenceRepository, overrideMainNavigationItem: MainNavigationItem?) {
    val mainNavigationItem = ObservableField<MainNavigationItem>(overrideMainNavigationItem ?: MainNavigationItem.Home(userPreferenceRepository.navigationItem))
    var previousNavigationItem: MainNavigationItem = MainNavigationItem.Home(userPreferenceRepository.navigationItem)

    sealed class MainNavigationItem(val stringValue: String) {
        abstract fun getFragment(): CampfireFragment<*, *>
        class Home(val homeNavigationItem: HomeViewModel.HomeNavigationItem) : MainNavigationItem(VALUE_HOME + homeNavigationItem.stringValue) {
            override fun getFragment() = HomeFragment.newInstance(homeNavigationItem)
        }

        class Detail(val songId: String, val playlistId: Int? = null) : MainNavigationItem(VALUE_DETAIL) {
            override fun getFragment() = DetailFragment.newInstance(songId, playlistId)
        }

        companion object {
            private const val VALUE_HOME = "home_"
            private const val VALUE_DETAIL = "detail_"

            fun fromStringValue(string: String?) = when {
                string == null || string.isEmpty() -> null
                string.startsWith(VALUE_HOME) -> Home(HomeViewModel.HomeNavigationItem.fromStringValue(string.removePrefix(VALUE_HOME)))
                string.startsWith(VALUE_DETAIL) -> Detail(string.removePrefix(VALUE_DETAIL), null)//TODO: Implement playlist ID parsing.
                else -> Home(HomeViewModel.HomeNavigationItem.Library)
            }
        }
    }
}