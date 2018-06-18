package com.pandulapeter.campfire.feature.main.home.onboarding

import android.net.Uri
import android.os.Bundle
import android.support.customtabs.CustomTabsIntent
import android.text.SpannableString
import android.text.Spanned
import android.text.TextPaint
import android.text.method.LinkMovementMethod
import android.text.style.ClickableSpan
import android.text.style.ForegroundColorSpan
import android.transition.Fade
import android.view.View
import android.view.animation.AccelerateInterpolator
import android.widget.FrameLayout
import com.pandulapeter.campfire.R
import com.pandulapeter.campfire.databinding.*
import com.pandulapeter.campfire.feature.main.home.HomeContainerFragment
import com.pandulapeter.campfire.feature.main.options.about.AboutViewModel
import com.pandulapeter.campfire.feature.shared.CampfireFragment
import com.pandulapeter.campfire.util.addPageScrollListener
import com.pandulapeter.campfire.util.color
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
        val first = getString(R.string.welcome_conditions_part_1)
        val second = getString(R.string.welcome_conditions_part_2)
        val third = getString(R.string.welcome_conditions_part_3)
        val fourth = getString(R.string.welcome_conditions_part_4)
        val fifth = getString(R.string.welcome_conditions_part_5)
        binding.textBottom.text = SpannableString("$first$second$third$fourth$fifth").apply {
            fun applyClickableSpan(startIndex: Int, endIndex: Int, url: String) {
                setSpan(
                    ForegroundColorSpan(getCampfireActivity().color(R.color.accent)),
                    startIndex,
                    endIndex,
                    Spanned.SPAN_INCLUSIVE_INCLUSIVE
                )
                setSpan(
                    object : ClickableSpan() {
                        override fun onClick(widget: View?) {
                            if (!getCampfireActivity().isUiBlocked) {
                                getCampfireActivity().isUiBlocked = true
                                CustomTabsIntent.Builder()
                                    .setToolbarColor(getCampfireActivity().color(R.color.accent))
                                    .build()
                                    .launchUrl(getCampfireActivity(), Uri.parse(url))
                            }
                        }

                        override fun updateDrawState(ds: TextPaint?) {
                            ds?.isUnderlineText = false
                        }

                    },
                    startIndex,
                    endIndex,
                    Spanned.SPAN_INCLUSIVE_INCLUSIVE
                )
            }
            applyClickableSpan(first.length, first.length + second.length, AboutViewModel.TERMS_AND_CONDITIONS_URL)
            applyClickableSpan(first.length + second.length + third.length, length - fifth.length, AboutViewModel.PRIVACY_POLICY_URL)
        }
        binding.textBottom.movementMethod = LinkMovementMethod.getInstance()
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
                        binding.textBottom.translationY = binding.textBottom.height * absoluteOffset * 4
                        binding.textBottom.alpha = 1 - absoluteOffset * 5
                        viewBinding.logo.alpha = 1 - absoluteOffset * 3
                        viewBinding.logo.translationY = -width * absoluteOffset * 0.4f
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
            adapter = OnboardingAdapter(childFragmentManager)
        }
    }

    fun languageFiltersUpdated(selectedLanguageCount: Int) {
        viewModel.canSkip.set(selectedLanguageCount > 0)
    }

    private fun navigateToHome() {
        (parentFragment as? HomeContainerFragment)?.navigateToHome()
    }
}