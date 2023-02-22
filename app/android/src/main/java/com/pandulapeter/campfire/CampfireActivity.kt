package com.pandulapeter.campfire

import android.content.ActivityNotFoundException
import android.net.Uri
import android.os.Bundle
import android.widget.Toast
import androidx.activity.compose.setContent
import androidx.appcompat.app.AppCompatActivity
import androidx.browser.customtabs.CustomTabsIntent
import androidx.core.view.WindowCompat
import androidx.lifecycle.lifecycleScope
import com.pandulapeter.campfire.data.model.domain.UserPreferences
import com.pandulapeter.campfire.presentation.android.CampfireAndroidApp
import com.pandulapeter.campfire.shared.ui.CampfireViewModel
import com.pandulapeter.campfire.shared.ui.CampfireViewModelStateHolder
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.onEach
import org.koin.android.ext.android.inject

class CampfireActivity : AppCompatActivity() {

    private val viewModel by inject<CampfireViewModel>()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        WindowCompat.setDecorFitsSystemWindows(window, false)
        val windowInsetsController = WindowCompat.getInsetsController(window, window.decorView)
        viewModel.uiMode.distinctUntilChanged().onEach {
            windowInsetsController.isAppearanceLightStatusBars = when (it) {
                UserPreferences.UiMode.LIGHT -> true
                UserPreferences.UiMode.DARK -> false
                UserPreferences.UiMode.SYSTEM_DEFAULT, null -> !resources.getBoolean(R.bool.is_in_dark_mode)
            }
        }.launchIn(lifecycleScope)
        setContent {
            CampfireAndroidApp(
                stateHolder = CampfireViewModelStateHolder.fromViewModel(viewModel),
                urlOpener = ::openUrl
            )
        }
    }

    private fun openUrl(url: String) = try {
        CustomTabsIntent.Builder()
            .setColorScheme(if (resources.getBoolean(R.bool.is_in_dark_mode)) CustomTabsIntent.COLOR_SCHEME_DARK else CustomTabsIntent.COLOR_SCHEME_LIGHT)
            .build()
            .launchUrl(this, Uri.parse(url))
    } catch (exception: ActivityNotFoundException) {
        Toast.makeText(this, exception.message, Toast.LENGTH_SHORT).show()
    }
}