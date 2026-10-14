/*
 * This file is part of Campfire.
 * Copyright (c) Pandula Péter 2017-2026.
 * https://github.com/pandulapeter/campfire
 *
 * This Source Code Form is subject to the terms of the Mozilla Public License, v. 2.0.
 * If a copy of the MPL was not distributed with this file, You can obtain one at
 * https://mozilla.org/MPL/2.0/.
 */
package com.pandulapeter.campfire.presentation.ui.metronome

import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.repeatOnLifecycle
import com.pandulapeter.campfire.metronome.api.model.BeatLevel
import com.pandulapeter.campfire.presentation.ui.CampfireViewModel
import com.pandulapeter.campfire.presentation.ui.platform.areBeatHapticsFeltInBackground
import com.pandulapeter.campfire.presentation.ui.platform.beatHapticsDispatcher
import com.pandulapeter.campfire.presentation.ui.platform.rememberBeatHaptics
import kotlinx.coroutines.withContext

/**
 * The beat in the hand, from the heard beats, only where the user asked for it: while the app is resumed, or for as long
 * as it is composed where [areBeatHapticsFeltInBackground].
 */
@Composable
internal fun MetronomeHapticsEffect(viewModel: CampfireViewModel) {
    val haptics = rememberBeatHaptics() ?: return
    val settings by viewModel.metronomeSettings.collectAsStateWithLifecycle()
    if (!settings.isHapticBeatEnabled) return
    val lifecycleOwner = LocalLifecycleOwner.current
    LaunchedEffect(haptics, lifecycleOwner) {
        lifecycleOwner.repeatOnLifecycle(if (areBeatHapticsFeltInBackground) Lifecycle.State.CREATED else Lifecycle.State.RESUMED) {
            withContext(beatHapticsDispatcher) {
                viewModel.metronomeBeats.collect { beat ->
                    if (!beat.isSubdivision && beat.level != BeatLevel.MUTED) haptics.onBeat(isAccent = beat.level == BeatLevel.ACCENT)
                }
            }
        }
    }
}
