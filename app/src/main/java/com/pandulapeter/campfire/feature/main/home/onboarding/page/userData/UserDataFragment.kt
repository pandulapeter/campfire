package com.pandulapeter.campfire.feature.main.home.onboarding.page.userData

import android.net.Uri
import android.os.Bundle
import android.support.customtabs.CustomTabsIntent
import android.text.SpannableString
import android.text.Spanned
import android.text.method.LinkMovementMethod
import android.text.style.ClickableSpan
import android.view.View
import android.widget.FrameLayout
import com.crashlytics.android.Crashlytics
import com.pandulapeter.campfire.BuildConfig
import com.pandulapeter.campfire.R
import com.pandulapeter.campfire.data.persistence.PreferenceDatabase
import com.pandulapeter.campfire.databinding.FragmentOnboardingUserDataBinding
import com.pandulapeter.campfire.feature.main.options.about.AboutViewModel
import com.pandulapeter.campfire.feature.shared.CampfireFragment
import com.pandulapeter.campfire.util.color
import com.pandulapeter.campfire.util.waitForLayout
import io.fabric.sdk.android.Fabric
import org.koin.android.ext.android.inject

class UserDataFragment : CampfireFragment<FragmentOnboardingUserDataBinding, UserDataViewModel>(R.layout.fragment_onboarding_user_data) {

    override val viewModel = UserDataViewModel()
    private val preferenceDatabase by inject<PreferenceDatabase>()

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        val first = getString(R.string.user_data_message_end_part_1)
        val second = getString(R.string.user_data_message_end_part_2)
        val third = getString(R.string.user_data_message_end_part_3)
        binding.textEnd.movementMethod = LinkMovementMethod.getInstance()
        binding.textEnd.text = SpannableString("$first$second$third").apply {
            setSpan(object : ClickableSpan() {
                override fun onClick(widget: View?) {
                    CustomTabsIntent.Builder()
                        .setToolbarColor(requireContext().color(R.color.accent))
                        .build()
                        .launchUrl(requireContext(), Uri.parse(AboutViewModel.PRIVACY_POLICY_URL))
                }
            }, first.length, length - third.length, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE)
        }
        binding.linearLayout.apply {
            waitForLayout {
                layoutParams = (layoutParams as FrameLayout.LayoutParams).apply { setMargins(0, -getCampfireActivity().toolbarHeight, 0, 0) }
            }
        }
    }

    //TODO: Call this method when the onboarding flow is over.
    private fun onScreenClosed() {
        // Set up analytics.
        val shouldShareUsageData = binding.checkboxAnalytics.isChecked
        preferenceDatabase.shouldShareUsageData = shouldShareUsageData
        if (shouldShareUsageData) {
            analyticsManager.updateCollectionEnabledState()
            analyticsManager.onConsentGiven(System.currentTimeMillis())
        }

        // Set up crash reporting.
        val shouldShareCrashReports = binding.checkboxCrashReporting.isChecked
        preferenceDatabase.shouldShareCrashReports = shouldShareCrashReports
        @Suppress("ConstantConditionIf")
        if (shouldShareCrashReports && BuildConfig.BUILD_TYPE != "debug") {
            Fabric.with(requireContext().applicationContext, Crashlytics())
        }
    }
}