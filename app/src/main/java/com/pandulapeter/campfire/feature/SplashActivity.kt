package com.pandulapeter.campfire.feature

import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.support.v7.app.AppCompatActivity
import com.pandulapeter.campfire.data.repository.UserPreferenceRepository
import com.pandulapeter.campfire.util.IntentExtraDelegate
import com.pandulapeter.campfire.util.getIntentFor
import org.koin.android.ext.android.inject

/**
 * Splash screen that's displayd while [MainActivity] is loading.
 */
class SplashActivity : AppCompatActivity() {
    private val userPreferenceRepository by inject<UserPreferenceRepository>()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        intent.mainNavigationItem.let {
            if (it.isNotEmpty()) {
                MainViewModel.MainNavigationItem.fromStringValue(it).let {
                    if (it is MainViewModel.MainNavigationItem.Home) {
                        userPreferenceRepository.navigationItem = it.homeNavigationItem
                    }
                }
            }
        }
        startActivity(MainActivity.getStartIntent(this, MainViewModel.MainNavigationItem.Home(userPreferenceRepository.navigationItem)))
        finish()
    }

    companion object {
        private var Intent.mainNavigationItem by IntentExtraDelegate.String("main_navigation_item")

        fun getStartIntent(context: Context, mainNavigationItem: MainViewModel.MainNavigationItem? = null) =
            context.getIntentFor(SplashActivity::class) { intent ->
                mainNavigationItem?.let { intent.mainNavigationItem = it.stringValue }
                intent.flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
            }
    }
}