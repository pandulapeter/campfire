package com.pandulapeter.campfire.feature.main.options.about

import android.content.ActivityNotFoundException
import android.content.Context
import android.content.Intent
import android.databinding.ObservableBoolean
import android.net.Uri
import android.support.customtabs.CustomTabsIntent
import com.pandulapeter.campfire.R
import com.pandulapeter.campfire.feature.shared.CampfireViewModel
import com.pandulapeter.campfire.integration.AnalyticsManager
import com.pandulapeter.campfire.util.color
import org.koin.android.ext.android.inject

class AboutViewModel(private val isUiBlocked: () -> Boolean) : CampfireViewModel() {

    companion object {
        private const val PLAY_STORE_URL = "market://details?id=com.pandulapeter.campfire"
        private const val GIT_HUB_URL = "https://github.com/pandulapeter/campfire-android"
        const val TERMS_AND_CONDITIONS_URL = "https://campfire-test1.herokuapp.com/v1/terms-and-conditions"
        const val PRIVACY_POLICY_URL = "https://campfire-test1.herokuapp.com/v1/privacy-policy"
        private const val OPEN_SOURCE_LICENSES_URL = "https://campfire-test1.herokuapp.com/v1/open-source-licenses"
        const val EMAIL_ADDRESS = "pandulapeter@gmail.com"
    }

    private val analyticsManager by inject<AnalyticsManager>()
    val shouldShowErrorShowSnackbar = ObservableBoolean()
    val shouldShowNoEasterEggSnackbar = ObservableBoolean()
    val shouldShowWorkInProgressSnackbar = ObservableBoolean()
    val shouldBlockUi = ObservableBoolean()

    fun onLogoClicked() {
        analyticsManager.trackAboutLogoPressed()
        shouldShowNoEasterEggSnackbar.set(true)
    }

    fun onGooglePlayClicked(context: Context) {
        analyticsManager.trackAboutLinkOpened(AnalyticsManager.PARAM_VALUE_ABOUT_GOOGLE_PLAY)
        context.tryToOpenIntent(PLAY_STORE_URL.toUrlIntent())
    }

    fun onGitHubClicked(context: Context) {
        analyticsManager.trackAboutLinkOpened(AnalyticsManager.PARAM_VALUE_ABOUT_GITHUB)
        context.tryToOpenIntent(GIT_HUB_URL.toUrlIntent())
    }

    fun onShareClicked(context: Context) {
        analyticsManager.trackAboutLinkOpened(AnalyticsManager.PARAM_VALUE_ABOUT_SHARE)
        context.tryToOpenIntent(
            Intent.createChooser(
                Intent().apply {
                    action = Intent.ACTION_SEND
                    type = "text/plain"
                }.putExtra(Intent.EXTRA_TEXT, PLAY_STORE_URL).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK), null
            )
        )
    }

    fun onContactClicked(context: Context) {
        analyticsManager.trackAboutLinkOpened(AnalyticsManager.PARAM_VALUE_ABOUT_CONTACT_ME)
        context.tryToOpenIntent(
            Intent.createChooser(
                Intent().apply {
                    action = Intent.ACTION_SENDTO
                    type = "text/plain"
                    data = Uri.parse("mailto:$EMAIL_ADDRESS?subject=${Uri.encode(context.getString(R.string.campfire))}")
                }.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK), null
            )
        )
    }

    fun onBuyMeABeerClicked() {
        analyticsManager.trackAboutLinkOpened(AnalyticsManager.PARAM_VALUE_ABOUT_BUY_ME_A_BEER)
        //TODO: Start in app purchase flow
        shouldShowWorkInProgressSnackbar.set(true)
    }

    fun onTermsAndConditionsClicked(context: Context) {
        analyticsManager.trackAboutLegalPageOpened(AnalyticsManager.PARAM_VALUE_ABOUT_TERMS_AND_CONDITIONS)
        context.openInCustomTab(TERMS_AND_CONDITIONS_URL)
    }

    fun onPrivacyPolicyClicked(context: Context) {
        analyticsManager.trackAboutLegalPageOpened(AnalyticsManager.PARAM_VALUE_ABOUT_PRIVACY_POLICY)
        context.openInCustomTab(PRIVACY_POLICY_URL)
    }

    fun onLicensesClicked(context: Context) {
        analyticsManager.trackAboutLegalPageOpened(AnalyticsManager.PARAM_VALUE_ABOUT_OPEN_SOURCE_LICENSES)
        context.openInCustomTab(OPEN_SOURCE_LICENSES_URL)
    }

    private fun Context.tryToOpenIntent(intent: Intent) {
        if (!isUiBlocked()) {
            try {
                startActivity(intent)
                shouldBlockUi.set(true)
            } catch (exception: ActivityNotFoundException) {
                shouldShowErrorShowSnackbar.set(true)
            }
        }
    }

    private fun Context.openInCustomTab(url: String) {
        if (!isUiBlocked()) {
            shouldBlockUi.set(true)
            CustomTabsIntent.Builder()
                .setToolbarColor(color(R.color.accent))
                .build()
                .launchUrl(this, Uri.parse(url))
        }
    }

    private fun String.toUrlIntent() = Intent(Intent.ACTION_VIEW).apply { data = Uri.parse(this@toUrlIntent) }
}