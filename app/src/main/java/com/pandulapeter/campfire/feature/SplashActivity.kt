package com.pandulapeter.campfire.feature

import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.support.v7.app.AppCompatActivity
import com.pandulapeter.campfire.util.IntentExtraDelegate
import com.pandulapeter.campfire.util.getIntentFor

/**
 * Splash screen that's displayd while [MainActivity] is loading.
 */
class SplashActivity : AppCompatActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        startActivity(
            MainActivity.getStartIntent(
                this,
                intent.mainNavigationItem.let { if (it.isEmpty()) null else MainViewModel.MainNavigationItem.fromStringValue(it) })
        )
        finish()
    }

    companion object {
        private var Intent.mainNavigationItem by IntentExtraDelegate.String("main_navigation_item")

        fun getStartIntent(context: Context, mainNavigationItem: MainViewModel.MainNavigationItem? = null) =
            context.getIntentFor(SplashActivity::class) { intent ->
                mainNavigationItem?.let { intent.mainNavigationItem = it.stringValue }
            }
    }
}