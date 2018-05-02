package com.pandulapeter.campfire.feature.home.options.about

import android.content.ActivityNotFoundException
import android.content.Context
import android.content.Intent
import android.databinding.ObservableBoolean
import android.net.Uri
import android.support.customtabs.CustomTabsIntent
import com.pandulapeter.campfire.R
import com.pandulapeter.campfire.feature.shared.CampfireViewModel
import com.pandulapeter.campfire.util.color

class AboutViewModel : CampfireViewModel() {

    companion object {
        private const val PLAY_STORE_URL = "market://details?id=com.pandulapeter.campfire"
        private const val GIT_HUB_URL = "https://github.com/pandulapeter/campfire-android"
        private const val PRIVACY_POLICY_URL = "https://campfire-test1.herokuapp.com/v1/privacy-policy"
        private const val OPEN_SOURCE_LICENSES_URL = "https://campfire-test1.herokuapp.com/v1/open-source-licenses"
        const val EMAIL_ADDRESS = "pandulapeter@gmail.com"
    }

    val shouldShowErrorShowSnackbar = ObservableBoolean()
    val shouldShowNoEasterEggSnackbar = ObservableBoolean()
    val shouldShowWorkInProgressSnackbar = ObservableBoolean()

    fun onLogoClicked() = shouldShowNoEasterEggSnackbar.set(true)

    fun onGooglePlayClicked(context: Context) = context.tryToOpenIntent(PLAY_STORE_URL.toUrlIntent())

    fun onGitHubClicked(context: Context) = context.tryToOpenIntent(GIT_HUB_URL.toUrlIntent())

    fun onShareClicked(context: Context) = context.tryToOpenIntent(
        Intent.createChooser(
            Intent().apply {
                action = Intent.ACTION_SEND
                type = "text/plain"
            }.putExtra(Intent.EXTRA_TEXT, PLAY_STORE_URL).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK), null
        )
    )

    fun onContactClicked(context: Context) = context.tryToOpenIntent(
        Intent.createChooser(
            Intent().apply {
                action = Intent.ACTION_SENDTO
                type = "text/plain"
                data = Uri.parse("mailto:$EMAIL_ADDRESS?subject=${Uri.encode(context.getString(R.string.campfire))}")
            }.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK), null
        )
    )

    //TODO: Start in app purchase flow
    fun onBuyMeABeerClicked() = shouldShowWorkInProgressSnackbar.set(true)

    fun onPrivacyPolicyClicked(context: Context) = context.openInCustomTab(PRIVACY_POLICY_URL)

    fun onLicensesClicked(context: Context) = context.openInCustomTab(OPEN_SOURCE_LICENSES_URL)

    private fun Context.tryToOpenIntent(intent: Intent) {
        try {
            startActivity(intent)
        } catch (exception: ActivityNotFoundException) {
            shouldShowErrorShowSnackbar.set(true)
        }
    }

    private fun Context.openInCustomTab(url: String) = CustomTabsIntent.Builder()
        .setToolbarColor(color(R.color.accent))
        .build()
        .launchUrl(this, Uri.parse(url))

    private fun String.toUrlIntent() = Intent(Intent.ACTION_VIEW).apply { data = Uri.parse(this@toUrlIntent) }
}