package com.pandulapeter.campfire.feature.main.options

import android.content.Context
import androidx.fragment.app.Fragment
import com.pandulapeter.campfire.R
import com.pandulapeter.campfire.feature.main.options.about.AboutFragment
import com.pandulapeter.campfire.feature.main.options.changelog.ChangelogFragment
import com.pandulapeter.campfire.feature.main.options.preferences.PreferencesFragment

class OptionsFragmentPagerAdapter(private val context: Context, fragmentManager: androidx.fragment.app.FragmentManager) :
    androidx.fragment.app.FragmentPagerAdapter(fragmentManager) {

    override fun getItem(position: Int): Fragment = when (position) {
        0 -> PreferencesFragment()
        1 -> ChangelogFragment()
        2 -> AboutFragment()
        else -> throw IllegalArgumentException("The pager has no Fragment for position $position.")
    }

    override fun getCount() = 3

    override fun getPageTitle(position: Int): CharSequence = context.getString(
        when (position) {
            0 -> R.string.options_preferences_preferences
            1 -> R.string.options_preferences_changelog
            2 -> R.string.options_preferences_about
            else -> throw IllegalArgumentException("The pager has no Fragment for position $position.")
        }
    )
}