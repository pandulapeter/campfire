package com.pandulapeter.campfire.feature.main.home.onboarding

import android.support.v4.app.FragmentManager
import android.support.v4.app.FragmentPagerAdapter
import com.pandulapeter.campfire.feature.main.home.onboarding.page.OnboardingPageFragment

class OnboardingAdapter(fragmentManager: FragmentManager) : FragmentPagerAdapter(fragmentManager) {

    override fun getItem(position: Int) = OnboardingPageFragment()

    override fun getCount() = 3
}