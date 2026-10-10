/*
 * This file is part of Campfire.
 * Copyright (c) Pandula Péter 2017-2026.
 * https://github.com/pandulapeter/campfire
 *
 * This Source Code Form is subject to the terms of the Mozilla Public License, v. 2.0.
 * If a copy of the MPL was not distributed with this file, You can obtain one at
 * https://mozilla.org/MPL/2.0/.
 */
package com.pandulapeter.campfire.presentation.ui.platform

import android.content.Context
import android.os.Build
import android.os.VibrationEffect
import android.os.Vibrator
import android.os.VibratorManager
import kotlinx.coroutines.CoroutineDispatcher
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.platform.LocalContext
import kotlinx.coroutines.Dispatchers

internal actual val areBeatHapticsFeltInBackground = true

/** The vibrator service may be called from any thread. */
internal actual val beatHapticsDispatcher: CoroutineDispatcher = Dispatchers.Default

@Composable
internal actual fun rememberBeatHaptics(): BeatHaptics? {
    val context = LocalContext.current
    return remember(context) {
        val vibrator = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            context.getSystemService(VibratorManager::class.java)?.defaultVibrator
        } else {
            @Suppress("DEPRECATION")
            context.getSystemService(Context.VIBRATOR_SERVICE) as? Vibrator
        }
        vibrator?.takeIf { it.hasVibrator() }?.let { BeatHaptics { isAccent -> it.vibrate(beatEffect(isAccent)) } }
    }
}

/** The predefined clicks where there are any, which every device tunes to its own motor; a short pulse before them. */
private fun beatEffect(isAccent: Boolean) = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
    VibrationEffect.createPredefined(if (isAccent) VibrationEffect.EFFECT_HEAVY_CLICK else VibrationEffect.EFFECT_CLICK)
} else {
    VibrationEffect.createOneShot(if (isAccent) 30L else 15L, VibrationEffect.DEFAULT_AMPLITUDE)
}
