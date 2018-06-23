package com.pandulapeter.campfire.feature.main.home.onboarding

import android.support.v4.app.FragmentManager
import android.support.v4.app.FragmentStatePagerAdapter
import com.pandulapeter.campfire.feature.main.home.onboarding.page.contentLanguage.ContentLanguageFragment
import com.pandulapeter.campfire.feature.main.home.onboarding.page.songAppearance.SongAppearanceFragment
import com.pandulapeter.campfire.feature.main.home.onboarding.page.userData.UserDataFragment
import com.pandulapeter.campfire.feature.main.home.onboarding.page.welcome.WelcomeFragment

class OnboardingPagerAdapter(fragmentManager: FragmentManager) : FragmentStatePagerAdapter(fragmentManager) {

    override fun getItem(position: Int) = when (position) {
        0 -> WelcomeFragment()
        1 -> UserDataFragment()
        2 -> SongAppearanceFragment()
        3 -> ContentLanguageFragment()
        else -> throw IllegalArgumentException("No page defined for index $position.")
    }

    override fun getCount() = 4
}