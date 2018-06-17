package com.pandulapeter.campfire.feature.main.home.onboarding

import android.os.Bundle
import android.transition.Fade
import android.view.View
import android.view.animation.AccelerateInterpolator
import android.widget.FrameLayout
import com.pandulapeter.campfire.R
import com.pandulapeter.campfire.databinding.FragmentOnboardingBinding
import com.pandulapeter.campfire.feature.main.home.HomeContainerFragment
import com.pandulapeter.campfire.feature.shared.CampfireFragment
import com.pandulapeter.campfire.util.addPageScrollListener
import com.pandulapeter.campfire.util.waitForPreDraw

class OnboardingFragment : CampfireFragment<FragmentOnboardingBinding, OnboardingViewModel>(R.layout.fragment_onboarding) {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        exitTransition = Fade()
    }

    override val viewModel = OnboardingViewModel(::navigateToHome) {
        if (binding.viewPager.currentItem + 1 < binding.viewPager.adapter?.count ?: 0) {
            binding.viewPager.setCurrentItem(binding.viewPager.currentItem + 1, true)
        }
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        binding.root.apply {
            waitForPreDraw {
                if (isAdded) {
                    layoutParams = (layoutParams as FrameLayout.LayoutParams).apply { setMargins(0, -getCampfireActivity().toolbarHeight, 0, 0) }
                }
                false
            }
        }
        binding.viewPager.apply {
            addPageScrollListener(
                onPageScrolled = { index, offset ->
                    viewModel.doneButtonOffset.set(
                        when (binding.viewPager.adapter?.count) {
                            index + 2 -> offset
                            index + 1 -> 1f
                            else -> 0f
                        }
                    )
                }
            )
            val interpolator = AccelerateInterpolator()
            setPageTransformer(false) { view, offset ->
                view.apply {
                    translationX = -interpolator.getInterpolation(offset) * width * 0.4f
                    translationY = interpolator.getInterpolation(Math.abs(offset)) * height * 0.1f
                    alpha = 1 - Math.abs(offset)
                    scaleX = 1 - Math.abs(offset * 0.75f)
                    scaleY = scaleX
                }
            }
            adapter = OnboardingAdapter(childFragmentManager)
        }
    }

    private fun navigateToHome() {
        (parentFragment as? HomeContainerFragment)?.navigateToHome()
    }
}