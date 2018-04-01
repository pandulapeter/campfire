package com.pandulapeter.campfire.feature.home.options

import android.content.Context
import android.support.v4.app.FragmentManager
import android.support.v4.app.FragmentPagerAdapter
import com.pandulapeter.campfire.R
import com.pandulapeter.campfire.feature.home.options.pages.AboutFragment
import com.pandulapeter.campfire.feature.home.options.pages.ChangelogFragment
import com.pandulapeter.campfire.feature.home.options.pages.PreferencesFragment

class OptionsFragmentPagerAdapter(private val context: Context, fragmentManager: FragmentManager) : FragmentPagerAdapter(fragmentManager) {

    override fun getItem(position: Int) = when (position) {
        0 -> PreferencesFragment()
        1 -> ChangelogFragment()
        2 -> AboutFragment()
        else -> throw IllegalArgumentException("The pager has no Fragment for position $position.")
    }

    override fun getCount() = 3

    override fun getPageTitle(position: Int): CharSequence = context.getString(
        when (position) {
            0 -> R.string.settings_preferences
            1 -> R.string.settings_changelog
            2 -> R.string.settings_about
            else -> throw IllegalArgumentException("The pager has no Fragment for position $position.")
        }
    )
}