package com.pandulapeter.campfire.presentation.android

import android.graphics.Color
import androidx.activity.ComponentActivity
import androidx.activity.SystemBarStyle
import androidx.activity.compose.LocalActivity
import androidx.activity.enableEdgeToEdge
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.pandulapeter.campfire.shared.ui.CampfireApp
import com.pandulapeter.campfire.shared.ui.CampfireViewModel
import com.pandulapeter.campfire.shared.ui.theme.isDarkTheme
import org.koin.compose.viewmodel.koinViewModel

/**
 * Android shell of the shared UI: keeps the system bar icons in sync with the selected theme (which can differ from
 * the system theme) and lets the URL opener follow it too.
 *
 * @param urlOpener Opens the given URL, styled for the given theme.
 */
@Composable
fun CampfireAndroidApp(
    viewModel: CampfireViewModel = koinViewModel(),
    urlOpener: (url: String, isDarkTheme: Boolean) -> Unit
) {
    val userPreferences by viewModel.userPreferences.collectAsStateWithLifecycle()
    val isDarkTheme = userPreferences?.uiMode.isDarkTheme()
    val activity = LocalActivity.current as? ComponentActivity
    LaunchedEffect(activity, isDarkTheme) {
        activity?.enableEdgeToEdge(
            statusBarStyle = if (isDarkTheme) SystemBarStyle.dark(Color.TRANSPARENT) else SystemBarStyle.light(Color.TRANSPARENT, Color.TRANSPARENT),
            navigationBarStyle = if (isDarkTheme) SystemBarStyle.dark(Color.TRANSPARENT) else SystemBarStyle.light(Color.TRANSPARENT, Color.TRANSPARENT)
        )
    }
    CampfireApp(
        viewModel = viewModel,
        urlOpener = { urlOpener(it, isDarkTheme) }
    )
}
