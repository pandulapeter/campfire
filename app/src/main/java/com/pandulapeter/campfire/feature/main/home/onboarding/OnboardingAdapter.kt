package com.pandulapeter.campfire.feature.main.home.onboarding

import android.support.v4.app.FragmentManager
import android.support.v4.app.FragmentPagerAdapter
import com.pandulapeter.campfire.feature.main.home.onboarding.page.OnboardingPageFragment
import com.pandulapeter.campfire.feature.main.home.onboarding.page.welcome.WelcomeFragment

class OnboardingAdapter(fragmentManager: FragmentManager) : FragmentPagerAdapter(fragmentManager) {

    override fun getItem(position: Int) = when (position) {
        0 -> WelcomeFragment()
        else -> OnboardingPageFragment()
    }

    override fun getCount() = 3
}