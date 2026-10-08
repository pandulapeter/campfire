/*
 * This file is part of Campfire.
 * Copyright (c) Pandula Péter 2017-2026.
 * https://github.com/pandulapeter/campfire
 *
 * This Source Code Form is subject to the terms of the Mozilla Public License, v. 2.0.
 * If a copy of the MPL was not distributed with this file, You can obtain one at
 * https://mozilla.org/MPL/2.0/.
 */
package com.pandulapeter.campfire

import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.withFrameNanos
import com.pandulapeter.campfire.presentation.ui.platform.desktopDataDirectory
import java.io.File
import kotlinx.coroutines.delay
import kotlinx.coroutines.withTimeoutOrNull

/**
 * Set by the build's training run (`recordClassDataArchive`), which needs the process to end on its own: a killed JVM
 * writes no archive.
 */
private val isTrainingRun = System.getProperty("campfire.trainingRun") == "true"

/**
 * Ends the training run with [exit] once the demo library has been planted and the first screen has been drawn.
 */
@Composable
internal fun TrainingRunEffect(exit: () -> Unit) {
    if (isTrainingRun) {
        LaunchedEffect(Unit) {
            val data = desktopDataDirectory()
            // The demo library being written means Koin started, the preferences were read and the songs scanned.
            while (!File(data, "preferences/preferences.json").isFile ||
                File(data, "library/songs").listFiles { file -> file.extension == "cho" }.isNullOrEmpty()
            ) delay(100)
            // Enough frames for the song cards, the welcome sheet and their animations to have been composed and
            // drawn - bounded in time too, so a session that produces no frames still ends normally (and so still
            // writes the archive) instead of being killed by the build's timeout.
            withTimeoutOrNull(15_000) { repeat(120) { withFrameNanos { } } }
            exit()
        }
    }
}
