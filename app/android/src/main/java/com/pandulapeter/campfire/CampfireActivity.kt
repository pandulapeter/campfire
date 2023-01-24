package com.pandulapeter.campfire

import android.os.Bundle
import androidx.activity.compose.setContent
import androidx.appcompat.app.AppCompatActivity
import androidx.core.view.WindowCompat
import com.pandulapeter.campfire.presentation.android.CampfireAndroidApp
import com.pandulapeter.campfire.shared.ui.CampfireViewModel
import com.pandulapeter.campfire.shared.ui.CampfireViewModelStateHolder
import org.koin.android.ext.android.inject

class CampfireActivity : AppCompatActivity() {

    private val viewModel by inject<CampfireViewModel>()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        WindowCompat.setDecorFitsSystemWindows(window, false)
        setContent {
            CampfireAndroidApp(
                stateHolder = CampfireViewModelStateHolder.fromViewModel(viewModel)
            )
        }
    }
}