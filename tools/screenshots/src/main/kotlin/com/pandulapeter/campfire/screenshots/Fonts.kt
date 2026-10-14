/*
 * This file is part of Campfire.
 * Copyright (c) Pandula Péter 2017-2026.
 * https://github.com/pandulapeter/campfire
 *
 * This Source Code Form is subject to the terms of the Mozilla Public License, v. 2.0.
 * If a copy of the MPL was not distributed with this file, You can obtain one at
 * https://mozilla.org/MPL/2.0/.
 */
package com.pandulapeter.campfire.screenshots

import androidx.compose.ui.text.ExperimentalTextApi
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontVariation
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.platform.Font
import com.pandulapeter.campfire.presentation.ui.platform.ImpersonatedPlatform
import java.io.File

/**
 * The fonts a platform's system draws its own chrome in - the status bar, the title bar, the menu bar - around the app,
 * which is set in the fonts it bundles on every platform. A Mac has the Apple ones itself, so they are left to
 * Compose's own default (SF Pro); the others come from the folder the build downloads them into.
 */
internal class Fonts(directory: File) {
    private val roboto = File(directory, "Roboto.ttf")
    private val openSans = File(directory, "OpenSans.ttf")

    private val android: FontFamily by lazy { variableFamily(roboto) }

    private val windows: FontFamily by lazy { variableFamily(openSans) }

    /** What the system chrome around the app is set in on [platform]. */
    fun chromeFamily(platform: ImpersonatedPlatform) = when (platform) {
        ImpersonatedPlatform.ANDROID -> android
        ImpersonatedPlatform.WINDOWS -> windows
        ImpersonatedPlatform.IOS, ImpersonatedPlatform.MACOS -> FontFamily.Default
    }

    /** One file of a variable font, as the nine weights Compose asks a family for, each set on its weight axis. */
    @OptIn(ExperimentalTextApi::class)
    private fun variableFamily(file: File) = FontFamily(
        (100..900 step 100).map { weight ->
            Font(
                file = file,
                weight = FontWeight(weight),
                variationSettings = FontVariation.Settings(FontVariation.weight(weight)),
            )
        },
    )
}
