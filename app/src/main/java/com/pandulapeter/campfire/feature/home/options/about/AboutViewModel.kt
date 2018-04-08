package com.pandulapeter.campfire.feature.home.options.about

import android.content.ActivityNotFoundException
import android.content.Context
import android.content.Intent
import android.databinding.ObservableBoolean
import android.net.Uri
import com.pandulapeter.campfire.R
import com.pandulapeter.campfire.feature.shared.CampfireViewModel

class AboutViewModel : CampfireViewModel() {

    companion object {
        private const val PLAY_STORE_URL = "market://details?id=com.pandulapeter.campfire"
        private const val GIT_HUB_URL = "https://github.com/pandulapeter/campfire-android"
        private const val EMAIL_ADDRESS = "pandulapeter@gmail.com"
    }

    val shouldErrorShowSnackbar = ObservableBoolean()

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

    private fun Context.tryToOpenIntent(intent: Intent) {
        try {
            startActivity(intent)
        } catch (exception: ActivityNotFoundException) {
            shouldErrorShowSnackbar.set(true)
        }
    }

    private fun String.toUrlIntent() = Intent(Intent.ACTION_VIEW).apply { data = Uri.parse(this@toUrlIntent) }
}