package com.pandulapeter.campfire.feature.main.home.onboarding

import android.os.Bundle
import android.text.SpannableString
import android.text.Spanned
import android.text.style.ForegroundColorSpan
import android.transition.Fade
import android.view.View
import android.view.animation.AccelerateInterpolator
import android.widget.FrameLayout
import com.pandulapeter.campfire.R
import com.pandulapeter.campfire.databinding.*
import com.pandulapeter.campfire.feature.main.home.HomeContainerFragment
import com.pandulapeter.campfire.feature.main.home.onboarding.welcome.LegalDocumentsBottomSheetFragment
import com.pandulapeter.campfire.feature.shared.deprecated.OldCampfireFragment
import com.pandulapeter.campfire.util.addPageScrollListener
import com.pandulapeter.campfire.util.color
import com.pandulapeter.campfire.util.onEventTriggered
import com.pandulapeter.campfire.util.waitForPreDraw

class OnboardingFragment : OldCampfireFragment<FragmentOnboardingBinding, OnboardingViewModel>(R.layout.fragment_onboarding) {

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
        viewModel.shouldShowLegalDocuments.onEventTriggered(this) {
            if (!getCampfireActivity().isUiBlocked) {
                LegalDocumentsBottomSheetFragment.show(childFragmentManager)
            }
        }
        val first = getString(R.string.welcome_conditions_part_1)
        val second = getString(R.string.welcome_conditions_part_2)
        val third = getString(R.string.welcome_conditions_part_3)
        val fourth = getString(R.string.welcome_conditions_part_4)
        val fifth = getString(R.string.welcome_conditions_part_5)
        binding.textBottom.text = SpannableString("$first$second$third$fourth$fifth").apply {
            setSpan(
                ForegroundColorSpan(getCampfireActivity().color(R.color.accent)),
                first.length,
                first.length + second.length,
                Spanned.SPAN_INCLUSIVE_INCLUSIVE
            )
            setSpan(
                ForegroundColorSpan(getCampfireActivity().color(R.color.accent)),
                first.length + second.length + third.length,
                length - fifth.length,
                Spanned.SPAN_INCLUSIVE_INCLUSIVE
            )
        }
        val interpolator = AccelerateInterpolator()
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
            setPageTransformer(false) { view, offset ->
                val absoluteOffset = Math.abs(offset)
                fun View.applyStandardTransformation() {
                    translationX = -interpolator.getInterpolation(offset) * width * 0.4f
                    translationY = interpolator.getInterpolation(absoluteOffset) * height * 0.1f
                    alpha = 1 - absoluteOffset
                    scaleX = 1 - absoluteOffset * 0.75f
                    scaleY = scaleX
                }

                fun View.applyTitleTransformation() {
                    translationX = width * offset * 0.5f
                }

                val viewBinding = view.tag
                when (viewBinding) {
                    is FragmentOnboardingWelcomeBinding -> {
                        view.applyStandardTransformation()
                        binding.textBottom.apply {
                            if (height == 0) {
                                post { translationY = height * absoluteOffset * 4 }
                            } else {
                                translationY = height * absoluteOffset * 4
                            }
                        }
                        binding.textBottom.alpha = 1 - absoluteOffset * 5
                        viewBinding.logo.translationX = width * offset * 0.3f
                        viewBinding.logo.translationY = interpolator.getInterpolation(absoluteOffset) * viewBinding.logo.height * 0.5f
                        viewBinding.logo.alpha = 1 - absoluteOffset * 0.7f
                        viewBinding.title.applyTitleTransformation()
                    }
                    is FragmentOnboardingUserDataBinding -> {
                        view.applyStandardTransformation()
                        viewBinding.title.applyTitleTransformation()

                    }
                    is FragmentOnboardingSongAppearanceBinding -> {
                        view.applyStandardTransformation()
                        viewBinding.title.applyTitleTransformation()
                    }
                    is FragmentOnboardingContentLanguageBinding -> {
                        view.applyStandardTransformation()
                        viewBinding.title.applyTitleTransformation()
                    }
                }
            }
            offscreenPageLimit = 3
            adapter = OnboardingPagerAdapter(childFragmentManager)
        }
    }

    fun languageFiltersUpdated(selectedLanguageCount: Int) {
        viewModel.canSkip.set(selectedLanguageCount > 0)
    }

    private fun navigateToHome() {
        (parentFragment as? HomeContainerFragment)?.navigateToHome()
    }
}