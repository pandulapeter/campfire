package com.pandulapeter.campfire.feature.home.settings

import android.content.Context
import android.support.v4.app.FragmentManager
import android.support.v4.app.FragmentPagerAdapter
import com.pandulapeter.campfire.R

class SettingsFragmentPagerAdapter(private val context: Context, fragmentManager: FragmentManager) : FragmentPagerAdapter(fragmentManager) {

    override fun getItem(position: Int) = when (position) {
        0 -> SettingsPreferencesFragment()
        1 -> SettingsChangelogFragment()
        2 -> SettingsAboutFragment()
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