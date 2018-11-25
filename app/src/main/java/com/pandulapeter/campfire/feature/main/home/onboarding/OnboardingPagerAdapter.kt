package com.pandulapeter.campfire.feature.main.home.onboarding

import com.pandulapeter.campfire.feature.main.home.onboarding.contentLanguage.ContentLanguageFragment
import com.pandulapeter.campfire.feature.main.home.onboarding.songAppearance.SongAppearanceFragment
import com.pandulapeter.campfire.feature.main.home.onboarding.userData.UserDataFragment
import com.pandulapeter.campfire.feature.main.home.onboarding.welcome.WelcomeFragment

class OnboardingPagerAdapter(fragmentManager: androidx.fragment.app.FragmentManager) : androidx.fragment.app.FragmentStatePagerAdapter(fragmentManager) {

    override fun getItem(position: Int) = when (position) {
        0 -> WelcomeFragment()
        1 -> UserDataFragment()
        2 -> SongAppearanceFragment()
        3 -> ContentLanguageFragment()
        else -> throw IllegalArgumentException("No page defined for index $position.")
    }

    override fun getCount() = 4
}