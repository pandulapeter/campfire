package com.pandulapeter.campfire.feature.main.options.about

import android.content.Intent
import android.net.Uri
import androidx.lifecycle.MutableLiveData
import com.pandulapeter.campfire.feature.shared.CampfireViewModel
import com.pandulapeter.campfire.feature.shared.InteractionBlocker
import com.pandulapeter.campfire.integration.AnalyticsManager
import com.pandulapeter.campfire.util.toUrlIntent

class AboutViewModel(
    private val analyticsManager: AnalyticsManager,
    interactionBlocker: InteractionBlocker
) : CampfireViewModel(interactionBlocker) {

    val shouldShowErrorShowSnackbar = MutableLiveData<Boolean?>()
    val shouldAnimateLogo = MutableLiveData<Boolean?>()
    val shouldStartPurchaseFlow = MutableLiveData<Boolean?>()
    val intentToStart = MutableLiveData<Intent?>()
    val urlToStart = MutableLiveData<String?>()

    fun onLogoClicked() {
        analyticsManager.trackAboutLogoPressed()
        shouldAnimateLogo.value = true
    }

    fun onGooglePlayClicked() {
        if (!isUiBlocked) {
            isUiBlocked = true
            analyticsManager.trackAboutLinkOpened(AnalyticsManager.PARAM_VALUE_ABOUT_GOOGLE_PLAY)
            intentToStart.value = PLAY_STORE_URL.toUrlIntent()
        }
    }

    fun onGitHubClicked() {
        if (!isUiBlocked) {
            isUiBlocked = true
            analyticsManager.trackAboutLinkOpened(AnalyticsManager.PARAM_VALUE_ABOUT_GITHUB)
            intentToStart.value = GIT_HUB_URL.toUrlIntent()
        }
    }

    fun onShareClicked() {
        if (!isUiBlocked) {
            isUiBlocked = true
            analyticsManager.trackAboutLinkOpened(AnalyticsManager.PARAM_VALUE_ABOUT_SHARE)
            intentToStart.value = Intent.createChooser(
                Intent().apply {
                    action = Intent.ACTION_SEND
                    type = "text/plain"
                }.putExtra(Intent.EXTRA_TEXT, PLAY_STORE_URL).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK), null
            )
        }
    }

    fun onContactClicked() {
        if (!isUiBlocked) {
            isUiBlocked = true
            analyticsManager.trackAboutLinkOpened(AnalyticsManager.PARAM_VALUE_ABOUT_CONTACT_ME)
            intentToStart.value = Intent.createChooser(
                Intent().apply {
                    action = Intent.ACTION_SENDTO
                    type = "text/plain"
                    data = Uri.parse("mailto:$EMAIL_ADDRESS?subject=${Uri.encode("Campfire")}")
                }.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK), null
            )
        }
    }

    fun onBuyMeABeerClicked() {
        if (!isUiBlocked) {
            isUiBlocked = true
            analyticsManager.trackAboutLinkOpened(AnalyticsManager.PARAM_VALUE_ABOUT_BUY_ME_A_BEER)
            shouldStartPurchaseFlow.value = true
        }
    }

    fun onTermsAndConditionsClicked() {
        if (!isUiBlocked) {
            isUiBlocked = true
            analyticsManager.trackAboutLegalPageOpened(AnalyticsManager.PARAM_VALUE_ABOUT_TERMS_AND_CONDITIONS)
            urlToStart.value = TERMS_AND_CONDITIONS_URL
        }
    }

    fun onPrivacyPolicyClicked() {
        if (!isUiBlocked) {
            isUiBlocked = true
            analyticsManager.trackAboutLegalPageOpened(AnalyticsManager.PARAM_VALUE_ABOUT_PRIVACY_POLICY)
            urlToStart.value = PRIVACY_POLICY_URL
        }
    }

    fun onLicensesClicked() {
        if (!isUiBlocked) {
            isUiBlocked = true
            analyticsManager.trackAboutLegalPageOpened(AnalyticsManager.PARAM_VALUE_ABOUT_OPEN_SOURCE_LICENSES)
            urlToStart.value = OPEN_SOURCE_LICENSES_URL
        }
    }

    companion object {
        private const val GIT_HUB_URL = "https://github.com/pandulapeter/campfire-android"
        const val PLAY_STORE_URL = "market://details?id=com.pandulapeter.campfire"
        const val TERMS_AND_CONDITIONS_URL = "https://campfire-test1.herokuapp.com/v1/terms-and-conditions"
        const val PRIVACY_POLICY_URL = "https://campfire-test1.herokuapp.com/v1/privacy-policy"
        const val OPEN_SOURCE_LICENSES_URL = "https://campfire-test1.herokuapp.com/v1/open-source-licenses"
        const val EMAIL_ADDRESS = "pandulapeter@gmail.com"
    }
}