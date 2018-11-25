package com.pandulapeter.campfire.feature.main.home.onboarding

import androidx.fragment.app.FragmentManager
import androidx.fragment.app.FragmentStatePagerAdapter
import com.pandulapeter.campfire.feature.main.home.onboarding.contentLanguage.ContentLanguageFragment
import com.pandulapeter.campfire.feature.main.home.onboarding.songAppearance.SongAppearanceFragment
import com.pandulapeter.campfire.feature.main.home.onboarding.userData.UserDataFragment
import com.pandulapeter.campfire.feature.main.home.onboarding.welcome.WelcomeFragment

class OnboardingPagerAdapter(fragmentManager: FragmentManager) : FragmentStatePagerAdapter(fragmentManager) {

    override fun getItem(position: Int) = when (position) {
        0 -> WelcomeFragment.newInstance()
        1 -> UserDataFragment.newInstance()
        2 -> SongAppearanceFragment.newInstance()
        3 -> ContentLanguageFragment.newInstance()
        else -> throw IllegalArgumentException("No page defined for index $position.")
    }

    override fun getCount() = 4
}