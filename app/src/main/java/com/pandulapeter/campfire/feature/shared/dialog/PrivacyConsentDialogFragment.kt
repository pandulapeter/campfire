package com.pandulapeter.campfire.feature.shared.dialog

import android.databinding.DataBindingUtil
import android.net.Uri
import android.os.Bundle
import android.support.customtabs.CustomTabsIntent
import android.support.v4.app.FragmentManager
import android.support.v7.app.AlertDialog
import android.text.SpannableString
import android.text.Spanned
import android.text.method.LinkMovementMethod
import android.text.style.ClickableSpan
import android.view.LayoutInflater
import android.view.View
import com.crashlytics.android.Crashlytics
import com.pandulapeter.campfire.BuildConfig
import com.pandulapeter.campfire.PrivacyConsentBinding
import com.pandulapeter.campfire.R
import com.pandulapeter.campfire.data.persistence.PreferenceDatabase
import com.pandulapeter.campfire.feature.main.options.about.AboutViewModel
import com.pandulapeter.campfire.integration.AnalyticsManager
import com.pandulapeter.campfire.util.color
import io.fabric.sdk.android.Fabric
import org.koin.android.ext.android.inject

class PrivacyConsentDialogFragment : BaseDialogFragment() {

    companion object {
        fun show(fragmentManager: FragmentManager) = PrivacyConsentDialogFragment().run {
            isCancelable = false
            show(fragmentManager, tag)
        }
    }

    private val preferenceDatabase by inject<PreferenceDatabase>()
    private val analyticsManager by inject<AnalyticsManager>()
    private val binding by lazy {
        DataBindingUtil.inflate<PrivacyConsentBinding>(LayoutInflater.from(context), R.layout.dialog_privacy_consent, null, false).apply {
            val first = getString(R.string.main_privacy_policy_message_end_part_1)
            val second = getString(R.string.main_privacy_policy_message_end_part_2)
            val third = getString(R.string.main_privacy_policy_message_end_part_3)
            textEnd.movementMethod = LinkMovementMethod.getInstance()
            textEnd.text = SpannableString("$first$second$third").apply {
                setSpan(object : ClickableSpan() {
                    override fun onClick(widget: View?) {
                        CustomTabsIntent.Builder()
                            .setToolbarColor(requireContext().color(R.color.accent))
                            .build()
                            .launchUrl(requireContext(), Uri.parse(AboutViewModel.PRIVACY_POLICY_URL))
                    }
                }, first.length, length - third.length, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE)
            }
        }
    }

    override fun AlertDialog.Builder.createDialog(arguments: Bundle?): AlertDialog = setView(binding.root)
        .setPositiveButton(R.string.main_privacy_policy_positive, { _, _ ->
            preferenceDatabase.shouldShowPrivacyPolicy = false

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
        })
        .create()
}