package com.pandulapeter.campfire

import android.content.Context
import com.google.android.material.dialog.MaterialAlertDialogBuilder

// TODO: Delete this class
object TemporaryWarningManager {

    private const val WARNING_KEY = "shouldHideWarningMessage"

    fun showWarningMessageIfNeeded(context: Context) {
        val sharedPreferences = context.applicationContext.getSharedPreferences("temporary", Context.MODE_PRIVATE)
        if (!sharedPreferences.getBoolean(WARNING_KEY, false)) {
            MaterialAlertDialogBuilder(context)
                .setTitle("Under development")
                .setMessage("Campfire is being rewritten from scratch.\n\nThe current version does not yet contain all the functionality from the previous one. The only reason for it being public is that the server for the old app has been shut down.\n\nI'm working hard to improve Campfire and to eventually migrate all the features. This new app already supports adding your own songs, a better support for large screens, and it also has desktop versions for Windows, Linux and MacOS (check out my GitHub page). Unfortunately the way the songs are displayed is far from final yet and you might run into bugs here and there.\n\nStay tuned for updates and sorry for the inconvenience!")
                .setNeutralButton("Don't show this again") { dialog, _ ->
                    sharedPreferences.edit().putBoolean(WARNING_KEY, true).apply()
                    dialog.dismiss()
                }
                .setPositiveButton("OK") { dialog, _ -> dialog.dismiss() }
                .show()
        }
    }
}