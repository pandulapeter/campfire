package com.pandulapeter.campfire.feature.main.home.onboarding

import android.support.v4.app.FragmentManager
import android.support.v4.app.FragmentStatePagerAdapter
import com.pandulapeter.campfire.feature.main.home.onboarding.page.languageSelector.LanguageSelectorFragment
import com.pandulapeter.campfire.feature.main.home.onboarding.page.musicianType.MusicianTypeFragment
import com.pandulapeter.campfire.feature.main.home.onboarding.page.userData.UserDataFragment
import com.pandulapeter.campfire.feature.main.home.onboarding.page.welcome.WelcomeFragment

class OnboardingAdapter(fragmentManager: FragmentManager) : FragmentStatePagerAdapter(fragmentManager) {

    override fun getItem(position: Int) = when (position) {
        0 -> WelcomeFragment()
        1 -> UserDataFragment()
        2 -> MusicianTypeFragment()
        3 -> LanguageSelectorFragment()
        else -> throw IllegalArgumentException("No page defined for index $position.")
    }

    override fun getCount() = 4
}