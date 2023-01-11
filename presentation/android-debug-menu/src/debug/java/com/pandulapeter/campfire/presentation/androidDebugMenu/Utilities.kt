package com.pandulapeter.campfire.presentation.androidDebugMenu

import androidx.core.view.WindowInsetsCompat
import com.pandulapeter.beagle.Beagle
import com.pandulapeter.beagle.common.configuration.Insets

internal val insetHandler: (Insets) -> Insets = {
    Beagle.currentActivity?.window?.decorView?.rootWindowInsets?.let { windowInsets ->
        WindowInsetsCompat.toWindowInsetsCompat(windowInsets)
            .getInsetsIgnoringVisibility(WindowInsetsCompat.Type.systemBars() or WindowInsetsCompat.Type.displayCutout()).let { insets ->
                Insets(
                    left = insets.left,
                    top = insets.top,
                    right = insets.right,
                    bottom = insets.bottom,
                )
            }
    } ?: it
}